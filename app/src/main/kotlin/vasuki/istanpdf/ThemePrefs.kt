package vasuki.istanpdf

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color

object ThemePrefs {
    const val PREFS_NAME = "istan_theme"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_HIGH_CONTRAST = "hcm_enabled"
    private const val LEGACY_THEME_AMOLED = 3

    const val THEME_AUTO = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun themeMode(context: Context): Int {
        val preferences = prefs(context)
        if (preferences.contains(KEY_THEME_MODE)) {
            val mode = preferences.getInt(KEY_THEME_MODE, THEME_AUTO)
            if (mode == LEGACY_THEME_AMOLED) return THEME_DARK
            if (mode in THEME_AUTO..THEME_DARK) return mode
        }
        if (preferences.contains(KEY_AMOLED)) {
            return if (preferences.getBoolean(KEY_AMOLED, false)) THEME_DARK else THEME_LIGHT
        }
        return THEME_AUTO
    }

    fun setThemeMode(context: Context, mode: Int) {
        if (mode < THEME_AUTO || mode > THEME_DARK) return
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).remove(KEY_AMOLED).apply()
    }

    fun isHighContrast(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIGH_CONTRAST, false)

    fun setHighContrast(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun isDark(context: Context): Boolean {
        val mode = themeMode(context)
        if (mode == THEME_LIGHT) return false
        if (mode == THEME_DARK) return true
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun token(context: Context): String =
        "${themeMode(context)}:${isDark(context)}:${isHighContrast(context)}"

    fun resolveColor(context: Context, colorRes: Int): Int {
        val dark = isDark(context)
        val hcm = dark && isHighContrast(context)
        return when (colorRes) {
            R.color.istan_olive -> if (dark) Color.WHITE else Color.parseColor("#09090B")
            R.color.istan_olive_dark -> if (dark) Color.parseColor("#E4E4E7") else Color.parseColor("#27272A")
            R.color.istan_background -> if (dark) Color.BLACK else Color.parseColor("#FAFAFA")
            R.color.istan_surface -> if (hcm) Color.parseColor("#18181B") else if (dark) Color.parseColor("#0C0C0E") else Color.WHITE
            R.color.istan_surface_high -> if (hcm) Color.parseColor("#27272A") else if (dark) Color.parseColor("#1C1C1F") else Color.parseColor("#F4F4F5")
            R.color.istan_sheet -> if (hcm) Color.parseColor("#18181B") else if (dark) Color.parseColor("#0C0C0E") else Color.WHITE
            R.color.istan_outline -> if (hcm) Color.parseColor("#71717A") else if (dark) Color.parseColor("#27272A") else Color.parseColor("#E4E4E7")
            R.color.istan_text -> if (dark) Color.parseColor("#FAFAFA") else Color.parseColor("#09090B")
            R.color.istan_text_muted -> if (dark) Color.parseColor("#A1A1AA") else Color.parseColor("#52525B")
            R.color.istan_outline_emphasis -> if (hcm) Color.parseColor("#D4D4D8") else if (dark) Color.parseColor("#A1A1AA") else Color.parseColor("#71717A")
            R.color.istan_destructive -> if (dark) Color.parseColor("#F87171") else Color.parseColor("#DC2626")
            R.color.istan_scrim_sheet -> if (dark) Color.argb(178, 18, 18, 18) else Color.argb(82, 0, 0, 0)
            R.color.istan_scrim_immersive -> Color.parseColor("#E6252525")
            R.color.istan_glass -> Color.parseColor("#29FFFFFF")
            R.color.istan_placeholder -> if (hcm) Color.parseColor("#27272A") else if (dark) Color.parseColor("#1C1C1F") else Color.parseColor("#F4F4F5")
            else -> context.getColor(colorRes)
        }
    }
}
