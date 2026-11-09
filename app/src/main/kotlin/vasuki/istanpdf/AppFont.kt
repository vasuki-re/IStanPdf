package vasuki.istanpdf

import android.graphics.Typeface
import android.os.Build
import vasuki.istanpdf.di.AppModule
import java.util.Locale

object AppFont {
    val regular: Typeface
        get() = resolveTypeface(R.font.vasuki_regular, Typeface.NORMAL)
    val medium: Typeface
        get() = resolveTypeface(R.font.vasuki_medium, Typeface.NORMAL)
    val semiBold: Typeface
        get() = resolveTypeface(R.font.vasuki_semibold, Typeface.BOLD)

    private fun resolveTypeface(fontResId: Int, style: Int): Typeface {
        if (isKoreanLocale()) {
            return Typeface.create(Typeface.SANS_SERIF, style)
        }
        return AppModule.get().context.resources.getFont(fontResId)
    }

    private fun isKoreanLocale(): Boolean {
        val config = AppModule.get().context.resources.configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
        return locale.language == Locale.KOREAN.language
    }
}
