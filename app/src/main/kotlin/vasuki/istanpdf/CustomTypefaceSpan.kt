package vasuki.istanpdf

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.TypefaceSpan

class CustomTypefaceSpan(type: Typeface) : TypefaceSpan("") {
    private val newType = type

    override fun updateDrawState(ds: TextPaint) = applyCustomTypeFace(ds, newType)

    override fun updateMeasureState(paint: TextPaint) = applyCustomTypeFace(paint, newType)

    companion object {
        private fun applyCustomTypeFace(paint: Paint, tf: Typeface) {
            val oldStyle = paint.typeface?.style ?: 0
            val fake = oldStyle and tf.style.inv()
            if (fake and Typeface.BOLD != 0) paint.isFakeBoldText = true
            if (fake and Typeface.ITALIC != 0) paint.textSkewX = -0.25f
            paint.typeface = tf
        }
    }
}
