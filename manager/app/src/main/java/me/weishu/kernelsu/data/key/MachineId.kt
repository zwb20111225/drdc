package me.weishu.kernelsu.data.key

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Machine fingerprint per SPEC §5.1: android_id + board serial + kernel build fingerprint +
 * CPU serial, hashed with SHA-256 and truncated to 16 hex chars. No privacy-sensitive fields
 * (IMEI, phone number, location) are collected.
 */
object MachineId {

    fun compute(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()

        val board = listOf(
            Build.BOARD,
            Build.HARDWARE,
            Build.DEVICE,
            Build.PRODUCT,
            Build.MANUFACTURER,
            Build.MODEL,
        ).joinToString("|")

        val kernel = listOf(
            System.getProperty("os.version").orEmpty(),
            Build.FINGERPRINT,
            Build.BOOTLOADER,
        ).joinToString("|")

        val cpu = listOf(
            readCpuSerial(),
            Build.SOC_MODEL,
            Build.SOC_MANUFACTURER,
        ).joinToString("|")

        val raw = listOf(androidId, board, kernel, cpu).joinToString("::")
        return sha256Hex(raw).take(16)
    }

    /** `a1b2c3d4e5f60718` → `a1b2-c3d4-e5f6-0718` */
    fun pretty(id: String): String = id.chunked(4).joinToString("-")

    private fun readCpuSerial(): String = runCatching {
        File("/proc/cpuinfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("Serial") }
                ?.substringAfter(':')
                ?.trim()
                .orEmpty()
        }
    }.getOrDefault("")

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
