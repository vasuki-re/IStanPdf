package vasuki.istanpdf.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import vasuki.istanpdf.model.PageItem
import vasuki.istanpdf.util.ContentFiles
import java.io.File

object PdfReorder {
    @Throws(Exception::class)
    fun run(ctx: Context, src: Uri, pages: List<PageItem>, dst: Uri): Int {
        var kept = 0
        val tempIn = ContentFiles.copyUriToCache(ctx, src, ".pdf")
        try {
            val rotations = mutableListOf<Int>()

            PdfStore.openDst(ctx, dst).use { out ->
                val srcDoc = PdfDocument(PdfReader(tempIn))
                try {
                    val dstDoc = PdfDocument(PdfWriter(out))
                    try {
                        for (page in pages) {
                            if (!page.keep) continue
                            val repl = page.replacementFile
                            if (repl != null) {
                                addImagePage(dstDoc, repl)
                            } else {
                                srcDoc.copyPagesTo(listOf(page.originalIndex + 1), dstDoc)
                            }
                            rotations.add(page.rotation)
                            kept++
                        }
                        require(kept > 0) { "Keep at least one page" }

                        for (i in 1..dstDoc.numberOfPages) {
                            val rotation = rotations[i - 1]
                            if (rotation != 0) {
                                val dstPage = dstDoc.getPage(i)
                                val existing = dstPage.rotation
                                var r = (existing + rotation) % 360
                                if (r < 0) r += 360
                                dstPage.put(PdfName.Rotate, PdfNumber(r))
                            }
                        }
                    } finally {
                        dstDoc.close()
                    }
                } finally {
                    srcDoc.close()
                }
            }
        } finally {
            if (tempIn.exists()) tempIn.delete()
        }
        return kept
    }

    @Throws(Exception::class)
    fun replacePages(ctx: Context, src: Uri, pages: List<PageItem>, dst: Uri) {
        val tempIn = ContentFiles.copyUriToCache(ctx, src, ".pdf")
        try {
            PdfStore.openDst(ctx, dst).use { out ->
                val srcDoc = PdfDocument(PdfReader(tempIn))
                try {
                    val dstDoc = PdfDocument(PdfWriter(out))
                    try {
                        val rotations = mutableListOf<Int>()
                        for (page in pages) {
                            if (!page.keep) continue
                            val repl = page.replacementFile
                            if (repl != null) {
                                addImagePage(dstDoc, repl)
                            } else {
                                srcDoc.copyPagesTo(listOf(page.originalIndex + 1), dstDoc)
                            }
                            rotations.add(page.rotation)
                        }
                        for (i in 1..dstDoc.numberOfPages) {
                            val rotation = rotations[i - 1]
                            if (rotation != 0) {
                                val dstPage = dstDoc.getPage(i)
                                val existing = dstPage.rotation
                                var r = (existing + rotation) % 360
                                if (r < 0) r += 360
                                dstPage.put(PdfName.Rotate, PdfNumber(r))
                            }
                        }
                    } finally {
                        dstDoc.close()
                    }
                } finally {
                    srcDoc.close()
                }
            }
        } finally {
            if (tempIn.exists()) tempIn.delete()
        }
    }

    private fun addImagePage(dstDoc: PdfDocument, file: File) {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val imageData = ImageDataFactory.createJpeg(file.readBytes())
        var pw = bounds.outWidth * 72f / 300f
        var ph = bounds.outHeight * 72f / 300f
        if (pw <= 0 || ph <= 0) {
            pw = 595f
            ph = 842f
        }
        val page = dstDoc.addNewPage(PageSize(pw, ph))
        val canvas = PdfCanvas(page)
        canvas.addImageFittedIntoRectangle(imageData, Rectangle(0f, 0f, pw, ph), false)
    }
}
