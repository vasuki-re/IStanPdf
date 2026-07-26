package vasuki.istanpdf.pdf

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import vasuki.istanpdf.model.PageItem
import vasuki.istanpdf.util.ContentFiles

object PdfReorder {
    @Throws(Exception::class)
    fun run(ctx: Context, src: Uri, pages: List<PageItem>, dst: Uri): Int {
        var kept = 0
        val tempIn = ContentFiles.copyUriToCache(ctx, src, ".pdf")
        try {
            val pageNumbers = mutableListOf<Int>()
            val rotations = mutableListOf<Int>()

            for (page in pages) {
                if (page.keep) {
                    pageNumbers.add(page.originalIndex + 1)
                    rotations.add(page.rotation)
                    kept++
                }
            }
            require(kept > 0) { "Keep at least one page" }

            PdfStore.openDst(ctx, dst).use { out ->
                val srcDoc = PdfDocument(PdfReader(tempIn))
                val dstDoc = PdfDocument(PdfWriter(out))
                try {
                    srcDoc.copyPagesTo(pageNumbers, dstDoc)

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
                    srcDoc.close()
                }
            }
        } finally {
            if (tempIn.exists()) tempIn.delete()
        }
        return kept
    }
}
