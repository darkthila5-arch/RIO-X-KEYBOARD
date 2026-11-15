package com.rio.board

import android.content.Context
import android.content.res.Configuration
import android.os.Build

/**
 * Colours for the two "follow the phone" themes.
 *  - Auto         : Dark when the phone is in dark mode, Light when it is not.
 *  - Material You : colours taken from the wallpaper (Android 12 and newer). Older phones fall back to Auto.
 * Every array is: background, key, special key, accent, active, pressed, text, hint, snow.
 */
object SystemTheme {

    fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun auto(night: Boolean): Array<String> =
        if (night) arrayOf(
            "#17181C", "#2B2D35", "#3A3D49", "#3B82F6", "#6D5BD0", "#5A5F73", "#FFFFFF", "#9AA3C0", "#FFFFFF"
        ) else arrayOf(
            "#E8EAF0", "#FFFFFF", "#C9CEDB", "#3B82F6", "#7C6CE0", "#B8BFD3", "#111111", "#5B6382", "#8FB8FF"
        )

    /** null when the phone has no wallpaper colours (Android 11 or older). */
    fun material(ctx: Context, night: Boolean): Array<String>? {
        if (Build.VERSION.SDK_INT < 31) return null
        return try {
            fun c(id: Int): String = String.format("#%08X", ctx.getColor(id))
            if (night) arrayOf(
                c(android.R.color.system_neutral1_900),
                c(android.R.color.system_neutral1_800),
                c(android.R.color.system_neutral2_700),
                c(android.R.color.system_accent1_500),
                c(android.R.color.system_accent3_500),
                c(android.R.color.system_neutral2_600),
                c(android.R.color.system_neutral1_50),
                c(android.R.color.system_neutral2_300),
                c(android.R.color.system_accent1_100)
            ) else arrayOf(
                c(android.R.color.system_neutral1_100),
                c(android.R.color.system_neutral1_10),
                c(android.R.color.system_neutral2_200),
                c(android.R.color.system_accent1_600),
                c(android.R.color.system_accent3_600),
                c(android.R.color.system_neutral2_300),
                c(android.R.color.system_neutral1_900),
                c(android.R.color.system_neutral2_600),
                c(android.R.color.system_accent1_300)
            )
        } catch (e: Exception) {
            null
        }
    }
}
