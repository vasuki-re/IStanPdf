package vasuki.istanpdf

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.TypefaceSpan

class CustomTypefaceSpan(private val typeface: Typeface) : TypefaceSpan("") {

    override fun updateDrawState(ds: TextPaint) {
        ds.typeface = typeface
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = typeface
    }
}
