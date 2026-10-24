package me.weishu.kernelsu.data.key

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.weishu.kernelsu.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
internal data class ActivateRequest(
    val card_key: String,
    val machine_id: String,
    val app_version: String,
    val nonce: String,
    val ts: Long,
    val sign: String,
)

@Serializable
internal data class HeartbeatRequest(
    val token: String,
    val machine_id: String,
    val app_version: String,
    val nonce: String,
    val ts: Long,
    val sign: String,
)

@Serializable
data class KeyResponse(
    val ok: Boolean = false,
    val token: String? = null,
    @kotlinx.serialization.SerialName("expire_at") val expireAt: Long = 0L,
    val type: String? = null,
    val machine_id: String? = null,
    val server_time: Long = 0L,
    val code: String? = null,
    val error: String? = null,
)

/** Thrown for server-side rejections; [code] mirrors the API error code. */
class KeyError(val code: String, message: String?, val httpCode: Int) :
    IOException(message ?: code)

object KeyApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val random = SecureRandom()

    val serverUrl: String
        get() = BuildConfig.KEY_SERVER_URL.trimEnd('/')

    val appVersion: String
        get() = BuildConfig.VERSION_NAME

    suspend fun activate(
        client: OkHttpClient,
        cardKey: String,
        machineId: String,
    ): KeyResponse = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis() / 1000
        val nonce = newNonce()
        val payload = ActivateRequest(
            card_key = cardKey,
            machine_id = machineId,
            app_version = appVersion,
            nonce = nonce,
            ts = ts,
            sign = hmacHex("$cardKey|$machineId|$appVersion|$nonce|$ts"),
        )
        post(client, "/api/v1/activate", json.encodeToString(payload))
    }

    suspend fun heartbeat(
        client: OkHttpClient,
        token: String,
        machineId: String,
    ): KeyResponse = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis() / 1000
        val nonce = newNonce()
        val payload = HeartbeatRequest(
            token = token,
            machine_id = machineId,
            app_version = appVersion,
            nonce = nonce,
            ts = ts,
            sign = hmacHex("$token|$machineId|$appVersion|$nonce|$ts"),
        )
        post(client, "/api/v1/heartbeat", json.encodeToString(payload))
    }

    private fun post(client: OkHttpClient, path: String, body: String): KeyResponse {
        val request = Request.Builder()
            .url(serverUrl + path)
            .post(body.toRequestBody(jsonMedia))
            .build()
        val callClient = client.newBuilder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
        callClient.newCall(request).execute().use { response ->
            val text = response.body.string()
            val parsed = runCatching { json.decodeFromString<KeyResponse>(text) }.getOrNull()
                ?: throw KeyError(
                    if (response.isSuccessful) "bad_response" else "http_${response.code}",
                    text.take(200),
                    response.code,
                )
            if (!parsed.ok) {
                throw KeyError(
                    parsed.code ?: "error",
                    parsed.error,
                    response.code,
                )
            }
            return parsed
        }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(12)
        random.nextBytes(bytes)
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    private fun hmacHex(canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.KEY_APP_SECRET.toByteArray(), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray()).joinToString("") {
            String.format(Locale.US, "%02x", it)
        }
    }
}
