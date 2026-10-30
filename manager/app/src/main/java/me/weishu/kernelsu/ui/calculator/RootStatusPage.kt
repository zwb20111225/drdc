package me.weishu.kernelsu.ui.calculator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import me.weishu.kernelsu.Natives
import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Fallback for the disguised entry: when the unlock code is entered but no manager APK is
 * installed, serve a single local page over loopback and open it with the default browser.
 *
 * The socket is bound to 127.0.0.1 only, serves one page, and closes itself after
 * [LIFETIME_MS] so nothing keeps listening after the user is done.
 */
object RootStatusPage {

    private const val LIFETIME_MS = 90_000L

    @Volatile
    private var current: ServerSocket? = null

    fun open(context: Context) {
        val app = context.applicationContext
        val socket = runCatching {
            ServerSocket(0, 4, InetAddress.getLoopbackAddress()).apply { reuseAddress = true }
        }.getOrNull() ?: return

        runCatching { current?.close() }
        current = socket

        Thread({ serve(socket) }, "drdc-root-page").apply {
            isDaemon = true
            start()
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${socket.localPort}/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }

    private fun serve(socket: ServerSocket) {
        val deadline = System.currentTimeMillis() + LIFETIME_MS
        runCatching { socket.soTimeout = 1_000 }
        try {
            while (System.currentTimeMillis() < deadline) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                runCatching {
                    client.use { c ->
                        c.soTimeout = 2_000
                        val reader = c.getInputStream().bufferedReader()
                        reader.readLine()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        respond(c.getOutputStream(), render())
                    }
                }
            }
        } finally {
            runCatching { socket.close() }
            if (current === socket) current = null
        }
    }

    private fun respond(out: OutputStream, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun render(): String {
        val ksuVersion = runCatching { Natives.version }.getOrDefault(0)
        val selinux = runCatching { File("/sys/fs/selinux/enforce").readText().trim() }
            .getOrNull()
        val selinuxText = when (selinux) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            else -> "未知"
        }
        val kernel = System.getProperty("os.version").orEmpty()
        val device = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Root 状态</title>
            <style>
              :root { color-scheme: dark; }
              body { margin:0; min-height:100vh; display:flex; align-items:center;
                     justify-content:center; background:#0b0e14; color:#e8edf6;
                     font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; padding:24px; }
              .card { width:min(430px,100%); border-radius:24px; padding:26px 22px;
                      background:#141a26; border:1px solid #24304a; }
              h1 { font-size:20px; margin:0 0 4px; }
              .ok { color:#4ade80; font-size:13px; margin:0 0 18px; }
              table { width:100%; border-collapse:collapse; font-size:14px; }
              td { padding:8px 0; border-bottom:1px solid #1e2739; }
              td:first-child { color:#8ea0bb; width:42%; }
              td:last-child { text-align:right; word-break:break-all; }
              .tip { margin:18px 0 0; font-size:12px; color:#748196; line-height:1.7; }
            </style>
            </head>
            <body>
            <main class="card">
              <h1>Root 已获取</h1>
              <p class="ok">内核级 Root · UID 0</p>
              <table>
                <tr><td>设备</td><td>${device.ifBlank { "未知" }}</td></tr>
                <tr><td>Android</td><td>${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})</td></tr>
                <tr><td>内核</td><td>${kernel.ifBlank { "未知" }}</td></tr>
                <tr><td>SELinux</td><td>$selinuxText</td></tr>
                <tr><td>管理器 UID</td><td>${Process.myUid()}</td></tr>
                <tr><td>UAPI 版本</td><td>${if (ksuVersion > 0) ksuVersion else "未连接"}</td></tr>
              </table>
              <p class="tip">没有检测到面具（Magisk）。装好面具后回到计算器再输一次解锁码即可直接打开。<br>
              本页面只在 127.0.0.1 上临时提供，90 秒后自动关闭。</p>
            </main>
            </body>
            </html>
        """.trimIndent()
    }
}
