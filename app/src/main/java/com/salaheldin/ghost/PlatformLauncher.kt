package com.salaheldin.ghost

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri

object PlatformLauncher {

    private const val TAG = "GhostPlatformLauncher"

    private val PACKAGES = mapOf(
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "messenger" to "com.facebook.orca",
        "telegram" to "org.telegram.messenger",
    )

    private val READABLE = mapOf(
        "whatsapp" to "WhatsApp",
        "instagram" to "Instagram",
        "messenger" to "Messenger",
        "telegram" to "Telegram",
        "sms" to "Messages",
    )

    /**
     * Opens the conversation on its platform. Deep links are built from the
     * stored `handle` only — the old code fed displayName ("Salaheldin Mahmoud")
     * into instagram.com/_u/ and t.me/, which resolves to a wrong profile or a
     * dead link. A name is not a handle.
     */
    fun launch(context: Context, conversation: ConversationEntity) {
        val platform = conversation.platform.lowercase()
        val packageName = PACKAGES[platform].orEmpty()
        val readable = READABLE[platform] ?: platform.replaceFirstChar { it.uppercase() }

        if (platform == "sms") {
            launchSms(context, conversation.handle.ifEmpty { conversation.displayName })
            return
        }

        // Group chats have no per-user deep link; go straight to the app.
        val deepLink = if (conversation.isGroup) null else getDeepLinkIntent(platform, conversation.handle)

        if (deepLink != null) {
            try {
                deepLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(deepLink)
                return
            } catch (e: Exception) {
                Log.d(TAG, "Deep link failed, falling back to app launch: ${e.message}")
            }
        }

        if (packageName.isEmpty()) {
            showError(context, "Unsupported platform: $readable")
            return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            showError(context, "$readable is not installed.")
            return
        }

        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName: ${e.message}")
            showError(context, "Could not open $readable")
        }
    }

    private fun getDeepLinkIntent(platform: String, handle: String): Intent? {
        if (handle.isBlank()) return null

        return when (platform) {
            "whatsapp" -> {
                val digits = handle.filter { it.isDigit() }
                if (digits.length >= 8) {
                    Intent(Intent.ACTION_VIEW, "https://wa.me/$digits".toUri())
                        .setPackage("com.whatsapp")
                } else null
            }
            "instagram" -> {
                val username = handle.removePrefix("@")
                if (username.matches(Regex("^[A-Za-z0-9._]{1,30}$"))) {
                    Intent(Intent.ACTION_VIEW, "https://instagram.com/_u/$username".toUri())
                        .setPackage("com.instagram.android")
                } else null
            }
            "telegram" -> {
                val username = handle.removePrefix("@")
                if (username.matches(Regex("^[A-Za-z0-9_]{5,32}$"))) {
                    Intent(Intent.ACTION_VIEW, "https://t.me/$username".toUri())
                        .setPackage("org.telegram.messenger")
                } else null
            }
            else -> null
        }
    }

    private fun launchSms(context: Context, handle: String) {
        try {
            val digits = handle.filter { it.isDigit() || it == '+' }
            val uri = if (digits.length >= 5) "smsto:$digits".toUri() else "smsto:".toUri()
            val intent = Intent(Intent.ACTION_SENDTO, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SMS: ${e.message}")
            showError(context, "Could not open Messages")
        }
    }

    private fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}