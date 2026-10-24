package me.weishu.kernelsu

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Reports uncaught exceptions two ways:
 *  - uploads them to our own server (best effort, so we see crashes from every device),
 *  - writes them to the public Downloads folder so a user can send the file without root.
 */
class CrashLogger(
    private val app: Application,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "uncaught exception on ${thread.name}", throwable)
        val stack = throwable.stackTraceToString().take(32 * 1024)
        runCatching { upload(thread, stack) }
        runCatching { dump(thread, stack) }
        previous?.uncaughtException(thread, throwable) ?: exitProcess(1)
    }

    private fun deviceInfo(): Pair<String, String> =
        "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})" to
            "Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT} / kernel ${System.getProperty("os.version")}"

    private fun upload(thread: Thread, stack: String) {
        val (device, platform) = deviceInfo()
        val payload = JSONObject().apply {
            put("package", app.packageName)
            put("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("device", device)
            put("platform", platform)
            put("thread", thread.name)
            put("stack", stack)
            put("ts", System.currentTimeMillis() / 1000)
        }.toString()

        val client = OkHttpClient.Builder()
            .callTimeout(4, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(BuildConfig.KEY_SERVER_URL.trimEnd('/') + "/api/v1/crash")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().close()
    }

    private fun dump(thread: Thread, stack: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val (device, platform) = deviceInfo()
        val body = buildString {
            appendLine("DRDC crash report")
            appendLine("time      : $timestamp")
            appendLine("package   : ${app.packageName}")
            appendLine("version   : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device    : $device")
            appendLine("platform  : $platform")
            appendLine("thread    : ${thread.name}")
            appendLine()
            append(stack)
        }

        val name = "DRDC-crash-$timestamp.txt"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let { target ->
            app.contentResolver.openOutputStream(target)?.use { it.write(body.toByteArray()) }
            Log.i(TAG, "crash report written to $target ($name)")
        }
    }

    private companion object {
        const val TAG = "DRDCCrash"
    }
}
