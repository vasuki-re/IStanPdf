package vasuki.istanpdf

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguagePrefs {
    private const val PREFS_NAME = "istan_language"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val PORTUGUESE_BRAZIL = "pt-BR"
    const val KOREAN = "ko"

    private val supported = setOf(SYSTEM, ENGLISH, PORTUGUESE_BRAZIL, KOREAN)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun selected(context: Context): String {
        val value = prefs(context).getString(KEY_LANGUAGE_TAG, SYSTEM) ?: SYSTEM
        return if (value in supported) value else SYSTEM
    }

    fun applySaved(context: Context) {
        apply(selected(context))
    }

    fun update(context: Context, languageTag: String) {
        val normalized = if (languageTag in supported) languageTag else SYSTEM
        prefs(context).edit().putString(KEY_LANGUAGE_TAG, normalized).apply()
        apply(normalized)
    }

    private fun apply(languageTag: String) {
        val locales = if (languageTag == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}