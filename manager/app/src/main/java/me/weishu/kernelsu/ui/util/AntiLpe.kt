package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.ShellUtils

/**
 * Controls the kernel-side PI-futex filter for CVE-2026-43499 (GhostLock).
 *
 * The switch lives in the kernel module (`/sys/module/drdc/parameters/anti_lpe`); the
 * persisted preference is re-applied at app start and the boot-time off flag keeps the
 * kernel consistent before the manager runs.
 */
object AntiLpe {

    private const val PARAM = "/sys/module/drdc/parameters/anti_lpe"
    private const val OFF_FLAG = "/data/adb/drdc/anti_lpe_off"

    /** True when the flashed kernel module exposes the toggle. */
    fun isSupported(): Boolean = runCatching {
        ShellUtils.fastCmd(getRootShell(), "test -e $PARAM && echo ok").trim() == "ok"
    }.getOrDefault(false)

    /** Current kernel value, or null when the module does not support it. */
    fun isEnabled(): Boolean? = runCatching {
        when (ShellUtils.fastCmd(getRootShell(), "cat $PARAM 2>/dev/null").trim()) {
            "Y", "1" -> true
            "N", "0" -> false
            else -> null
        }
    }.getOrNull()

    /** Applies the setting to the running kernel. Must be called off the main thread. */
    fun apply(enabled: Boolean): Boolean = runCatching {
        val command = if (enabled) {
            "mkdir -p /data/adb/drdc; rm -f $OFF_FLAG; echo 1 > $PARAM"
        } else {
            "mkdir -p /data/adb/drdc; touch $OFF_FLAG; echo 0 > $PARAM"
        }
        val value = ShellUtils.fastCmd(getRootShell(), "$command 2>/dev/null; cat $PARAM 2>/dev/null").trim()
        if (enabled) value == "Y" || value == "1" else value == "N" || value == "0"
    }.getOrDefault(false)
}
