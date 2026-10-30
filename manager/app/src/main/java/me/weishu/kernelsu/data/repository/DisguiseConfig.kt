package me.weishu.kernelsu.data.repository

import android.content.Context
import me.weishu.kernelsu.ksuApp

/**
 * Hidden entry settings.
 *
 * There is no launcher-icon switch and no accessibility service any more: the calculator
 * trigger lives on the root side (drdcd + the kernel module), which reads touch input at
 * the input subsystem and raises the manager itself. The manager only persists the code
 * and shows it in Settings; nothing in this APK watches other apps.
 *
 * The code contains digits and the four operators plus the decimal point, because those
 * are the keys every stock calculator exposes. Both sides go through [normalize] so
 * `×`/`*`, `÷`/`/` and the Unicode minus all compare equal.
 */
object DisguiseConfig {
    const val SETTINGS_PREFS = "settings"
    const val KEY_DISGUISE_PASSCODE = "disguise_passcode"

    /** Default unlock code: 112233, then `=`. */
    const val DEFAULT_PASSCODE = "112233"
    const val MIN_PASSCODE_LENGTH = 4
    const val MAX_PASSCODE_LENGTH = 8

    /** Keys the calculator offers besides digits. */
    const val ALLOWED_SYMBOLS = "+-*/."

    /** Name of the `activity-alias` that shows the launcher entry. */
    const val WINTER_ALIAS = "me.weishu.kernelsu.ui.WinterAlias"

    /**
     * Canonical form used for comparison and storage: full-width / typographic operators are
     * folded to the ASCII ones so the same string is shown in Settings and typed in the
     * calculator.
     */
    fun normalize(value: String): String = value
        .map { c ->
            when (c) {
                '\u00d7', 'x', 'X' -> '*'
                '\u00f7' -> '/'
                '\u2212', '\u2013', '\u2014' -> '-'
                else -> c
            }
        }
        .joinToString("")

    /** Drops everything the calculator cannot type, so the field and the keypad agree. */
    fun sanitize(value: String): String = normalize(value)
        .filter { it.isDigit() || it in ALLOWED_SYMBOLS }
        .take(MAX_PASSCODE_LENGTH)

    fun isValidPasscode(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.length in MIN_PASSCODE_LENGTH..MAX_PASSCODE_LENGTH &&
            normalized.all { it.isDigit() || it in ALLOWED_SYMBOLS } &&
            !normalized.startsWith("0")
    }

    /** Reads the code straight from prefs so the root side picks up edits immediately. */
    fun readPasscode(): String =
        ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISGUISE_PASSCODE, DEFAULT_PASSCODE)
            ?.let { normalize(it) }
            ?.takeIf { isValidPasscode(it) }
            ?: DEFAULT_PASSCODE
}
