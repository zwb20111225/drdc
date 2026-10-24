package me.weishu.kernelsu.data.key

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredToken(
    val token: String,
    /** JWT expiry in milliseconds (refreshed by heartbeat). */
    val tokenExpiresAt: Long,
    /** Card expiry in seconds, 0 means permanent. */
    val cardExpireAt: Long,
    val type: String,
    val machineId: String,
    /** Last successful server contact, milliseconds. Used for the 72h offline grace window. */
    val lastValidatedAt: Long,
)

/**
 * Persists the activation token encrypted with an AES-GCM key that lives in the Android
 * Keystore (SPEC §5.2). The Keystore key never leaves the TEE/StrongBox-backed store.
 */
object TokenStore {

    private const val PREFS = "drdc_key_auth"
    private const val KEY_ALIAS = "drdc_key_token_v1"
    private const val AAD = "drdc-key-v1"

    private const val P_TOKEN = "token"
    private const val P_TOKEN_EXP = "token_expires_at"
    private const val P_CARD_EXP = "card_expire_at"
    private const val P_TYPE = "card_type"
    private const val P_MACHINE = "machine_id"
    private const val P_SEEN = "last_validated"

    fun load(context: Context): StoredToken? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val blob = prefs.getString(P_TOKEN, null) ?: return null
        val token = decrypt(blob) ?: return null
        return StoredToken(
            token = token,
            tokenExpiresAt = prefs.getLong(P_TOKEN_EXP, 0L),
            cardExpireAt = prefs.getLong(P_CARD_EXP, 0L),
            type = prefs.getString(P_TYPE, "permanent") ?: "permanent",
            machineId = prefs.getString(P_MACHINE, "") ?: "",
            lastValidatedAt = prefs.getLong(P_SEEN, 0L),
        )
    }

    fun save(context: Context, response: KeyResponse, machineId: String) {
        val token = response.token ?: return
        val blob = runCatching { encrypt(token) }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(P_TOKEN, blob)
            .putLong(P_TOKEN_EXP, jwtExpiryMillis(token))
            .putLong(P_CARD_EXP, response.expireAt)
            .putString(P_TYPE, response.type ?: "permanent")
            .putString(P_MACHINE, machineId)
            .putLong(P_SEEN, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Reads `exp` out of the JWT payload without verifying it (the server already signed it). */
    fun jwtExpiryMillis(token: String): Long {
        val payload = token.split('.').getOrNull(1) ?: return 0L
        val decoded = runCatching {
            String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        }.getOrNull() ?: return 0L
        val exp = Regex("\"exp\"\\s*:\\s*(\\d+)").find(decoded)?.groupValues?.getOrNull(1) ?: return 0L
        return (exp.toLongOrNull() ?: 0L) * 1000L
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD.toByteArray())
        val cipherText = cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        if (bytes.size <= 12) return@runCatching null
        val iv = bytes.copyOfRange(0, 12)
        val cipherText = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD.toByteArray())
        String(cipher.doFinal(cipherText))
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
