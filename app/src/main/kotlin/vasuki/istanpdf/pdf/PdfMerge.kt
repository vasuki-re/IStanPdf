package vasuki.istanpdf.pdf

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfNumber
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.utils.PdfMerger
import vasuki.istanpdf.util.ContentFiles
import java.io.File
import java.io.FileOutputStream

object PdfMerge {
    @Throws(Exception::class)
    fun run(ctx: Context, src: List<Uri>, rotations: List<Int>?, dst: Uri) {
        val tempOut = File.createTempFile("merged_", ".pdf", ctx.cacheDir)
        val tempIns = mutableListOf<File>()
        try {
            val merged = PdfDocument(PdfWriter(FileOutputStream(tempOut)))
            val merger = PdfMerger(merged)
            val rotationRanges = mutableListOf<IntArray>()
            var pageOffset = 0
            try {
                for (i in src.indices) {
                    val uri = src[i]
                    val rotation = rotations?.getOrNull(i) ?: 0
                    val tempIn = ContentFiles.copyUriToCache(ctx, uri, ".pdf")
                    tempIns.add(tempIn)

                    val srcDoc = PdfDocument(PdfReader(tempIn))
                    try {
                        val srcPages = srcDoc.numberOfPages
                        if (rotation != 0) {
                            rotationRanges.add(intArrayOf(pageOffset + 1, pageOffset + srcPages, rotation))
                        }
                        merger.merge(srcDoc, 1, srcPages)
                        pageOffset += srcPages
                    } finally {
                        srcDoc.close()
                    }
                }

                for (range in rotationRanges) {
                    for (p in range[0]..range[1]) {
                        val page = merged.getPage(p)
                        val existing = page.rotation
                        page.put(PdfName.Rotate, PdfNumber(existing + range[2]))
                    }
                }
            } finally {
                merged.close()
            }
            ContentFiles.copyFileToUri(ctx, tempOut, dst)
        } finally {
            if (tempOut.exists()) tempOut.delete()
            for (tempIn in tempIns) {
                if (tempIn.exists()) tempIn.delete()
            }
        }
    }
}
