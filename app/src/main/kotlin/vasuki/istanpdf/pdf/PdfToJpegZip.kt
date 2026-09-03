package vasuki.istanpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import vasuki.istanpdf.util.ContentFiles
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

object PdfToJpegZip {
    @Throws(Exception::class)
    fun run(ctx: Context, src: Uri, dst: Uri, dpi: Int) {
        require(dpi > 0) { "Export DPI must be greater than zero" }
        val base = PdfStore.base(ctx, src)
        ctx.contentResolver.openOutputStream(dst).use { output ->
            requireNotNull(output) { "Cannot open output file" }
            ZipOutputStream(output).use { zip ->
                withRenderer(ctx, src) { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            zip.putNextEntry(ZipEntry("%s-%03d.jpg".format(base, i + 1)))
                            try {
                                writeJpeg(page, i + 1, dpi, zip)
                            } finally {
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    fun runSingle(ctx: Context, src: Uri, dst: Uri, dpi: Int) {
        require(dpi > 0) { "Export DPI must be greater than zero" }
        ctx.contentResolver.openOutputStream(dst).use { output ->
            requireNotNull(output) { "Cannot open output file" }
            withRenderer(ctx, src) { renderer ->
                require(renderer.pageCount == 1) { "PDF no longer has exactly one page" }
                renderer.openPage(0).use { page ->
                    writeJpeg(page, 1, dpi, output)
                }
            }
        }
    }

    private fun withRenderer(ctx: Context, src: Uri, block: (PdfRenderer) -> Unit) {
        var tempPdf: File? = null
        var fd: ParcelFileDescriptor? = null
        try {
            if (src.scheme == "file") {
                fd = ParcelFileDescriptor.open(File(src.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                tempPdf = ContentFiles.copyUriToCache(ctx, src, ".pdf")
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            requireNotNull(fd) { "Cannot open PDF file" }
            val renderer = PdfRenderer(fd)
            fd = null
            renderer.use(block)
        } finally {
            try { fd?.close() } catch (_: Exception) {}
            if (tempPdf?.exists() == true) tempPdf.delete()
        }
    }

    private fun writeJpeg(page: PdfRenderer.Page, pageNumber: Int, dpi: Int, output: OutputStream) {
        val width = ceil(page.width.toDouble() * dpi / PDF_POINTS_PER_INCH).toInt()
        val height = ceil(page.height.toDouble() * dpi / PDF_POINTS_PER_INCH).toInt()
        require(width > 0 && height > 0) { "Page $pageNumber has invalid dimensions" }
        require(width.toLong() * height <= MAX_RENDER_PIXELS) {
            "Page $pageNumber is too large to export at ${dpi} DPI"
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Failed to compress page $pageNumber to JPEG"
            }
        } finally {
            bitmap.recycle()
        }
    }

    private const val PDF_POINTS_PER_INCH = 72.0
    private const val JPEG_QUALITY = 95
    private const val MAX_RENDER_PIXELS = 36_000_000L
}
