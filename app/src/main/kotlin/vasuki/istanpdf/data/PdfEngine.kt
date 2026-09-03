package vasuki.istanpdf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import vasuki.istanpdf.pdf.ImagesToPdf
import vasuki.istanpdf.pdf.PdfCompress
import vasuki.istanpdf.pdf.PdfMerge
import vasuki.istanpdf.pdf.PdfReorder
import vasuki.istanpdf.pdf.PdfToJpegZip
import vasuki.istanpdf.util.ContentFiles
import java.io.Closeable
import java.io.File
import kotlin.math.max

class PdfEngine(context: Context) {
    private val context = context.applicationContext

    inner class RenderSession internal constructor(uri: Uri) : Closeable {
        private val tempFile: File? =
            if (uri.scheme == "file") null else ContentFiles.copyUriToCache(context, uri, ".pdf")
        private val fd: ParcelFileDescriptor = ParcelFileDescriptor.open(
            tempFile ?: File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        private val renderer = PdfRenderer(fd)

        @get:Synchronized
        val pageCount: Int
            get() = renderer.pageCount

        @Synchronized
        fun renderPage(index: Int, targetWidth: Int): Bitmap {
            require(index in 0 until renderer.pageCount) { "Page index out of range" }
            renderer.openPage(index).use { page ->
                val width = targetWidth
                val height = max(1, width * page.height / max(1, page.width))
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }

        override fun close() {
            renderer.close()
            fd.close()
            tempFile?.let { if (it.exists()) it.delete() }
        }
    }

    fun openSession(uri: Uri): RenderSession = RenderSession(uri)

    fun interface RenderProgressListener {
        fun onPageRendered(current: Int, total: Int)
    }

    @Throws(Exception::class)
    fun merge(sources: List<Uri>, rotations: List<Int>, destination: Uri) {
        PdfMerge.run(context, sources, rotations, destination)
    }

    @Throws(Exception::class)
    fun reorder(source: Uri, pages: List<vasuki.istanpdf.model.PageItem>, destination: Uri): Int {
        return PdfReorder.run(context, source, pages, destination)
    }

    @Throws(Exception::class)
    fun replacePages(source: Uri, pages: List<vasuki.istanpdf.model.PageItem>, destination: Uri) {
        PdfReorder.replacePages(context, source, pages, destination)
    }

    @Throws(Exception::class)
    fun imagesToPdf(sources: List<Uri>, pages: List<vasuki.istanpdf.model.PageItem>, destination: Uri) {
        ImagesToPdf.run(context, sources, pages, destination)
    }

    @Throws(Exception::class)
    fun pdfToJpegZip(source: Uri, destination: Uri, dpi: Int) {
        PdfToJpegZip.run(context, source, destination, dpi)
    }

    @Throws(Exception::class)
    fun pdfToJpeg(source: Uri, destination: Uri, dpi: Int) {
        PdfToJpegZip.runSingle(context, source, destination, dpi)
    }

    fun hasEmbeddedImages(source: Uri): Boolean {
        return PdfCompress.hasEmbeddedImages(context, source)
    }

    @Throws(Exception::class)
    fun compressByResolution(source: Uri, destination: Uri, dpi: Int, quality: Int): Int {
        return PdfCompress.runByResolution(context, source, destination, dpi, quality)
    }

    @Throws(Exception::class)
    fun compressBySize(source: Uri, destination: Uri, targetBytes: Long): Pair<Long, Int> {
        return PdfCompress.runBySize(context, source, destination, targetBytes)
    }
}
