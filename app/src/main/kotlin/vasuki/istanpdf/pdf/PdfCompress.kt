package vasuki.istanpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import vasuki.istanpdf.util.ContentFiles
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PdfCompress {

    private const val MAX_DPI = 300
    private const val MIN_DPI = 36
    private const val MIN_QUALITY = 20
    private const val MAX_QUALITY = 90
    private const val MAX_SAMPLE = 64

    fun hasEmbeddedImages(ctx: Context, source: Uri): Boolean {
        val temp = ContentFiles.copyUriToCache(ctx, source, ".pdf")
        try {
            return hasEmbeddedImages(temp)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun hasEmbeddedImages(file: File): Boolean {
        val doc = PdfDocument(PdfReader(file))
        try {
            for (i in 1..doc.numberOfPages) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                var found = false
                forEachImageXObject(doc.getPage(i)) { found = true }
                if (found) return true
            }
            return false
        } finally {
            doc.close()
        }
    }

    @Throws(Exception::class)
    fun runByResolution(ctx: Context, source: Uri, destination: Uri, dpi: Int, jpegQuality: Int) {
        val tempIn = ContentFiles.copyUriToCache(ctx, source, ".pdf")
        val tempOut = File.createTempFile("compressed_", ".pdf", ctx.cacheDir)
        try {
            compressToFile(tempIn, tempOut, dpi, jpegQuality)
            ContentFiles.copyFileToUri(ctx, tempOut, destination)
        } finally {
            if (tempIn.exists()) tempIn.delete()
            if (tempOut.exists()) tempOut.delete()
        }
    }

    @Throws(Exception::class)
    fun runBySize(ctx: Context, source: Uri, destination: Uri, targetBytes: Long): Long {
        val tempIn = ContentFiles.copyUriToCache(ctx, source, ".pdf")
        var result: File? = null
        try {
            if (tempIn.length() <= targetBytes) {
                ContentFiles.copyFileToUri(ctx, tempIn, destination)
                return tempIn.length()
            }

            if (!hasEmbeddedImages(tempIn)) {
                throw IllegalStateException("This PDF has no embedded images and cannot be compressed further.")
            }

            result = compressToTarget(ctx, tempIn, targetBytes, harshest = false)
            if (result.length() > targetBytes) {
                val corrective = compressToTarget(ctx, result, targetBytes, harshest = true)
                if (corrective.length() < result.length()) {
                    result.delete()
                    result = corrective
                } else {
                    corrective.delete()
                }
            }

            ContentFiles.copyFileToUri(ctx, result, destination)
            return result.length()
        } finally {
            if (tempIn.exists()) tempIn.delete()
            result?.delete()
        }
    }

    private fun compressToTarget(ctx: Context, input: File, targetBytes: Long, harshest: Boolean): File {
        val imageBytes = measureImageBytes(input)
        val nonImageBytes = (input.length() - imageBytes).coerceAtLeast(0)
        val targetImageBytes = targetBytes - nonImageBytes
        val ratio = if (targetImageBytes <= 0) 0.0 else (targetImageBytes.toDouble() / imageBytes).coerceIn(0.0, 1.0)

        val quality: Int
        val dpi: Int
        if (harshest || ratio <= 0.05) {
            quality = MIN_QUALITY
            dpi = MIN_DPI
        } else {
            quality = (ratio * 80 + 15).roundToInt().coerceIn(MIN_QUALITY, MAX_QUALITY)
            dpi = if (ratio >= 0.6) MAX_DPI else (MAX_DPI * sqrt(ratio / 0.6)).roundToInt().coerceIn(MIN_DPI, MAX_DPI)
        }

        val out = File.createTempFile("compressed_", ".pdf", ctx.cacheDir)
        try {
            compressToFile(input, out, dpi, quality)
        } catch (e: Exception) {
            out.delete()
            throw e
        }
        return out
    }

    private fun measureImageBytes(file: File): Long {
        val doc = PdfDocument(PdfReader(file))
        try {
            var total = 0L
            for (i in 1..doc.numberOfPages) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                forEachImageXObject(doc.getPage(i)) { stream ->
                    val rawLength = stream.length
                    val length = if (rawLength != null) rawLength.toLong() else 0L
                    if (length > 0) total += length
                }
            }
            return total
        } finally {
            doc.close()
        }
    }

    private fun compressToFile(input: File, output: File, dpi: Int, jpegQuality: Int) {
        val doc = PdfDocument(PdfReader(input), PdfWriter(FileOutputStream(output)))
        try {
            for (i in 1..doc.numberOfPages) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val page = doc.getPage(i)
                forEachImageXObject(page) { stream -> compressImageStream(page, stream, dpi, jpegQuality) }
            }
        } finally {
            doc.close()
        }
    }

    private fun compressImageStream(page: PdfPage, stream: PdfStream, dpi: Int, jpegQuality: Int) {
        try {
            val imageXObject = PdfImageXObject(stream)
            val origWidth = imageXObject.width.toInt()
            val origHeight = imageXObject.height.toInt()
            if (origWidth <= 0 || origHeight <= 0) return

            val pageWidth = page.pageSize.width
            val targetWidth = ((pageWidth / 72f) * dpi).toInt()
            val downscale = targetWidth < origWidth
            val scale = if (downscale) targetWidth.toFloat() / origWidth else 1f
            val targetHeight = (origHeight * scale).toInt()
            if (targetWidth <= 0 || targetHeight <= 0) return

            val imageBytes = imageXObject.imageBytes ?: return
            val hasSoftMask = stream.getAsStream(PdfName.SMask) != null
            val origColorSpace = stream.getAsName(PdfName.ColorSpace)
            if (PdfName.DeviceGray == origColorSpace || PdfName.DeviceCMYK == origColorSpace) return

            var decoded: Bitmap? = null
            var scaled: Bitmap? = null
            var flattened: Bitmap? = null
            try {
                val options = BitmapFactory.Options()
                if (downscale) options.inSampleSize = sampleSizeFor(origWidth, targetWidth)
                decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return

                scaled = if (downscale) {
                    Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
                } else {
                    decoded
                }

                flattened = if (scaled.hasAlpha() || hasSoftMask) {
                    flattenOnWhite(scaled)
                } else {
                    scaled
                }

                val baos = ByteArrayOutputStream()
                flattened.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                val newBytes = baos.toByteArray()

                val rawLength = stream.length
                val oldLength = if (rawLength != null) rawLength.toLong() else imageBytes.size.toLong()
                if (newBytes.size.toLong() * 100 >= oldLength * 95) return

                stream.setData(newBytes)
                stream.put(PdfName.Filter, PdfName.DCTDecode)
                stream.put(PdfName.Width, PdfNumber(scaled.width))
                stream.put(PdfName.Height, PdfNumber(scaled.height))
                stream.put(PdfName.BitsPerComponent, PdfNumber(8))
                stream.put(PdfName.ColorSpace, PdfName.DeviceRGB)
                stream.remove(PdfName.DecodeParms)
                stream.remove(PdfName.SMask)
            } finally {
                if (flattened != null && flattened !== scaled) flattened.recycle()
                if (scaled != null && scaled !== decoded) scaled.recycle()
                decoded?.recycle()
            }
        } catch (_: Exception) {
        }
    }

    private fun sampleSizeFor(origWidth: Int, targetWidth: Int): Int {
        var sample = 1
        while (origWidth / (sample * 2) >= targetWidth && sample < MAX_SAMPLE) {
            sample *= 2
        }
        return sample
    }

    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, null)
        return out
    }

    private fun forEachImageXObject(page: PdfPage, onImage: (PdfStream) -> Unit) {
        val xObjects = page.resources?.getResource(PdfName.XObject) ?: return
        walkXObjects(xObjects, mutableSetOf(), onImage)
    }

    private fun walkXObjects(xObjects: PdfDictionary, visited: MutableSet<PdfStream>, onImage: (PdfStream) -> Unit) {
        for (name in xObjects.keySet()) {
            val stream = xObjects.getAsStream(name) ?: continue
            if (!visited.add(stream)) continue
            val subtype = stream.getAsName(PdfName.Subtype)
            if (PdfName.Image == subtype) {
                onImage(stream)
            } else if (PdfName.Form == subtype) {
                val formXObjects = PdfFormXObject(stream).resources?.getResource(PdfName.XObject) ?: continue
                walkXObjects(formXObjects, visited, onImage)
            }
        }
    }
}