package me.weishu.kernelsu.ui.util

import com.topjohnwu.superuser.ShellUtils
import java.security.SecureRandom

/**
 * SKRoot-Lite style device root key.
 *
 * The key lives in `/data/adb/drdc/.root_key` (0600). The kernel reads it only
 * when a process runs `su` with `DRDC_ROOT_KEY=<key>` in its environment. Any
 * process that knows the key gets root without being added to the allowlist.
 *
 * All methods perform root shell I/O, so call them from a background thread.
 */
object RootKey {

    const val KEY_LEN = 48

    private const val PATH = "/data/adb/drdc/.root_key"
    private const val PARAM = "/sys/module/drdc/parameters/root_key_auth"
    private const val KEY_ONLY_PARAM = "/sys/module/drdc/parameters/root_key_only"
    private const val KEY_ONLY_FLAG = "/data/adb/drdc/root_key_only"
    private const val ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    /** True when the flashed kernel module has the root-key gate. */
    fun isSupported(): Boolean = runCatching {
        ShellUtils.fastCmd(getRootShell(), "test -e $PARAM && echo ok").trim() == "ok"
    }.getOrDefault(false)

    /** Current key, or null when it has not been generated / cannot be read. */
    fun read(): String? = runCatching {
        val key = ShellUtils.fastCmd(getRootShell(), "cat $PATH 2>/dev/null").trim()
        key.takeIf { it.length == KEY_LEN }
    }.getOrNull()

    /** Creates a key on first use; keeps the existing one otherwise. */
    fun ensure(): String? = read() ?: reset()

    /** True when the flashed kernel module supports key-only mode. */
    fun isKeyOnlySupported(): Boolean = runCatching {
        ShellUtils.fastCmd(getRootShell(), "test -e $KEY_ONLY_PARAM && echo ok").trim() == "ok"
    }.getOrDefault(false)

    /** Whether allowlisted apps must also present the key. */
    fun isKeyOnlyEnabled(): Boolean = runCatching {
        when (ShellUtils.fastCmd(getRootShell(), "cat $KEY_ONLY_PARAM 2>/dev/null").trim()) {
            "Y", "1" -> true
            else -> false
        }
    }.getOrDefault(false)

    /** Applies key-only mode and makes it survive a reboot via the flag file. */
    fun applyKeyOnly(enabled: Boolean): Boolean = runCatching {
        val command = if (enabled) {
            "mkdir -p /data/adb/drdc; touch $KEY_ONLY_FLAG; echo 1 > $KEY_ONLY_PARAM"
        } else {
            "mkdir -p /data/adb/drdc; rm -f $KEY_ONLY_FLAG; echo 0 > $KEY_ONLY_PARAM"
        }
        val value = ShellUtils.fastCmd(getRootShell(), "$command 2>/dev/null; cat $KEY_ONLY_PARAM 2>/dev/null").trim()
        if (enabled) value == "Y" || value == "1" else value == "N" || value == "0"
    }.getOrDefault(false)

    /** Replaces the key and returns the new one. */
    fun reset(): String? = runCatching {
        val key = generate()
        // The alphabet is alphanumeric, so the value is safe to interpolate.
        val command =
            "umask 077; mkdir -p /data/adb/drdc; " +
                "printf '%s' '$key' > $PATH; chmod 600 $PATH; cat $PATH"
        val out = ShellUtils.fastCmd(getRootShell(), command).trim()
        out.takeIf { it == key }
    }.getOrNull()

    private fun generate(): String {
        val random = SecureRandom()
        val sb = StringBuilder(KEY_LEN)
        repeat(KEY_LEN) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }
}
