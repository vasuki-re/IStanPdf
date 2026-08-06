package vasuki.istanpdf.presentation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView(context: Context) : AppCompatImageView(context) {

    private val imgMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val savedMatrix = Matrix()

    private var currentScale = 1f
    private val minScale = 1f
    private val maxScale = 5f
    private val doubleTapScale = 2.5f

    private var bitmapWidth = 0f
    private var bitmapHeight = 0f
    private var viewWidth = 0
    private var viewHeight = 0
    private var baseMatrix = Matrix()
    private var isReady = false

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private var zoomAnimator: ValueAnimator? = null

    init {
        scaleType = ScaleType.MATRIX

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                var scaleFactor = detector.scaleFactor
                val newScale = currentScale * scaleFactor
                if (newScale > maxScale) scaleFactor = maxScale / currentScale
                if (newScale < minScale) scaleFactor = minScale / currentScale
                imgMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                currentScale *= scaleFactor
                clampTranslation()
                imageMatrix = imgMatrix
                updateTouchIntercept()
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (currentScale > minScale + 0.01f) {
                    imgMatrix.postTranslate(-distanceX, -distanceY)
                    clampTranslation()
                    imageMatrix = imgMatrix
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val targetScale = if (currentScale < doubleTapScale - 0.1f) doubleTapScale else minScale
                animateZoom(currentScale, targetScale, e.x, e.y)
                return true
            }
        })
    }

    fun setImageBitmapAndReset(bitmap: Bitmap?) {
        setImageBitmap(bitmap)
        if (bitmap != null) {
            bitmapWidth = bitmap.width.toFloat()
            bitmapHeight = bitmap.height.toFloat()
            if (viewWidth > 0 && viewHeight > 0) {
                setupBaseMatrix()
            }
        }
    }

    fun resetZoom() {
        zoomAnimator?.cancel()
        currentScale = 1f
        imgMatrix.set(baseMatrix)
        imageMatrix = imgMatrix
        updateTouchIntercept()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        if (bitmapWidth > 0 && bitmapHeight > 0) {
            setupBaseMatrix()
        }
    }

    private fun setupBaseMatrix() {
        baseMatrix = Matrix()
        val scaleX = viewWidth / bitmapWidth
        val scaleY = viewHeight / bitmapHeight
        val scale = minOf(scaleX, scaleY)
        val dx = (viewWidth - bitmapWidth * scale) / 2f
        val dy = (viewHeight - bitmapHeight * scale) / 2f
        baseMatrix.setScale(scale, scale)
        baseMatrix.postTranslate(dx, dy)

        currentScale = 1f
        imgMatrix.set(baseMatrix)
        imageMatrix = imgMatrix
        isReady = true
        updateTouchIntercept()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isReady) return super.onTouchEvent(event)

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_DOWN) {
            updateTouchIntercept()
        }
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }

        return true
    }

    private fun updateTouchIntercept() {
        parent?.requestDisallowInterceptTouchEvent(currentScale > minScale + 0.01f)
    }

    private fun clampTranslation() {
        val rect = mappedImageRect() ?: return
        var dx = 0f
        var dy = 0f

        if (rect.width() <= viewWidth) {
            dx = (viewWidth - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) dx = -rect.left
            if (rect.right < viewWidth) dx = viewWidth - rect.right
        }

        if (rect.height() <= viewHeight) {
            dy = (viewHeight - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) dy = -rect.top
            if (rect.bottom < viewHeight) dy = viewHeight - rect.bottom
        }

        imgMatrix.postTranslate(dx, dy)
    }

    private fun mappedImageRect(): RectF? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        val rect = RectF(0f, 0f, bitmapWidth, bitmapHeight)
        imgMatrix.mapRect(rect)
        return rect
    }

    private fun animateZoom(from: Float, to: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()
        zoomAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            var prev = from
            addUpdateListener { anim ->
                val cur = anim.animatedValue as Float
                val factor = cur / prev
                imgMatrix.postScale(factor, factor, focusX, focusY)
                currentScale = cur
                clampTranslation()
                imageMatrix = imgMatrix
                prev = cur
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    updateTouchIntercept()
                }
            })
            start()
        }
    }
}
