package com.salaheldin.ghost

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class GhostNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "GhostListener"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Only care about WhatsApp for now — M3 scope
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(sbn.notification)

        if (messagingStyle != null) {
            // Grouped or single message(s) via MessagingStyle
            for (message in messagingStyle.messages) {
                val sender = message.person?.name ?: messagingStyle.conversationTitle ?: "Unknown"
                val text = message.text?.toString() ?: ""
                val timestamp = message.timestamp

                Log.d(TAG, "[MessagingStyle] Sender: $sender | Text: $text | Time: $timestamp")
            }
        } else {
            // Fallback for notifications that don't use MessagingStyle
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            Log.d(TAG, "[Fallback] Title: $title | Text: $text")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Ghost is now listening for notifications ✅")
    }
}