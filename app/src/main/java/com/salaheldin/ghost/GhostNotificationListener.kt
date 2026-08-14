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
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val isGroupSummary = (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

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

            for (message in messagingStyle.messages) {
                val sender = message.person?.name?.toString() ?: conversationName
                val text = message.text?.toString() ?: ""
                val timestamp = message.timestamp

                Log.d(TAG, "[MessagingStyle] Group: $isGroup | Conversation: $conversationName | Sender: $sender | Text: $text")

                saveMessage(
                    conversationKey = conversationName,
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

            Log.d(TAG, "[Fallback] Title: $title | Text: $text")

            saveMessage(
                conversationKey = title,
                isGroup = false,
                sender = title,
                content = text,
                timestamp = sbn.postTime
            )
        }
    }

    private fun saveMessage(
        conversationKey: String,
        isGroup: Boolean,
        sender: String,
        content: String,
        timestamp: Long
    ) {
        if (content.isBlank()) return

        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)

            var conversation = db.conversationDao().findByContact(conversationKey)
            if (conversation == null) {
                db.conversationDao().insert(
                    ConversationEntity(
                        contactIdentifier = conversationKey,
                        isGroup = isGroup,
                        lastMessage = content,
                        lastMessageTime = timestamp,
                        status = "WAITING_FOR_REPLY"
                    )
                )
                conversation = db.conversationDao().findByContact(conversationKey)
            } else {
                db.conversationDao().update(
                    conversation.copy(
                        lastMessage = content,
                        lastMessageTime = timestamp,
                        status = "WAITING_FOR_REPLY",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            val rowId = db.messageDao().insert(
                MessageEntity(
                    conversationId = conversation?.id ?: 0,
                    sender = sender,
                    content = content,
                    timestamp = timestamp
                )
            )
            if (rowId == -1L) {
                Log.d(TAG, "Duplicate skipped: $sender | $content")
            } else {
                Log.d(TAG, "Saved to DB: $sender | $content (conversation: ${conversation?.id})")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Ghost is now listening for notifications ✅")
    }
}