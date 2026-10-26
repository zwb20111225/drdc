package me.weishu.kernelsu

import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Boot tracing plus previous-run crash collection.
 *
 * A crash that happens on the UI thread before (or inside) the JVM handler never reaches
 * [CrashLogger], and a native crash never reaches Java at all. This keeps a tiny trace of
 * every startup stage and, on the next launch, uploads that trace together with logcat's
 * crash buffer and the newest tombstone. Everything is best effort and never touches the
 * main thread.
 */
object Diagnostics {

    private const val TAG = "DRDCDiag"

    private fun traceFile(context: Context) = File(context.filesDir, "boot-trace.txt")
    private fun lastUploadFile(context: Context) = File(context.filesDir, "diag-uploaded.txt")

    /** Append one startup stage. Cheap enough to call from onCreate. */
    fun mark(context: Context, stage: String) {
        runCatching {
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "$stamp pid=${android.os.Process.myPid()} $stage\n"
            val file = traceFile(context)
            if (file.length() > 256 * 1024) file.writeText("")
            file.appendText(line)
        }
    }

    /** Call once from Application.onCreate, after CrashLogger is installed. */
    fun start(context: Context) {
        val previous = runCatching { traceFile(context).takeIf { it.isFile }?.readText() }.getOrNull()
        mark(context, "application.onCreate")
        Thread({
            runCatching { collectAndUpload(context, previous) }
                .onFailure { Log.w(TAG, "diagnostics failed", it) }
        }, "drdc-diag").apply { isDaemon = true }.start()
    }

    private fun collectAndUpload(context: Context, previousTrace: String?) {
        // Give GrantBackup.restore / BootRepair a moment to run first, otherwise the
        // report always shows an empty allowlist even when the repair worked.
        runCatching { Thread.sleep(5000) }
        val crash = crashBuffer(context)
        val tombstone = newestTombstone(context)
        val allowlist = allowlistState()
        val kernelLog = if (rooted()) {
            shell(true, "dmesg | grep -iE 'drdc|anti_lpe|post_fs|post-fs' | tail -n 80", 6)
        } else {
            ""
        }
        val modules = if (rooted()) {
            shell(
                true,
                "ls -1 /data/adb/modules 2>/dev/null; echo '-- update --'; ls -1 /data/adb/modules_update 2>/dev/null",
                5,
            )
        } else {
            ""
        }
        val drdcDir = if (rooted()) {
            shell(true, "ls -ld /data/adb/drdc 2>/dev/null; ls -l /data/adb/drdc/.feature_config 2>/dev/null", 5)
        } else {
            ""
        }
        val rootKey = if (rooted()) {
            shell(true, "stat -c '%s bytes mode=%a' /data/adb/drdc/.root_key 2>/dev/null; cat /sys/module/drdc/parameters/root_key_auth 2>/dev/null", 5)
        } else {
            ""
        }
        val trace = previousTrace.orEmpty()
        if (crash.isBlank() && tombstone.isBlank() && trace.isBlank()) return

        val report = buildString {
            appendLine("DRDC startup diagnostics")
            appendLine("version  : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device   : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("platform : Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT} / kernel ${System.getProperty("os.version")}")
            appendLine("grants   : kernel count=${runCatching { Natives.getSuperuserCount() }.getOrNull() ?: "?"}")
            appendLine("grantBak : ${runCatching { me.weishu.kernelsu.data.GrantBackup.count(context) }.getOrDefault(-1)}")
            appendLine("allowlist: ${allowlist.ifBlank { "missing" }}")
            appendLine("modules  : ${modules.replace('\n', ' ').ifBlank { "none" }}")
            appendLine("drdcDir  : ${drdcDir.replace('\n', ' ').ifBlank { "missing" }}")
            appendLine("rootKey  : ${rootKey.replace('\n', ' ').ifBlank { "missing" }}")
            appendLine("manager  : ${runCatching { Natives.isManager }.getOrDefault(false)} / lateLoad=${runCatching { Natives.isLateLoadMode }.getOrDefault(false)}")
            appendLine("rootShell: ${runCatching { me.weishu.kernelsu.ui.util.rootAvailable() }.getOrDefault(false)}")
            appendLine("antiLpe  : ${runCatching { java.io.File("/sys/module/drdc/parameters/anti_lpe").readText().trim() }.getOrNull() ?: "?"}")
            val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            appendLine("settings : ui=${prefs.getString("ui_mode", "?")} update=${prefs.getBoolean("check_update", true)} antiLpe=${prefs.getBoolean("anti_lpe", true)} softReboot=${prefs.getBoolean("soft_reboot", false)}")
            appendLine()
            appendLine("===== previous boot trace =====")
            appendLine(trace.take(24 * 1024))
            if (crash.isNotBlank()) {
                appendLine()
                appendLine("===== logcat -b crash =====")
                appendLine(crash.take(24 * 1024))
            }
            if (tombstone.isNotBlank()) {
                appendLine()
                appendLine("===== newest tombstone =====")
                appendLine(tombstone.take(24 * 1024))
            }
            if (kernelLog.isNotBlank()) {
                appendLine()
                appendLine("===== kernel log (drdc/anti_lpe/post-fs) =====")
                appendLine(kernelLog.take(12 * 1024))
            }
        }.take(48 * 1024)

        // Do not upload the same crash on every launch.
        val bootId = runCatching {
            java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
        }.getOrDefault("")
        val signature = if (crash.isBlank() && tombstone.isBlank()) {
            // No crash: send one startup trace per boot so we can see post-reboot state.
            "trace:${BuildConfig.VERSION_CODE}:$bootId"
        } else {
            (crash + tombstone).hashCode().toString()
        }
        val last = runCatching { lastUploadFile(context).readText() }.getOrDefault("")
        if (last == signature) return

        val base = BuildConfig.KEY_SERVER_URL.trimEnd('/')
        if (base.isEmpty()) return
        val payload = JSONObject().apply {
            put("package", context.packageName)
            put("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) diag")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            put("platform", "Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            put("thread", "diag")
            put("stack", report)
            put("ts", System.currentTimeMillis() / 1000)
        }.toString()

        val client = OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(4, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("$base/api/v1/crash")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) runCatching { lastUploadFile(context).writeText(signature) }
        }
    }

    /** Only ask su when our kernel is actually loaded, to avoid pointless prompts. */
    private fun rooted(): Boolean = runCatching { Natives.version > 0 }.getOrDefault(false)

    private fun allowlistState(): String {
        if (!rooted()) return ""
        return shell(
            true,
            "ls -l /data/adb/drdc/.allowlist 2>/dev/null; od -An -tx1 -N8 /data/adb/drdc/.allowlist 2>/dev/null",
            5,
        ).replace('\n', ' ').trim()
    }

    private fun crashBuffer(context: Context): String {
        // Apps can usually only read their own log entries; try without root first and
        // fall back to su, which is what actually works on a rooted device.
        val plain = shell(false, "logcat -b crash -d -t 400", 5)
        if (plain.isNotBlank()) return plain
        if (!rooted()) return ""
        return shell(true, "logcat -b crash -d -t 400", 6)
    }

    private fun newestTombstone(context: Context): String {
        if (!rooted()) return ""
        val name = shell(true, "ls -t /data/tombstones 2>/dev/null | head -n 1", 5).trim()
        if (name.isEmpty()) return ""
        return shell(true, "cat /data/tombstones/$name 2>/dev/null", 6)
    }

    /** Runs a command with a hard timeout so diagnostics can never wedge a process. */
    private fun shell(root: Boolean, command: String, timeoutSeconds: Long): String {
        return runCatching {
            val builder = if (root) ProcessBuilder("su", "-c", command) else ProcessBuilder("sh", "-c", command)
            builder.redirectErrorStream(true)
            val process = builder.start()
            val output = StringBuilder()
            val reader = Thread({
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (output.length < 64 * 1024) output.append(line).append('\n')
                        }
                    }
                }
            }, "drdc-diag-io")
            reader.isDaemon = true
            reader.start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) process.destroyForcibly()
            reader.join(1500)
            output.toString().trim()
        }.getOrDefault("")
    }
}
