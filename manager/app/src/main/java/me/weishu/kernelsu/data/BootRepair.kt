package me.weishu.kernelsu.data

import com.topjohnwu.superuser.ShellUtils
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ui.util.getRootShell
import me.weishu.kernelsu.ui.util.rootAvailable

/**
 * On some devices init never manages to exec `/data/adb/drdcd post-fs-data` (the rc
 * injection or the early-boot exec fails). Modules then stay in `modules_update`, an
 * uninstalled module keeps its `.remove` marker forever, and the allowlist is not
 * restored. When we can prove that happened, run the stage once from the manager so the
 * device recovers without waiting for a fixed kernel.
 */
object BootRepair {

    private val SCRIPT = """
        boot=${'$'}(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
        [ -z "${'$'}boot" ] && exit 0
        last=${'$'}(cat /data/adb/drdc/.boot_repair 2>/dev/null)
        [ "${'$'}boot" = "${'$'}last" ] && exit 0
        # The kernel writes .allowlist here and drdcd writes .feature_config here.
        # If this directory is missing, grants and kernel feature settings are never
        # persisted. Create it before touching anything else.
        mkdir -p /data/adb/drdc/log /data/adb/drdc/bin
        chmod 700 /data/adb/drdc 2>/dev/null
        # Re-apply saved kernel feature settings (su compat, adb root, sulog, ...).
        /data/adb/drdcd feature load >> /data/adb/drdc/log/boot_repair.log 2>&1
        need=0
        ls /data/adb/modules/*/.remove >/dev/null 2>&1 && need=1
        if [ -d /data/adb/modules_update ] && [ -n "${'$'}(ls -A /data/adb/modules_update 2>/dev/null)" ]; then
            need=1
        fi
        if [ "${'$'}need" = 1 ]; then
            /data/adb/drdcd post-fs-data >> /data/adb/drdc/log/boot_repair.log 2>&1
        fi
        echo "${'$'}boot" > /data/adb/drdc/.boot_repair
    """.trimIndent()

    /** Must be called from a background thread. */
    fun maybeRun() {
        val isManager = runCatching { Natives.isManager }.getOrDefault(false)
        val version = runCatching { Natives.version }.getOrDefault(0)
        if (!isManager || version <= 0) return
        if (!rootAvailable()) return
        runCatching { ShellUtils.fastCmd(getRootShell(), SCRIPT) }
    }
}
