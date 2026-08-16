package com.salaheldin.ghost

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GhostNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "GhostListener"

        // Supported platforms: package name -> display name
        private val SUPPORTED_PACKAGES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram",
            "com.instagram.android" to "Instagram",
            "com.facebook.orca" to "Messenger",
            "com.facebook.katana" to "Messenger",  // Facebook app itself, handles messages on some setups
            // SMS: cover both common default apps, since it varies by phone
            "com.google.android.apps.messaging" to "SMS",
            "com.samsung.android.messaging" to "SMS"
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // TEMPORARY DEBUG LINE — logs every notification's package name,
        // even ones Ghost doesn't currently recognize. Remove once we've
        // confirmed Messenger's real package name.
        Log.d(TAG, "[DEBUG] Notification received from package: ${sbn.packageName}")

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
                    timestamp = timestamp
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
                timestamp = sbn.postTime
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
        timestamp: Long
    ) {
        if (content.isBlank()) return

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
            } else {
                val updatedStatus = if (classification.requirement == ReplyRequirement.NO_REPLY_REQUIRED) {
                    conversation.status
                } else {
                    "WAITING_FOR_REPLY"
                }

                db.conversationDao().update(
                    conversation.copy(
                        lastMessage = content,
                        lastMessageTime = timestamp,
                        status = updatedStatus,
                        priority = classification.priority.name,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            val rowId = db.messageDao().insert(
                MessageEntity(
                    conversationId = conversation?.id ?: 0,
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
                Log.d(TAG, "Saved to DB: [$platform] $sender | $content | ${classification.requirement} (${classification.priority}) (conversation: ${conversation?.id})")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Ghost is now listening for notifications ✅")
    }
}