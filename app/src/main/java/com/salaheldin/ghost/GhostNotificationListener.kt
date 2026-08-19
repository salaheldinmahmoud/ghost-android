package com.salaheldin.ghost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GhostNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "GhostListener"
        private const val ACTION_RESET_NEW_MESSAGES = "com.salaheldin.ghost.ACTION_RESET_NEW_MESSAGES"

        // Supported platforms: package name -> display name
        private val SUPPORTED_PACKAGES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram",
            "com.instagram.android" to "Instagram",
            "com.facebook.orca" to "Messenger",
            "com.facebook.katana" to "Messenger",
            "com.google.android.apps.messaging" to "SMS",
            "com.samsung.android.messaging" to "SMS",
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val newMessagesPlatforms = mutableSetOf<String>()
    private var newMessagesCount = 0

    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_RESET_NEW_MESSAGES) {
                newMessagesCount = 0
                newMessagesPlatforms.clear()
                GLog.d(TAG) { "New message counts reset via broadcast" }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        GLog.d(TAG) { "GhostNotificationListener created" }
        GhostNotificationManager.createChannels(this)
        observeAttentionSignals()

        val filter = IntentFilter(ACTION_RESET_NEW_MESSAGES)
        ContextCompat.registerReceiver(this, resetReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(resetReceiver)
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun observeAttentionSignals() {
        val db = AppDatabase.getInstance(this)
        GLog.d(TAG) { "Observing database for attention signals" }

        serviceScope.launch {
            db.conversationDao().getAllConversations()
                .map { list -> list.count { it.status == "WAITING_FOR_REPLY" } }
                .distinctUntilChanged()
                .collect { count ->
                    GhostNotificationManager.showWaitingForReply(applicationContext, count)
                }
        }

        serviceScope.launch {
            db.conversationDao().getAllConversations()
                .map { list ->
                    val waiting = list.filter { it.status == "WAITING_FOR_REPLY" }
                    var unusualCount = 0
                    waiting.forEach { conv ->
                        val events = db.responseEventDao().getEventsForConversation(conv.id)
                        val baseline = BaselineCalculator.calculate(events)
                        val currentWaitMs = System.currentTimeMillis() - conv.lastMessageTime
                        if (BaselineCalculator.checkUnusualDelay(baseline, currentWaitMs) == true) {
                            unusualCount++
                        }
                    }
                    unusualCount
                }
                .distinctUntilChanged()
                .collect { count ->
                    GhostNotificationManager.showUnusualDelay(applicationContext, count)
                }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Ignore Ghost's own notifications
        if (sbn.packageName == packageName) return

        val platform = SUPPORTED_PACKAGES[sbn.packageName] ?: return

        val isGroupSummary = (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        // Platform-native handle when the OS gives us one — far more reliable
        // than a display name for deep linking.
        val handle = sbn.notification.shortcutId ?: ""

        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(sbn.notification)

        if (messagingStyle != null) {
            val isGroup = messagingStyle.isGroupConversation
            val conversationName = if (isGroup) {
                val rawTitle = messagingStyle.conversationTitle?.toString() ?: "Unknown Group"
                rawTitle.replace(Regex("\\s*\\(\\d+\\s*messages?\\)\\s*$"), "").trim()
            } else {
                messagingStyle.messages.lastOrNull()?.person?.name?.toString() ?: "Unknown"
            }

            val personUri = messagingStyle.messages.lastOrNull()?.person?.uri.orEmpty()

            for (message in messagingStyle.messages) {
                val sender = message.person?.name?.toString() ?: conversationName
                val text = message.text?.toString() ?: ""

                GLog.d(TAG) { "[$platform][MessagingStyle] group=$isGroup len=${text.length}" }

                saveMessage(
                    platform = platform,
                    conversationKey = "$platform:$conversationName",
                    displayName = conversationName,
                    handle = handle.ifEmpty { extractHandle(personUri) },
                    isGroup = isGroup,
                    sender = sender,
                    content = text,
                    timestamp = message.timestamp,
                )
            }
        } else {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            // Skip the app's own system notifications ("Checking for new messages").
            if (title.equals(platform, ignoreCase = true)) return

            GLog.d(TAG) { "[$platform][Fallback] len=${text.length}" }

            saveMessage(
                platform = platform,
                conversationKey = "$platform:$title",
                displayName = title,
                handle = handle,
                isGroup = false,
                sender = title,
                content = text,
                timestamp = sbn.postTime,
            )
        }
    }

    /** "tel:+201234567890" / "mailto:x@y.z" -> the bare handle. */
    private fun extractHandle(uri: String): String =
        uri.substringAfter("tel:", "").ifEmpty { "" }

    private fun saveMessage(
        platform: String,
        conversationKey: String,
        displayName: String,
        handle: String,
        isGroup: Boolean,
        sender: String,
        content: String,
        timestamp: Long,
    ) {
        if (content.isBlank()) return

        val classification = MessageClassifier.classify(content)

        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)

            val needsReply = classification.requirement != ReplyRequirement.NO_REPLY_REQUIRED
            val initialStatus = if (needsReply) "WAITING_FOR_REPLY" else "NEW"

            // Atomic get-or-create: no insert/read race between concurrent notifications.
            val currentConversation = db.conversationDao().getOrCreate(
                ConversationEntity(
                    contactIdentifier = conversationKey,
                    displayName = displayName,
                    handle = handle,
                    platform = platform.lowercase(),
                    isGroup = isGroup,
                    lastMessage = content,
                    lastMessageTime = timestamp,
                    awaitingSince = if (needsReply) timestamp else 0L,
                    status = initialStatus,
                    priority = classification.priority.name,
                )
            )

            val rowId = db.messageDao().insert(
                MessageEntity(
                    conversationId = currentConversation.id,
                    sender = sender,
                    content = content,
                    timestamp = timestamp,
                    platform = platform.lowercase(),
                    requiresReply = classification.requirement.name,
                    priority = classification.priority.name,
                )
            )

            if (rowId == -1L) {
                GLog.d(TAG) { "Duplicate skipped for conv=${currentConversation.id}" }
                return@launch
            }

            GLog.d(TAG) {
                "Saved: [$platform] conv=${currentConversation.id} " +
                    "${classification.requirement}/${classification.priority}"
            }

            val isLatest = timestamp >= currentConversation.lastMessageTime

            // Start the clock only on the FIRST unanswered message.
            val newAwaitingSince = when {
                !needsReply -> currentConversation.awaitingSince
                currentConversation.awaitingSince == 0L -> timestamp
                else -> minOf(currentConversation.awaitingSince, timestamp)
            }

            val updatedStatus = if (needsReply) "WAITING_FOR_REPLY" else currentConversation.status

            db.conversationDao().update(
                currentConversation.copy(
                    lastMessage = if (isLatest) content else currentConversation.lastMessage,
                    lastMessageTime = if (isLatest) timestamp else currentConversation.lastMessageTime,
                    handle = currentConversation.handle.ifEmpty { handle },
                    awaitingSince = newAwaitingSince,
                    status = updatedStatus,
                    priority = if (isLatest) classification.priority.name else currentConversation.priority,
                    updatedAt = System.currentTimeMillis(),
                )
            )

            // Only surface a Ghost notification for messages that actually need
            // you — otherwise Ghost becomes the second inbox it promised not to be.
            if (needsReply) {
                newMessagesCount++
                newMessagesPlatforms.add(platform)
                GhostNotificationManager.showNewMessages(
                    applicationContext, newMessagesCount, newMessagesPlatforms
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Counts persist until the user opens Ghost; clearing the source app's
        // notification does not mean the message was handled.
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        GLog.d(TAG) { "Ghost is now listening for notifications" }
    }
}