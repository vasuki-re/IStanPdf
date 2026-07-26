package vasuki.istanpdf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import vasuki.istanpdf.model.PageItem
import vasuki.istanpdf.pdf.ImagesToPdf
import vasuki.istanpdf.pdf.PdfMerge
import vasuki.istanpdf.pdf.PdfReorder
import vasuki.istanpdf.pdf.PdfToJpegZip
import vasuki.istanpdf.util.ContentFiles
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class PdfEngine(context: Context) {
    private val context = context.applicationContext

    @Throws(Exception::class)
    fun renderFirstPage(uri: Uri, targetWidth: Int): Bitmap? {
        var tempPdf: File? = null
        var fd: ParcelFileDescriptor? = null
        try {
            if (uri.scheme == "file") {
                fd = ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                tempPdf = ContentFiles.copyUriToCache(context, uri, ".pdf")
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            requireNotNull(fd) { "Cannot open PDF file" }
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val width = targetWidth
                        val height = max(1, width * page.height / max(1, page.width))
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    }
                }
            }
            return null
        } finally {
            try { fd?.close() } catch (_: Exception) {}
            if (tempPdf?.exists() == true) tempPdf.delete()
        }
    }

    fun interface RenderProgressListener {
        fun onPageRendered(current: Int, total: Int)
    }

    @Throws(Exception::class)
    fun renderAllPages(
        uri: Uri,
        targetWidth: Int,
        cancelFlag: AtomicBoolean?,
        listener: RenderProgressListener?
    ): List<PageItem> {
        val rendered = mutableListOf<PageItem>()
        var tempPdf: File? = null
        var fd: ParcelFileDescriptor? = null
        try {
            if (uri.scheme == "file") {
                fd = ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                tempPdf = ContentFiles.copyUriToCache(context, uri, ".pdf")
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            requireNotNull(fd) { "Cannot open PDF file" }
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    if ((cancelFlag != null && cancelFlag.get()) || Thread.currentThread().isInterrupted) {
                        throw InterruptedException("Cancelled by user")
                    }
                    listener?.onPageRendered(i + 1, renderer.pageCount)
                    renderer.openPage(i).use { page ->
                        val width = targetWidth
                        val height = max(1, width * page.height / max(1, page.width))
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        rendered.add(PageItem(i, bitmap))
                    }
                }
            }
        } catch (e: Exception) {
            for (p in rendered) {
                if (!p.thumbnail.isRecycled) p.thumbnail.recycle()
            }
            rendered.clear()
            throw e
        } finally {
            try { fd?.close() } catch (_: Exception) {}
            if (tempPdf?.exists() == true) tempPdf.delete()
        }
        return rendered
    }

    @Throws(Exception::class)
    fun merge(sources: List<Uri>, rotations: List<Int>, destination: Uri) {
        PdfMerge.run(context, sources, rotations, destination)
    }

    @Throws(Exception::class)
    fun reorder(source: Uri, pages: List<PageItem>, destination: Uri): Int {
        return PdfReorder.run(context, source, pages, destination)
    }

    @Throws(Exception::class)
    fun imagesToPdf(sources: List<Uri>, pages: List<PageItem>, destination: Uri) {
        ImagesToPdf.run(context, sources, pages, destination)
    }

    @Throws(Exception::class)
    fun pdfToJpegZip(source: Uri, destination: Uri) {
        PdfToJpegZip.run(context, source, destination)
    }
}
