package vasuki.istanpdf.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class CropOverlayView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = Color.parseColor("#B3000000") }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = dp(1).toFloat()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(5).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    private val displayMatrix = Matrix()
    private val sideInset = dp(24).toFloat()
    private var imgLeft = 0f
    private var imgTop = 0f
    private var imgRight = 0f
    private var imgBottom = 0f
    private val cropRect = RectF()

    private var activeHandle = NONE
    private var lastX = 0f
    private var lastY = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setImage(bmp: Bitmap) {
        bitmap = bmp
        if (width > 0 && height > 0) {
            computeLayout(bmp)
        }
        invalidate()
    }

    fun cropBitmap(): Bitmap {
        val bmp = bitmap ?: throw IllegalStateException("No image set")
        val inv = Matrix()
        if (!displayMatrix.invert(inv)) return bmp
        val src = RectF()
        inv.mapRect(src, cropRect)
        val l = src.left.coerceIn(0f, bmp.width.toFloat())
        val t = src.top.coerceIn(0f, bmp.height.toFloat())
        val r = src.right.coerceIn(0f, bmp.width.toFloat())
        val b = src.bottom.coerceIn(0f, bmp.height.toFloat())
        val w = (r - l).toInt()
        val h = (b - t).toInt()
        if (w <= 0 || h <= 0) return bmp
        return Bitmap.createBitmap(bmp, l.toInt(), t.toInt(), w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bmp = bitmap ?: return
        computeLayout(bmp)
    }

    private fun computeLayout(bmp: Bitmap) {
        val availW = width - 2 * sideInset
        val availH = height - 2 * sideInset
        val scale = minOf(availW / bmp.width, availH / bmp.height)
        val dx = sideInset + (availW - bmp.width * scale) / 2f
        val dy = sideInset + (availH - bmp.height * scale) / 2f
        displayMatrix.setScale(scale, scale)
        displayMatrix.postTranslate(dx, dy)
        val rect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        displayMatrix.mapRect(rect)
        imgLeft = rect.left
        imgTop = rect.top
        imgRight = rect.right
        imgBottom = rect.bottom
        cropRect.set(rect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, displayMatrix, imagePaint)

        val l = cropRect.left
        val t = cropRect.top
        val r = cropRect.right
        val b = cropRect.bottom

        canvas.drawRect(imgLeft, imgTop, imgRight, t, dimPaint)
        canvas.drawRect(imgLeft, b, imgRight, imgBottom, dimPaint)
        canvas.drawRect(imgLeft, t, l, b, dimPaint)
        canvas.drawRect(r, t, imgRight, b, dimPaint)

        val thirdW = (r - l) / 3f
        val thirdH = (b - t) / 3f
        for (i in 1..2) {
            canvas.drawLine(l + thirdW * i, t, l + thirdW * i, b, gridPaint)
            canvas.drawLine(l, t + thirdH * i, r, t + thirdH * i, gridPaint)
        }

        canvas.drawRect(cropRect, borderPaint)

        val h = dp(22).toFloat()
        canvas.drawLine(l, t, l + h, t, handlePaint)
        canvas.drawLine(l, t, l, t + h, handlePaint)
        canvas.drawLine(r, t, r - h, t, handlePaint)
        canvas.drawLine(r, t, r, t + h, handlePaint)
        canvas.drawLine(l, b, l + h, b, handlePaint)
        canvas.drawLine(l, b, l, b - h, handlePaint)
        canvas.drawLine(r, b, r - h, b, handlePaint)
        canvas.drawLine(r, b, r, b - h, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = detectHandle(x, y)
                lastX = x
                lastY = y
                if (activeHandle != NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle == NONE) return false
                val dx = x - lastX
                val dy = y - lastY
                applyDrag(activeHandle, dx, dy)
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun detectHandle(x: Float, y: Float): Int {
        val slop = dp(28).toFloat()
        val l = cropRect.left
        val t = cropRect.top
        val r = cropRect.right
        val b = cropRect.bottom

        if (x >= l - slop && x <= l + slop && y >= t - slop && y <= t + slop) return LEFT_TOP
        if (x >= r - slop && x <= r + slop && y >= t - slop && y <= t + slop) return RIGHT_TOP
        if (x >= l - slop && x <= l + slop && y >= b - slop && y <= b + slop) return LEFT_BOTTOM
        if (x >= r - slop && x <= r + slop && y >= b - slop && y <= b + slop) return RIGHT_BOTTOM
        if (x >= l - slop && x <= l + slop) return LEFT
        if (x >= r - slop && x <= r + slop) return RIGHT
        if (y >= t - slop && y <= t + slop) return TOP
        if (y >= b - slop && y <= b + slop) return BOTTOM
        if (cropRect.contains(x, y)) return MOVE
        return NONE
    }

    private fun applyDrag(handle: Int, dx: Float, dy: Float) {
        val minSize = dp(100).toFloat()
        val l = cropRect.left
        val t = cropRect.top
        val r = cropRect.right
        val b = cropRect.bottom

        when (handle) {
            MOVE -> {
                val w = r - l
                val h = b - t
                val nl = (l + dx).coerceIn(imgLeft, imgRight - w)
                val nt = (t + dy).coerceIn(imgTop, imgBottom - h)
                cropRect.set(nl, nt, nl + w, nt + h)
            }
            LEFT_TOP -> {
                val nl = (l + dx).coerceIn(imgLeft, r - minSize)
                val nt = (t + dy).coerceIn(imgTop, b - minSize)
                cropRect.set(nl, nt, r, b)
            }
            RIGHT_TOP -> {
                val nr = (r + dx).coerceIn(l + minSize, imgRight)
                val nt = (t + dy).coerceIn(imgTop, b - minSize)
                cropRect.set(l, nt, nr, b)
            }
            LEFT_BOTTOM -> {
                val nl = (l + dx).coerceIn(imgLeft, r - minSize)
                val nb = (b + dy).coerceIn(t + minSize, imgBottom)
                cropRect.set(nl, t, r, nb)
            }
            RIGHT_BOTTOM -> {
                val nr = (r + dx).coerceIn(l + minSize, imgRight)
                val nb = (b + dy).coerceIn(t + minSize, imgBottom)
                cropRect.set(l, t, nr, nb)
            }
            LEFT -> {
                val nl = (l + dx).coerceIn(imgLeft, r - minSize)
                cropRect.set(nl, t, r, b)
            }
            RIGHT -> {
                val nr = (r + dx).coerceIn(l + minSize, imgRight)
                cropRect.set(l, t, nr, b)
            }
            TOP -> {
                val nt = (t + dy).coerceIn(imgTop, b - minSize)
                cropRect.set(l, nt, r, b)
            }
            BOTTOM -> {
                val nb = (b + dy).coerceIn(t + minSize, imgBottom)
                cropRect.set(l, t, r, nb)
            }
        }
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

    companion object {
        private const val NONE = -1
        private const val MOVE = 0
        private const val LEFT_TOP = 1
        private const val RIGHT_TOP = 2
        private const val LEFT_BOTTOM = 3
        private const val RIGHT_BOTTOM = 4
        private const val LEFT = 5
        private const val TOP = 6
        private const val RIGHT = 7
        private const val BOTTOM = 8
    }
}
