package vasuki.istanpdf.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

object BitmapUtils {

    fun loadImageUri(context: Context, uri: android.net.Uri, maxDim: Int): Bitmap {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)
        } ?: throw IllegalArgumentException("Cannot open image")
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw IllegalArgumentException("Cannot decode image")
        }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options()
        decodeOpts.inSampleSize = sample
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalArgumentException("Cannot decode image")

        val orientation = try {
            val stream = context.contentResolver.openInputStream(uri)
            stream?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val corrected = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(decoded, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(decoded, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(decoded, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flip(decoded, true, false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flip(decoded, false, true)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                val m = Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
                transform(decoded, m)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val m = Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
                transform(decoded, m)
            }
            else -> decoded
        }
        if (corrected !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }
        return corrected
    }

    fun loadCameraBitmap(file: File, maxDim: Int): Bitmap {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw IllegalArgumentException("Cannot decode image")
        }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options()
        decodeOpts.inSampleSize = sample
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: throw IllegalArgumentException("Cannot decode image")

        val orientation = try {
            val exif = ExifInterface(file.absolutePath)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val corrected = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(decoded, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(decoded, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(decoded, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flip(decoded, true, false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flip(decoded, false, true)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                val m = Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
                transform(decoded, m)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                val m = Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
                transform(decoded, m)
            }
            else -> decoded
        }
        if (corrected !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }
        return corrected
    }

    fun scaleToFit(src: Bitmap, maxDim: Int): Bitmap {
        val max = maxOf(src.width, src.height)
        if (max <= maxDim) return src
        val scale = maxDim.toFloat() / max
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt(),
            (src.height * scale).toInt(),
            true
        )
    }

    private fun rotate(bmp: Bitmap, degrees: Float): Bitmap {
        val m = Matrix()
        m.postRotate(degrees)
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun flip(bmp: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val m = Matrix()
        m.postScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private fun transform(bmp: Bitmap, m: Matrix): Bitmap =
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}
