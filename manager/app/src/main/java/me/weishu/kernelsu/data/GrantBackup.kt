package me.weishu.kernelsu.data

import android.content.Context
import me.weishu.kernelsu.Natives

/**
 * Some kernels do not manage to persist the allowlist across reboots: the kernel writes
 * `/data/adb/drdc/.allowlist` from init's task_work, which is not guaranteed to run on every
 * device. Keep a small copy of the grants applied through the manager and re-apply them when
 * the manager starts, so users do not have to authorize every app again after a reboot.
 */
object GrantBackup {

    private const val PREFS = "grant_backup"
    private const val KEY = "grants"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()).orEmpty()

    private fun write(context: Context, grants: Set<String>) {
        prefs(context).edit().putStringSet(KEY, grants).apply()
    }

    /** Call after a profile was successfully written to the kernel. */
    fun record(context: Context, profile: Natives.Profile) {
        val key = profile.name
        if (key.isEmpty() || key == "$" || profile.currentUid <= 0) return
        val entry = "$key:${profile.currentUid}"
        val grants = read(context).toMutableSet()
        if (profile.allowSu) grants.add(entry) else grants.remove(entry)
        write(context, grants)
    }

    private fun forget(context: Context, entry: String) {
        val grants = read(context).toMutableSet()
        if (grants.remove(entry)) write(context, grants)
    }

    fun count(context: Context): Int = read(context).size

    /**
     * Re-applies saved grants that the kernel no longer has. Safe to call from a background
     * thread; waits briefly for the manager to be recognised by the kernel (throne tracker
     * can run a moment after the app starts).
     */
    fun restore(context: Context) {
        var isManager = runCatching { Natives.isManager }.getOrDefault(false)
        var waited = 0
        while (!isManager && waited < 6000) {
            Thread.sleep(300)
            waited += 300
            isManager = runCatching { Natives.isManager }.getOrDefault(false)
        }
        if (!isManager) return
        val grants = read(context)
        if (grants.isEmpty()) return
        for (entry in grants) {
            val key = entry.substringBeforeLast(':')
            val uid = entry.substringAfterLast(':').toIntOrNull() ?: continue
            if (key.isEmpty() || uid <= 0) continue
            val currentUid = runCatching { context.packageManager.getPackageUid(key, 0) }.getOrNull()
            if (currentUid == null || currentUid != uid) {
                // Uninstalled or reinstalled with a different uid: the old grant is stale.
                forget(context, entry)
                continue
            }
            val profile = runCatching { Natives.getAppProfile(key, uid) }.getOrNull() ?: continue
            if (!profile.allowSu) {
                runCatching { Natives.setAppProfile(profile.copy(allowSu = true)) }
            }
        }
    }
}
