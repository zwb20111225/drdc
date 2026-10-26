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
        # Older installers could leave a regular file named /data/adb/drdc (usually a
        # stale drdcd binary). While it is a file, everything under it fails: the kernel
        # cannot save .allowlist, feature settings cannot be saved, root keys cannot be
        # written and the LKM install bails out. Repair it before anything else.
        repaired=0
        if [ -e /data/adb/drdc ] && [ ! -d /data/adb/drdc ]; then
            mv /data/adb/drdc "/data/adb/drdc.broken.${'$'}(date +%s)" 2>/dev/null && repaired=1
        fi
        mkdir -p /data/adb/drdc/log /data/adb/drdc/bin 2>/dev/null
        [ -d /data/adb/drdc ] || exit 0
        chmod 700 /data/adb/drdc 2>/dev/null
        last=${'$'}(cat /data/adb/drdc/.boot_repair 2>/dev/null)
        [ "${'$'}boot" = "${'$'}last" ] && [ "${'$'}repaired" = 0 ] && exit 0
        # Re-apply saved kernel feature settings (su compat, adb root, sulog, ...).
        /data/adb/drdcd feature load >> /data/adb/drdc/log/boot_repair.log 2>&1
        # If init never delivered post-fs-data, the kernel never loaded .allowlist and
        # every grant looks lost after a reboot. The force flag is 1 when the file has
        # data but the kernel reports zero grants.
        force=${'$'}forcePostFs
        need=0
        ls /data/adb/modules/*/.remove >/dev/null 2>&1 && need=1
        if [ -d /data/adb/modules_update ] && [ -n "${'$'}(ls -A /data/adb/modules_update 2>/dev/null)" ]; then
            need=1
        fi
        if [ "${'$'}need" = 1 ] || [ "${'$'}force" = 1 ]; then
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
        val forcePostFs = runCatching {
            val hasFile = ShellUtils.fastCmd(
                getRootShell(),
                "test -s /data/adb/drdc/.allowlist && echo yes"
            ).trim() == "yes"
            hasFile && Natives.getSuperuserCount() <= 0
        }.getOrDefault(false)
        val script = SCRIPT.replace("\$forcePostFs", if (forcePostFs) "1" else "0")
        runCatching { ShellUtils.fastCmd(getRootShell(), script) }
    }
}
