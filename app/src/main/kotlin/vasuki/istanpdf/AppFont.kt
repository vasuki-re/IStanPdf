package vasuki.istanpdf

import android.graphics.Typeface
import vasuki.istanpdf.di.AppModule

object AppFont {
    val regular: Typeface by lazy { AppModule.get().context.resources.getFont(R.font.vasuki_regular) }
    val semiBold: Typeface by lazy { AppModule.get().context.resources.getFont(R.font.vasuki_semibold) }
}
