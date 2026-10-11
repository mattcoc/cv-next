package io.github.mattcoc.cvnext

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
 * Valori cifrati con una chiave AES-GCM dell'Android Keystore.
 * Se il Keystore non è utilizzabile i valori vengono salvati in chiaro, come faceva la versione precedente.
 */
class SecureStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val key: SecretKey? = runCatching { loadKey() }.getOrNull()

    @Synchronized
    fun get(name: String): String? {
        val raw = prefs.getString(name, null) ?: return null
        return when {
            raw.startsWith(PLAIN) -> raw.removePrefix(PLAIN)
            raw.startsWith(ENCRYPTED) && key != null -> runCatching { decrypt(raw.removePrefix(ENCRYPTED)) }.getOrNull()
            else -> null
        }
    }

    @Synchronized
    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val stored = key?.let { runCatching { ENCRYPTED + encrypt(value) }.getOrNull() } ?: (PLAIN + value)
        prefs.edit().putString(name, stored).apply()
    }

    private fun loadKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val data = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12)) }
        return String(cipher.doFinal(data, 12, data.size - 12))
    }

    companion object {
        private const val ALIAS = "cvnext"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PLAIN = "p:"
        private const val ENCRYPTED = "e:"

        @Volatile private var instance: SecureStore? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: SecureStore(context.applicationContext).also { instance = it }
        }
    }
}
