package ws.chill.gamecheckout.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Port of `Auth/KeychainStore.swift`.
 *
 * iOS uses the Keychain with `kSecAttrAccessibleAfterFirstUnlock`; the Android
 * equivalent is a Keystore-backed AES-GCM key that encrypts each value before
 * it lands in `SharedPreferences`, so neither the hash nor its expiry is ever
 * stored in the clear.
 *
 * Note: unlike iOS, reinstalling the app (or clearing app data) drops the
 * stored session, because the encryption key lives in the app's Keystore.
 */
class SecureStore(
    context: Context,
    private val service: String,
) {
    private val appContext = context.applicationContext

    private val preferences = appContext.getSharedPreferences(
        PREFS_PREFIX + service,
        Context.MODE_PRIVATE,
    )

    private val alias = "$service.key"

    fun save(account: String, value: String) {
        val encrypted = encrypt(value) ?: return
        preferences.edit().putString(account, encrypted).apply()
    }

    fun read(account: String): String? {
        val encrypted = preferences.getString(account, null) ?: return null
        return decrypt(encrypted)
    }

    fun delete(account: String) {
        preferences.edit().remove(account).apply()
    }

    private fun secretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Not user-authentication-bound: the session is restored on cold
            // start without a prompt, matching kSecAttrAccessibleAfterFirstUnlock.
            .setInvalidatedByBiometricEnrollment(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encrypt(value: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        "${b64(cipher.iv)}:${b64(ciphertext)}"
    } catch (e: Exception) {
        // iOS swallows Keychain write errors the same way: a failure simply
        // means the session won't be persisted.
        null
    }

    private fun decrypt(stored: String): String? = try {
        val parts = stored.split(':')
        if (parts.size != 2) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, unB64(parts[0])),
            )
            String(cipher.doFinal(unB64(parts[1])), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unB64(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PREFS_PREFIX = "secure_store."
    }
}
