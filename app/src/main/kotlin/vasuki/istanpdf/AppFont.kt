package vasuki.istanpdf

import android.graphics.Typeface
import vasuki.istanpdf.di.AppModule

object AppFont {
    private const val FONT_ASSET = "vasuki.ttf"

    val regular: Typeface by lazy { buildWeight(400) }
    val medium: Typeface by lazy { buildWeight(500) }
    val semiBold: Typeface by lazy { buildWeight(600) }
    val bold: Typeface by lazy { buildWeight(700) }

    private fun buildWeight(weight: Int): Typeface =
        Typeface.Builder(AppModule.get().context.assets, FONT_ASSET)
            .setFontVariationSettings("'wght' $weight")
            .setWeight(weight)
            .build()
}
