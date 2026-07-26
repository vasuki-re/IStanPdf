package vasuki.istanpdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import vasuki.istanpdf.util.ContentFiles
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PdfToJpegZip {
    @Throws(Exception::class)
    fun run(ctx: Context, src: Uri, dst: Uri) {
        val base = PdfStore.base(ctx, src)
        var tempPdf: File? = null
        var fd: ParcelFileDescriptor? = null
        var out = ctx.contentResolver.openOutputStream(dst)
        try {
            if (src.scheme == "file") {
                fd = ParcelFileDescriptor.open(File(src.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                tempPdf = ContentFiles.copyUriToCache(ctx, src, ".pdf")
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            requireNotNull(fd) { "Cannot open PDF file" }
            requireNotNull(out) { "Cannot open output file" }

            val rnd = PdfRenderer(fd)
            fd = null
            rnd.use { renderer ->
                ZipOutputStream(out).use { zip ->
                    out = null
                    val total = renderer.pageCount
                    for (i in 0 until total) {
                        renderer.openPage(i).use { page ->
                            val w = page.width * 2
                            val h = page.height * 2
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val name = "%s-%03d.jpg".format(base, i + 1)
                                zip.putNextEntry(ZipEntry(name))
                                val success = bmp.compress(Bitmap.CompressFormat.JPEG, 90, zip)
                                zip.closeEntry()
                                check(success) { "Failed to compress page ${i + 1} to JPEG" }
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            try { fd?.close() } catch (_: Exception) {}
            try { out?.close() } catch (_: Exception) {}
            if (tempPdf?.exists() == true) tempPdf.delete()
        }
    }
}
