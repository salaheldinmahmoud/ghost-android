package com.salaheldin.ghost

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates and stores the SQLCipher database passphrase.
 *
 * The passphrase itself is a random 32-byte value, generated once on first
 * launch. It's never stored in plaintext: it's encrypted with an AES key
 * held in the Android Keystore (secure hardware-backed storage that never
 * exposes the raw key material to the app), and only the encrypted blob
 * lives in SharedPreferences.
 */
object DatabaseKeyProvider {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "ghost_db_key"
    private const val PREFS_NAME = "ghost_secure_prefs"
    private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val KEY_IV = "passphrase_iv"

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingEncrypted = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        val existingIv = prefs.getString(KEY_IV, null)

        val secretKey = getOrCreateKeystoreKey()

        if (existingEncrypted != null && existingIv != null) {
            // Decrypt and return the existing passphrase
            val encryptedBytes = Base64.decode(existingEncrypted, Base64.NO_WRAP)
            val ivBytes = Base64.decode(existingIv, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, ivBytes))
            return cipher.doFinal(encryptedBytes)
        }

        // First launch: generate a new random passphrase, encrypt it, store it
        val newPassphrase = ByteArray(32)
        SecureRandom().nextBytes(newPassphrase)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(newPassphrase)
        val iv = cipher.iv

        prefs.edit()
            .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()

        return newPassphrase
    }

    private fun getOrCreateKeystoreKey(): java.security.Key {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null)
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}