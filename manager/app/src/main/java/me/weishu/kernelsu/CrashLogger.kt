package me.weishu.kernelsu

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Writes uncaught exceptions to the public Downloads folder so users can send them
 * without root (adb / logcat is not always available).
 */
class CrashLogger(
    private val app: Application,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "uncaught exception on ${thread.name}", throwable)
        runCatching { dump(thread, throwable) }
        previous?.uncaughtException(thread, throwable) ?: exitProcess(1)
    }

    private fun dump(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val body = buildString {
            appendLine("DRDC crash report")
            appendLine("time      : $timestamp")
            appendLine("package   : ${app.packageName}")
            appendLine("version   : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device    : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("android   : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("kernel    : ${System.getProperty("os.version")}")
            appendLine("thread    : ${thread.name}")
            appendLine()
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))
            append(writer.toString())
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
