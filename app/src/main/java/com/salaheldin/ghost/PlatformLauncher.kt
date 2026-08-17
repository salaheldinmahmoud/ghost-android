package com.salaheldin.ghost

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri

object PlatformLauncher {

    private const val TAG = "GhostPlatformLauncher"

    /**
     * Attempts to open the appropriate messaging platform for a conversation.
     * Falls back to opening the app if a direct conversation link isn't possible.
     */
    fun launch(context: Context, conversation: ConversationEntity) {
        val platform = conversation.platform.lowercase()
        val displayName = conversation.displayName
        val packageName = getPackageName(platform)

        Log.d(TAG, "--- Launch Start ---")
        Log.d(TAG, "platform = $platform")
        Log.d(TAG, "expected package = $packageName")

        if (packageName.isNotEmpty()) {
            try {
                val info = context.packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "package installed/check result = true (version: ${info.versionName})")
            } catch (_: PackageManager.NameNotFoundException) {
                Log.d(TAG, "package installed/check result = false")
            }
        }

        if (platform == "sms") {
            launchSms(context, displayName)
            return
        }

        // Try deep link first if applicable
        val deepLinkIntent = getDeepLinkIntent(platform, displayName)
        if (deepLinkIntent != null) {
            try {
                Log.d(TAG, "launching deep link: ${deepLinkIntent.data}")
                deepLinkIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(deepLinkIntent)
                Log.d(TAG, "deep link launch successful")
                return
            } catch (e: Exception) {
                Log.d(TAG, "deep link failed, falling back to app launch: ${e.message}")
            }
        }

        // Fallback to app launch
        if (packageName.isNotEmpty()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            Log.d(TAG, "launchIntent result = ${if (launchIntent != null) "Found" else "Null"}")

            if (launchIntent != null) {
                try {
                    Log.d(TAG, "launching package: $packageName")
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Log.d(TAG, "package launch successful")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch package $packageName: ${e.message}")
                    showError(context, "Could not open $platform")
                }
            } else {
                val readableName = when (platform) {
                    "whatsapp" -> "WhatsApp"
                    "instagram" -> "Instagram"
                    "messenger" -> "Messenger"
                    "telegram" -> "Telegram"
                    else -> platform.replaceFirstChar { it.uppercase() }
                }
                showError(context, "$readableName is not installed.")
            }
        } else {
            showError(context, "Unsupported platform: $platform")
        }
    }

    private fun getDeepLinkIntent(platform: String, displayName: String): Intent? {
        return when (platform) {
            "whatsapp" -> {
                if (displayName.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } && displayName.length > 5) {
                    val clean = displayName.replace(Regex("[^0-9+]"), "")
                    Intent(Intent.ACTION_VIEW, "https://wa.me/$clean".toUri()).setPackage("com.whatsapp")
                } else null
            }
            "instagram" -> {
                if (!displayName.contains(" ")) {
                    Intent(Intent.ACTION_VIEW, "http://instagram.com/_u/$displayName".toUri()).setPackage("com.instagram.android")
                } else null
            }
            "telegram" -> {
                if (displayName.startsWith("@")) {
                    Intent(Intent.ACTION_VIEW, "https://t.me/${displayName.substring(1)}".toUri()).setPackage("org.telegram.messenger")
                } else if (!displayName.contains(" ")) {
                    Intent(Intent.ACTION_VIEW, "https://t.me/$displayName".toUri()).setPackage("org.telegram.messenger")
                } else null
            }
            else -> null
        }
    }

    private fun launchSms(context: Context, displayName: String) {
        try {
            val uri = if (displayName.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
                "smsto:${displayName.replace(" ", "")}".toUri()
            } else {
                "smsto:".toUri()
            }
            val intent = Intent(Intent.ACTION_SENDTO, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SMS: ${e.message}")
            showError(context, "Could not open SMS")
        }
    }

    private fun getPackageName(platform: String): String {
        return when (platform) {
            "whatsapp" -> "com.whatsapp"
            "instagram" -> "com.instagram.android"
            "messenger" -> "com.facebook.katana"
            "telegram" -> "org.telegram.messenger"
            else -> ""
        }
    }

    private fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}