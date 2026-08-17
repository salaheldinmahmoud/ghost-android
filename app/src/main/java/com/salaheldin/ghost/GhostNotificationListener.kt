package com.salaheldin.ghost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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
            "com.facebook.katana" to "Messenger",  // Facebook app itself, handles messages on some setups
            // SMS: cover both common default apps, since it varies by phone
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
                Log.d(TAG, "New message counts reset via broadcast")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "GhostNotificationListener created")
        GhostNotificationManager.createChannels(this)
        observeAttentionSignals()
        
        val filter = IntentFilter(ACTION_RESET_NEW_MESSAGES)
        ContextCompat.registerReceiver(this, resetReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(resetReceiver)
    }

    private fun observeAttentionSignals() {
        val db = AppDatabase.getInstance(this)
        Log.d(TAG, "Observing database for attention signals")
        
        // Observe WAITING_FOR_REPLY count
        serviceScope.launch {
            db.conversationDao().getAllConversations()
                .map { list -> list.count { it.status == "WAITING_FOR_REPLY" } }
                .distinctUntilChanged()
                .collect { count ->
                    Log.d(TAG, "Waiting count update: $count")
                    GhostNotificationManager.showWaitingForReply(applicationContext, count)
                }
        }

        // Observe UNUSUAL_DELAY count
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
                    Log.d(TAG, "Unusual delay count update: $count")
                    GhostNotificationManager.showUnusualDelay(applicationContext, count)
                }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "Notification received from: ${sbn.packageName}")

        // Ignore Ghost's own notifications
        if (sbn.packageName == packageName) {
            Log.d(TAG, "Ignoring Ghost's own notification")
            return
        }

        val platform = SUPPORTED_PACKAGES[sbn.packageName] ?: return

        val isGroupSummary = (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(sbn.notification)

        if (messagingStyle != null) {
            val isGroup = messagingStyle.isGroupConversation
            val conversationName = if (isGroup) {
                val rawTitle = messagingStyle.conversationTitle?.toString() ?: "Unknown Group"
                // Strip WhatsApp-style trailing unread counts, e.g. "Test (4 messages)"
                rawTitle.replace(Regex("\\s*\\(\\d+\\s*messages?\\)\\s*$"), "").trim()
            } else {
                messagingStyle.messages.lastOrNull()?.person?.name?.toString() ?: "Unknown"
            }

            for (message in messagingStyle.messages) {
                val sender = message.person?.name?.toString() ?: conversationName
                val text = message.text?.toString() ?: ""
                val timestamp = message.timestamp

                Log.d(TAG, "[$platform][MessagingStyle] Group: $isGroup | Conversation: $conversationName | Sender: $sender | Text: $text")

                saveMessage(
                    platform = platform,
                    conversationKey = "$platform:$conversationName",
                    displayName = conversationName,
                    isGroup = isGroup,
                    sender = sender,
                    content = text,
                    timestamp = timestamp,
                )
            }
        } else {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            // Skip the app's own system/status notifications (e.g. WhatsApp's
            // "Checking for new messages", or similar for other platforms) —
            // these aren't real conversations, just the app's own name as the title.
            if (title.equals(platform, ignoreCase = true)) return

            Log.d(TAG, "[$platform][Fallback] Title: $title | Text: $text")

            saveMessage(
                platform = platform,
                conversationKey = "$platform:$title",
                displayName = title,
                isGroup = false,
                sender = title,
                content = text,
                timestamp = sbn.postTime,
            )
        }
    }

    private fun saveMessage(
        platform: String,
        conversationKey: String,
        displayName: String,
        isGroup: Boolean,
        sender: String,
        content: String,
        timestamp: Long,
    ) {
        if (content.isBlank()) return

        Log.d(TAG, "saveMessage: starting for $platform")
        val classification = MessageClassifier.classify(content)

        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)

            var conversation = db.conversationDao().findByContact(conversationKey)

            if (conversation == null) {
                val initialStatus = if (classification.requirement == ReplyRequirement.NO_REPLY_REQUIRED) {
                    "NEW"
                } else {
                    "WAITING_FOR_REPLY"
                }

                db.conversationDao().insert(
                    ConversationEntity(
                        contactIdentifier = conversationKey,
                        displayName = displayName,
                        platform = platform.lowercase(),
                        isGroup = isGroup,
                        lastMessage = content,
                        lastMessageTime = timestamp,
                        status = initialStatus,
                        priority = classification.priority.name
                    )
                )
                conversation = db.conversationDao().findByContact(conversationKey)
                Log.d(TAG, "Created new conversation: id=${conversation?.id}, priority=${classification.priority.name}")
            }

            val currentConversation = conversation ?: return@launch

            val rowId = db.messageDao().insert(
                MessageEntity(
                    conversationId = currentConversation.id,
                    sender = sender,
                    content = content,
                    timestamp = timestamp,
                    platform = platform.lowercase(),
                    requiresReply = classification.requirement.name,
                    priority = classification.priority.name
                )
            )

            if (rowId == -1L) {
                Log.d(TAG, "Duplicate skipped: $sender | $content")
            } else {
                Log.d(TAG, "Saved to DB: [$platform] $sender | $content | ${classification.requirement} (${classification.priority}) (conversation: ${currentConversation.id})")

                // Update conversation summary only for NEW messages
                val isLatest = timestamp >= currentConversation.lastMessageTime
                
                val updatedStatus = if (classification.requirement == ReplyRequirement.NO_REPLY_REQUIRED) {
                    currentConversation.status
                } else {
                    "WAITING_FOR_REPLY"
                }

                val oldPriority = currentConversation.priority
                val latestPriority = classification.priority.name
                val finalPriority = if (isLatest) latestPriority else oldPriority

                Log.d(TAG, "Updating conversation ${currentConversation.id} priority: " +
                        "old=$oldPriority, message=$latestPriority, final=$finalPriority (isLatest=$isLatest)")

                db.conversationDao().update(
                    currentConversation.copy(
                        lastMessage = if (isLatest) content else currentConversation.lastMessage,
                        lastMessageTime = if (isLatest) timestamp else currentConversation.lastMessageTime,
                        status = updatedStatus,
                        priority = finalPriority,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // Update "New Messages" notification
                newMessagesCount++
                newMessagesPlatforms.add(platform)
                Log.d(TAG, "Triggering Ghost notification for new message. Session count: $newMessagesCount")
                GhostNotificationManager.showNewMessages(applicationContext, newMessagesCount, newMessagesPlatforms)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // If a notification for a supported app is cleared by the user, we could potentially reset our new messages count
        // but the requirements say "Ghost should not become another notification inbox".
        // For now, we'll keep the count until the user opens Ghost.
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Ghost is now listening for notifications ✅")
    }
}