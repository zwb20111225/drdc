package me.weishu.kernelsu.data.key

import android.content.Context

/**
 * Cosmetic display name entered together with the card key at activation. It is kept
 * separately from the token so the home greeting survives a heartbeat/sign-out cycle.
 */
object UserProfile {

    private const val PREFS = "drdc_user"
    private const val KEY = "username"

    fun username(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()

    fun setUsername(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, name.trim())
            .apply()
    }
}
