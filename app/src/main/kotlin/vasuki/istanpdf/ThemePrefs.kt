package vasuki.istanpdf

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.min
import kotlin.math.min
import kotlin.math.roundToInt

object ThemePrefs {
    const val PREFS_NAME = "istan_theme"
    private const val KEY_ACCENT_INDEX = "accent_index"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_THEME_MODE = "theme_mode"

    const val THEME_AUTO = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    data class Accent(val name: String, val base: Int, val dark: Int)

    val ACCENTS = arrayOf(
        Accent("Olive", Color.parseColor("#AEC09A"), Color.parseColor("#8FA67B")),
        Accent("Rose", Color.parseColor("#E2B6B3"), Color.parseColor("#D49A96")),
        Accent("Terracotta", Color.parseColor("#C28B7B"), Color.parseColor("#A66B5A")),
        Accent("Sand", Color.parseColor("#DFCA98"), Color.parseColor("#C2A970")),
        Accent("Aqua", Color.parseColor("#98C9A7"), Color.parseColor("#76A886")),
        Accent("Gold", Color.parseColor("#E5CD94"), Color.parseColor("#C9AE6D"))
    )

    enum class TypeScale(val sp: Float, val isBold: Boolean, val letterSpacing: Float = 0f, val isAllCaps: Boolean = false) {
        HEADLINE(32f, true, -0.02f, false),
        TITLE(22f, true, 0f, false),
        BODY(16f, false, 0f, false),
        LABEL(14f, true, 0f, false),
        CAPTION(12f, false, 0.04f, true)
    }

    private val LAUNCHER_ALIASES = arrayOf(
        "vasuki.istanpdf.LauncherOlive",
        "vasuki.istanpdf.LauncherRose",
        "vasuki.istanpdf.LauncherTerracotta",
        "vasuki.istanpdf.LauncherSand",
        "vasuki.istanpdf.LauncherAqua",
        "vasuki.istanpdf.LauncherGold"
    )

    private var iconHandler: Handler? = null
    private var iconRunnable: Runnable? = null

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun accentIndex(context: Context): Int {
        val index = prefs(context).getInt(KEY_ACCENT_INDEX, 0)
        return if (index < 0 || index >= ACCENTS.size) 0 else index
    }

    fun setAccentIndex(context: Context, index: Int) {
        if (index < 0 || index >= ACCENTS.size) return
        prefs(context).edit().putInt(KEY_ACCENT_INDEX, index).apply()

        if (iconHandler == null) {
            iconHandler = Handler(Looper.getMainLooper())
        }
        iconRunnable?.let { iconHandler?.removeCallbacks(it) }
        val appContext = context.applicationContext
        iconRunnable = Runnable { applyLauncherIconSilent(appContext) }
        iconHandler?.postDelayed(iconRunnable!!, 500)
    }

    fun applyLauncherIconSilent(context: Context) {
        val targetIndex = accentIndex(context)
        try {
            val pm = context.packageManager
            for (i in LAUNCHER_ALIASES.indices) {
                val desiredState = if (i == targetIndex)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED

                val component = ComponentName(context, LAUNCHER_ALIASES[i])
                if (pm.getComponentEnabledSetting(component) != desiredState) {
                    pm.setComponentEnabledSetting(component, desiredState, PackageManager.DONT_KILL_APP)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun accent(context: Context): Accent = ACCENTS[accentIndex(context)]

    fun themeMode(context: Context): Int {
        val preferences = prefs(context)
        if (preferences.contains(KEY_THEME_MODE)) {
            val mode = preferences.getInt(KEY_THEME_MODE, THEME_AUTO)
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

    fun isAmoled(context: Context): Boolean {
        val mode = themeMode(context)
        if (mode == THEME_DARK) return true
        if (mode == THEME_LIGHT) return false
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun token(context: Context): String =
        "${accentIndex(context)}:${themeMode(context)}:${isAmoled(context)}"

    fun resolveColor(context: Context, colorRes: Int): Int {
        val accent = accent(context)
        val amoled = isAmoled(context)
        return when (colorRes) {
            R.color.istan_olive, R.color.istan_olive_dark -> accentForeground(accent, amoled)
            R.color.istan_background -> if (amoled) Color.BLACK else Color.parseColor("#FAFAFA")
            R.color.istan_surface -> if (amoled) Color.parseColor("#101010") else tint(accent.base, Color.WHITE, 0.90f)
            R.color.istan_surface_high -> if (amoled) Color.parseColor("#1A1A1A") else tint(accent.base, Color.WHITE, 0.80f)
            R.color.istan_text -> if (amoled) Color.WHITE else Color.parseColor("#1C1C1C")
            R.color.istan_text_muted -> if (amoled) Color.parseColor("#B9B9B9") else Color.parseColor("#505548")
            R.color.istan_outline -> if (amoled) Color.parseColor("#333333") else tint(accent.dark, Color.WHITE, 0.62f)
            else -> context.getColor(colorRes)
        }
    }

    fun accentForeground(accent: Accent, amoled: Boolean): Int =
        if (amoled) accent.base else accent.dark

    fun quadrantColors(accent: Accent, amoled: Boolean): IntArray {
        return if (amoled) {
            intArrayOf(
                accent.dark,
                accent.base,
                tint(accent.base, Color.WHITE, 0.32f),
                tint(accent.dark, Color.BLACK, 0.48f)
            )
        } else {
            intArrayOf(
                tint(accent.base, Color.WHITE, 0.14f),
                accent.base,
                accent.dark,
                tint(accent.dark, Color.WHITE, 0.42f)
            )
        }
    }

    fun tint(from: Int, to: Int, amount: Float): Int {
        val clamped = max(0f, min(1f, amount))
        val a = (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped).roundToInt()
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped).roundToInt()
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped).roundToInt()
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped).roundToInt()
        return Color.argb(a, r, g, b)
    }
}
