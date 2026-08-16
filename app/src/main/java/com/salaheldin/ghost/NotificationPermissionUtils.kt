package com.salaheldin.ghost

import android.content.Context
import androidx.core.app.NotificationManagerCompat

fun isNotificationAccessGranted(context: Context): Boolean {
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    return enabledListeners.contains(context.packageName)
}