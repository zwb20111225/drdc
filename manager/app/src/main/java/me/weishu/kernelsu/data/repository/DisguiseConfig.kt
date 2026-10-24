package me.weishu.kernelsu.data.repository

import android.content.Context
import me.weishu.kernelsu.ksuApp

/**
 * Settings for the calculator disguise entry.
 *
 * Keep in sync with AndroidManifest.xml:
 * - `me.weishu.kernelsu.ui.CalculatorAlias` is enabled by default and only shows a calculator.
 * - [WINTER_ALIAS] is disabled by default and can be toggled from the settings screen.
 */
object DisguiseConfig {
    const val SETTINGS_PREFS = "settings"
    const val KEY_DISGUISE_PASSCODE = "disguise_passcode"
    const val KEY_SHOW_LAUNCHER_ICON = "show_launcher_icon"

    /** Default unlock code: type it in the calculator, then press `=`. */
    const val DEFAULT_PASSCODE = "112233"
    const val MIN_PASSCODE_LENGTH = 4
    const val MAX_PASSCODE_LENGTH = 12

    /** Name of the `activity-alias` that shows the real launcher entry. */
    const val WINTER_ALIAS = "me.weishu.kernelsu.ui.WinterAlias"

    fun isValidPasscode(value: String): Boolean =
        value.length in MIN_PASSCODE_LENGTH..MAX_PASSCODE_LENGTH &&
            value.all { it.isDigit() } &&
            !value.startsWith("0")

    /** Reads the code straight from prefs so the calculator picks up edits immediately. */
    fun readPasscode(): String =
        ksuApp.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISGUISE_PASSCODE, DEFAULT_PASSCODE)
            ?.takeIf { isValidPasscode(it) }
            ?: DEFAULT_PASSCODE
}
