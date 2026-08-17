package com.salaheldin.ghost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object GhostNotificationManager {

    private const val TAG = "GhostNotification"
    private const val CHANNEL_ID_NEW_MESSAGES = "ghost_new_messages"
    private const val CHANNEL_ID_WAITING_REPLY = "ghost_waiting_reply"
    private const val CHANNEL_ID_UNUSUAL_DELAY = "ghost_unusual_delay"

    private const val ID_NEW_MESSAGES = 1001
    private const val ID_WAITING_REPLY = 1002
    private const val ID_UNUSUAL_DELAY = 1003

    fun createChannels(context: Context) {
        Log.d(TAG, "Creating notification channels")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ID_NEW_MESSAGES,
                    "Ghost — New Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    CHANNEL_ID_WAITING_REPLY,
                    "Ghost — Waiting for Reply",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    CHANNEL_ID_UNUSUAL_DELAY,
                    "Ghost — Unusual Delay",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            manager.createNotificationChannels(channels)
        }
    }

    fun showNewMessages(context: Context, count: Int, platforms: Set<String>) {
        Log.d(TAG, "showNewMessages called: count=$count, platforms=$platforms")
        if (count <= 0) {
            cancel(context, ID_NEW_MESSAGES)
            return
        }

        val platformList = platforms.toList().sorted()
        val contentText: String
        val subText: String?

        if (platformList.size == 1) {
            val platform = platformList.first()
            val suffix = if (count == 1) "message" else "messages"
            contentText = "You have $count new $platform $suffix"
            subText = null
        } else {
            val suffix = if (count == 1) "message" else "messages"
            contentText = "You have $count new $suffix"
            subText = platformList.joinToString(" • ")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_NEW_MESSAGES)
            .setSmallIcon(R.drawable.ic_ghost_logo)
            .setContentTitle("Ghost")
            .setContentText(contentText)
            .apply { subText?.let { setSubText(it) } }
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createPendingIntent(context))
            .setAutoCancel(true)

        Log.d(TAG, "Posting NEW_MESSAGES notification: count=$count, platforms=$platformList, text='$contentText', subtext='$subText'")
        notify(context, ID_NEW_MESSAGES, builder.build())
    }

    fun showWaitingForReply(context: Context, count: Int) {
        Log.d(TAG, "showWaitingForReply called: count=$count")
        if (count <= 0) {
            cancel(context, ID_WAITING_REPLY)
            return
        }

        val text = "You have $count conversation${if (count > 1) "s" else ""} waiting for a reply"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_WAITING_REPLY)
            .setSmallIcon(R.drawable.ic_ghost_logo)
            .setContentTitle("Ghost")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createPendingIntent(context))
            .setAutoCancel(true)

        Log.d(TAG, "Posting WAITING_REPLY notification: $text")
        notify(context, ID_WAITING_REPLY, builder.build())
    }

    fun showUnusualDelay(context: Context, count: Int) {
        Log.d(TAG, "showUnusualDelay called: count=$count")
        if (count <= 0) {
            cancel(context, ID_UNUSUAL_DELAY)
            return
        }

        val text = "$count conversation${if (count > 1) "s are" else " is"} taking longer than usual"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_UNUSUAL_DELAY)
            .setSmallIcon(R.drawable.ic_ghost_logo)
            .setContentTitle("Ghost")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createPendingIntent(context))
            .setAutoCancel(true)

        Log.d(TAG, "Posting UNUSUAL_DELAY notification: $text")
        notify(context, ID_UNUSUAL_DELAY, builder.build())
    }

    fun clearNewMessages(context: Context) {
        cancel(context, ID_NEW_MESSAGES)
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            val hasPermission = if (Build.VERSION.SDK_INT >= 33) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true

            if (hasPermission) {
                NotificationManagerCompat.from(context).notify(id, notification)
                Log.d(TAG, "Notification posted successfully (id=$id)")
            } else {
                Log.w(TAG, "Skipping notification: POST_NOTIFICATIONS permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification: ${e.message}")
        }
    }

    private fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}