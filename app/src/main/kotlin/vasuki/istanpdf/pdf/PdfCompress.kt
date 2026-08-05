package vasuki.istanpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import vasuki.istanpdf.util.ContentFiles
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfCompress {

    fun hasEmbeddedImages(ctx: Context, source: Uri): Boolean {
        val temp = ContentFiles.copyUriToCache(ctx, source, ".pdf")
        try {
            val doc = PdfDocument(PdfReader(temp))
            try {
                for (i in 1..doc.numberOfPages) {
                    val page = doc.getPage(i)
                    val xObjects = page.resources?.getResource(PdfName.XObject) ?: continue
                    for (name in xObjects.keySet()) {
                        val obj = xObjects.getAsStream(name) ?: continue
                        val subtype = obj.getAsName(PdfName.Subtype)
                        if (PdfName.Image == subtype) return true
                    }
                }
            } finally {
                doc.close()
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
        return false
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
        var bestFile: File? = null
        try {
            var lo = 36
            var hi = 300
            var bestDiff = Long.MAX_VALUE

            for (iteration in 0 until 8) {
                val mid = (lo + hi) / 2
                val attempt = File.createTempFile("compress_attempt_", ".pdf", ctx.cacheDir)
                try {
                    compressToFile(tempIn, attempt, mid, 70)
                    val size = attempt.length()
                    val diff = size - targetBytes

                    if (diff <= 0 && -diff < bestDiff) {
                        bestDiff = -diff
                        bestFile?.delete()
                        bestFile = attempt
                    } else if (diff > 0 && (bestFile == null || diff < bestDiff)) {
                        bestDiff = diff
                        bestFile?.delete()
                        bestFile = attempt
                    } else {
                        attempt.delete()
                    }

                    if (size > targetBytes) {
                        hi = mid - 1
                    } else {
                        lo = mid + 1
                    }

                    if (lo > hi) break
                } catch (e: Exception) {
                    attempt.delete()
                    throw e
                }
            }

            val result = bestFile ?: throw IllegalStateException("Compression failed")
            ContentFiles.copyFileToUri(ctx, result, destination)
            return result.length()
        } finally {
            if (tempIn.exists()) tempIn.delete()
            bestFile?.delete()
        }
    }

    private fun compressToFile(input: File, output: File, dpi: Int, jpegQuality: Int) {
        val doc = PdfDocument(PdfReader(input), PdfWriter(FileOutputStream(output)))
        try {
            for (i in 1..doc.numberOfPages) {
                val page = doc.getPage(i)
                val xObjects = page.resources?.getResource(PdfName.XObject) ?: continue
                for (name in xObjects.keySet()) {
                    val stream = xObjects.getAsStream(name) ?: continue
                    val subtype = stream.getAsName(PdfName.Subtype)
                    if (PdfName.Image != subtype) continue

                    try {
                        val imageXObject = PdfImageXObject(stream)
                        val origWidth = imageXObject.width.toInt()
                        val origHeight = imageXObject.height.toInt()

                        val pageWidth = page.pageSize.width
                        val targetWidth = ((pageWidth / 72f) * dpi).toInt()

                        if (targetWidth >= origWidth) continue

                        val scale = targetWidth.toFloat() / origWidth
                        val targetHeight = (origHeight * scale).toInt()
                        if (targetWidth <= 0 || targetHeight <= 0) continue

                        val imageBytes = imageXObject.imageBytes ?: continue
                        val origBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            ?: continue

                        val scaled = Bitmap.createScaledBitmap(origBitmap, targetWidth, targetHeight, true)
                        if (scaled !== origBitmap) origBitmap.recycle()

                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                        scaled.recycle()

                        val newBytes = baos.toByteArray()
                        stream.setData(newBytes)
                        stream.put(PdfName.Filter, PdfName.DCTDecode)
                        stream.put(PdfName.Width, com.itextpdf.kernel.pdf.PdfNumber(targetWidth))
                        stream.put(PdfName.Height, com.itextpdf.kernel.pdf.PdfNumber(targetHeight))
                        stream.put(PdfName.BitsPerComponent, com.itextpdf.kernel.pdf.PdfNumber(8))
                        stream.put(PdfName.ColorSpace, PdfName.DeviceRGB)
                        stream.remove(PdfName.DecodeParms)
                        stream.remove(PdfName.SMask)
                    } catch (_: Exception) {
                        // skip images that can't be decoded
                    }
                }
            }
        } finally {
            doc.close()
        }
    }
}
