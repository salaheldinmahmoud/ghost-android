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

        // Skip the app-level "X messages from Y chats" summary notification
        val isGroupSummary = (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(sbn.notification)

        if (messagingStyle != null) {
            for (message in messagingStyle.messages) {
                val sender = message.person?.name?.toString()
                    ?: messagingStyle.conversationTitle?.toString()
                    ?: "Unknown"
                val text = message.text?.toString() ?: ""
                val timestamp = message.timestamp

                Log.d(TAG, "[MessagingStyle] Sender: $sender | Text: $text | Time: $timestamp")

                saveMessage(sender, text, timestamp)
            }
        } else {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            Log.d(TAG, "[Fallback] Title: $title | Text: $text")

            saveMessage(title, text, sbn.postTime)
        }
    }

    private fun saveMessage(sender: String, content: String, timestamp: Long) {
        if (content.isBlank()) return

        serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val rowId = db.messageDao().insert(
                MessageEntity(sender = sender, content = content, timestamp = timestamp)
            )
            if (rowId == -1L) {
                Log.d(TAG, "Duplicate skipped: $sender | $content")
            } else {
                Log.d(TAG, "Saved to DB: $sender | $content")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Ghost is now listening for notifications ✅")
    }
}