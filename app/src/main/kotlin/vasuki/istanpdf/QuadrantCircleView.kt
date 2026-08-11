package vasuki.istanpdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.min
import kotlin.math.roundToInt

class QuadrantCircleView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circleBounds = RectF()
    private var accent: ThemePrefs.Accent? = null
    private var selected = false
    private var amoled = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun bind(accent: ThemePrefs.Accent, selected: Boolean, amoled: Boolean) {
        this.accent = accent
        this.selected = selected
        this.amoled = amoled
        isSelected = selected
        isFocusable = true
        contentDescription = accent.name + " accent" + if (selected) ", selected" else ""
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.RadioButton"
        info.isCheckable = true
        info.isChecked = selected
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val accent = accent ?: return

        val inset = dp(if (selected) 3 else 4)
        val size = min(width, height) - (2 * inset)
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        circleBounds.set(left, top, left + size, top + size)
        val colors = ThemePrefs.quadrantColors(accent, amoled)
        for (i in colors.indices) {
            paint.style = Paint.Style.FILL
            paint.color = colors[i]
            canvas.drawArc(circleBounds, -90f + (i * 90), if (amoled) 90.5f else 90f, true, paint)
        }

        val strokeColor = if (selected) ThemePrefs.accentForeground(accent, amoled)
        else ThemePrefs.tint(accent.dark, if (amoled) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), if (amoled) 0.2f else 0.58f)

        paint.style = Paint.Style.STROKE
        paint.color = strokeColor

        if (!amoled) {
            paint.strokeWidth = dp(1).toFloat()
            val cx = circleBounds.centerX()
            val cy = circleBounds.centerY()
            canvas.drawLine(cx, circleBounds.top, cx, circleBounds.bottom, paint)
            canvas.drawLine(circleBounds.left, cy, circleBounds.right, cy, paint)
        }

        paint.strokeWidth = dp(if (selected) 2 else 1).toFloat()
        canvas.drawOval(circleBounds, paint)
    }

    private fun dp(value: Int): Float =
        Math.round(value * resources.displayMetrics.density).toFloat()
}
