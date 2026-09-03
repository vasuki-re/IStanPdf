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
import java.util.concurrent.atomic.AtomicInteger

object PdfCompress {

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
    fun runByResolution(ctx: Context, source: Uri, destination: Uri, dpi: Int, jpegQuality: Int): Int {
        val tempIn = ContentFiles.copyUriToCache(ctx, source, ".pdf")
        val tempOut = File.createTempFile("compressed_", ".pdf", ctx.cacheDir)
        val skipCount = AtomicInteger()
        try {
            compressToFile(tempIn, tempOut, dpi, jpegQuality, forceReencode = false, skipCount)
            ContentFiles.copyFileToUri(ctx, tempOut, destination)
        } finally {
            if (tempIn.exists()) tempIn.delete()
            if (tempOut.exists()) tempOut.delete()
        }
        return skipCount.get()
    }

    @Throws(Exception::class)
    fun runBySize(ctx: Context, source: Uri, destination: Uri, targetBytes: Long): Pair<Long, Int> {
        val tempIn = ContentFiles.copyUriToCache(ctx, source, ".pdf")
        var bestFile: File? = null
        var finalSkips = 0
        try {
            if (tempIn.length() <= targetBytes) {
                ContentFiles.copyFileToUri(ctx, tempIn, destination)
                return Pair(tempIn.length(), 0)
            }

            if (!hasEmbeddedImages(tempIn)) {
                throw IllegalStateException("This PDF has no embedded images and cannot be compressed further.")
            }

            val margin = (targetBytes / 50).coerceIn(1024, 10240)
            val searchTarget = targetBytes - margin

            fun search(loParam: Int, hiParam: Int, toDpi: (Int) -> Int, toQuality: (Int) -> Int): Pair<File, Int>? {
                var lo = loParam
                var hi = hiParam
                var best: File? = null
                var bestDiff = Long.MAX_VALUE
                var bestSkips = 0
                for (iteration in 0 until 8) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val mid = (lo + hi) / 2
                    val attempt = File.createTempFile("compress_attempt_", ".pdf", ctx.cacheDir)
                    val iterSkips = AtomicInteger()
                    try {
                        compressToFile(tempIn, attempt, toDpi(mid), toQuality(mid), forceReencode = true, iterSkips)
                        val size = attempt.length()
                        val absDiff = Math.abs(size - searchTarget)
                        val better = absDiff < bestDiff || (absDiff == bestDiff && size <= searchTarget)
                        if (better) {
                            bestDiff = absDiff
                            best?.delete()
                            best = attempt
                            bestSkips = iterSkips.get()
                        } else {
                            attempt.delete()
                        }
                        if (size > searchTarget) hi = mid - 1 else lo = mid + 1
                        if (lo > hi) break
                    } catch (e: Exception) {
                        attempt.delete()
                        throw e
                    }
                }
                return if (best != null) Pair(best, bestSkips) else null
            }

            val result1 = search(36, 300, { it }, { 70 })
            bestFile = result1?.first
            finalSkips = result1?.second ?: 0

            if (bestFile != null && bestFile!!.length() > searchTarget) {
                val pass2 = search(20, 65, { 36 }, { it })
                if (pass2 != null) {
                    val p1Diff = Math.abs(bestFile!!.length() - searchTarget)
                    val p2Diff = Math.abs(pass2.first.length() - searchTarget)
                    if (p2Diff < p1Diff || (p2Diff == p1Diff && pass2.first.length() <= searchTarget)) {
                        bestFile!!.delete()
                        bestFile = pass2.first
                        finalSkips = pass2.second
                    } else {
                        pass2.first.delete()
                    }
                }
            }

            val result = bestFile ?: throw IllegalStateException("Compression failed")
            ContentFiles.copyFileToUri(ctx, result, destination)
            return Pair(result.length(), finalSkips)
        } finally {
            if (tempIn.exists()) tempIn.delete()
            bestFile?.delete()
        }
    }

    private fun compressToFile(input: File, output: File, dpi: Int, jpegQuality: Int, forceReencode: Boolean, skipCount: AtomicInteger) {
        val doc = PdfDocument(PdfReader(input), PdfWriter(FileOutputStream(output)))
        try {
            for (i in 1..doc.numberOfPages) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val page = doc.getPage(i)
                forEachImageXObject(page) { stream ->
                    compressImageStream(page, stream, dpi, jpegQuality, forceReencode, skipCount)
                }
            }
        } finally {
            doc.close()
        }
    }

    private fun compressImageStream(page: PdfPage, stream: PdfStream, dpi: Int, jpegQuality: Int, forceReencode: Boolean, skipCount: AtomicInteger) {
        try {
            val imageXObject = PdfImageXObject(stream)
            val origWidth = imageXObject.width.toInt()
            val origHeight = imageXObject.height.toInt()
            if (origWidth <= 0 || origHeight <= 0) return

            val pageWidth = page.pageSize.width
            val targetWidth = ((pageWidth / 72f) * dpi).toInt()
            val downscale = targetWidth < origWidth
            if (!downscale && !forceReencode) return

            val scale = if (downscale) targetWidth.toFloat() / origWidth else 1f
            val finalWidth = if (downscale) targetWidth else origWidth
            val finalHeight = if (downscale) (origHeight * scale).toInt() else origHeight
            if (finalWidth <= 0 || finalHeight <= 0) return

            val imageBytes = imageXObject.imageBytes ?: return
            val hasSoftMask = stream.getAsStream(PdfName.SMask) != null
            val origColorSpace = stream.getAsName(PdfName.ColorSpace)
            if (PdfName.DeviceCMYK == origColorSpace) return

            var decoded: Bitmap? = null
            var scaled: Bitmap? = null
            var flattened: Bitmap? = null
            try {
                val options = BitmapFactory.Options()
                if (downscale) options.inSampleSize = sampleSizeFor(origWidth, finalWidth)
                decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return

                scaled = if (downscale) {
                    Bitmap.createScaledBitmap(decoded, finalWidth, finalHeight, true)
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

                val oldLength = stream.length.toLong()
                if (newBytes.size.toLong() * 100 >= oldLength * 95) return

                stream.setData(newBytes)
                stream.put(PdfName.Filter, PdfName.DCTDecode)
                stream.put(PdfName.Width, PdfNumber(flattened.width))
                stream.put(PdfName.Height, PdfNumber(flattened.height))
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
            skipCount.incrementAndGet()
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
