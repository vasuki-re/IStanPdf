package vasuki.istanpdf.docx

import android.content.Context
import android.net.Uri
import org.libreoffice.kit.Document
import vasuki.istanpdf.libreoffice.LibreOfficeDocumentEngine
import vasuki.istanpdf.libreoffice.UnoCommandHelper
import vasuki.istanpdf.util.ContentFiles
import java.io.File

object DocxRemove {
    data class PageSelection(val originalIndex: Int, val keep: Boolean)

    @Throws(Exception::class)
    fun run(ctx: Context, src: Uri, pages: List<PageSelection>, dst: Uri) {
        check(pages.any { it.keep }) { "Keep at least one page" }

        val office = LibreOfficeDocumentEngine.getOffice(ctx)
        var input: File? = null
        var output: File? = null
        var doc: Document? = null

        try {
            input = ContentFiles.copyUriToCache(ctx, src, ".docx")
            output = File.createTempFile("removed_uno_", ".docx", ctx.cacheDir)

            doc = office.documentLoad(Uri.fromFile(input).toString())
                ?: throw IllegalStateException("LO couldn't load DOCX")

            doc.initializeForRendering()

            var total = doc.getParts()
            if (total <= 0) total = pages.size

            remove(doc, pages, total)

            UnoCommandHelper.postAndWait(doc, ".uno:Repaginate")
            doc.saveAs(Uri.fromFile(output).toString(), "docx", null)

            check(output.exists() && output.length() > 0) {
                "LibreOffice failed to generate an output file"
            }

            ContentFiles.copyFileToUri(ctx, output, dst)
        } finally {
            doc?.destroy()
            if (input?.exists() == true) input.delete()
            if (output?.exists() == true) output.delete()
        }
    }

    private fun remove(doc: Document, pages: List<PageSelection>, totalPages: Int) {
        doc.setPart(0)
        UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfDoc")

        val order = pages.toMutableList()
        order.sortBy { it.originalIndex }

        var last = totalPages - 1
        for (i in order.indices.reversed()) {
            val page = order[i]
            if (page.keep) continue
            if (Thread.interrupted()) throw InterruptedException("Operation cancelled")

            doc.setPart(page.originalIndex)
            if (page.originalIndex == last) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel")
                UnoCommandHelper.postAndWait(doc, ".uno:Delete", "", UnoCommandHelper.HEAVY_TIMEOUT_MS)
                if (page.originalIndex > 0) {
                    UnoCommandHelper.postAndWait(doc, ".uno:SwBackspace")
                }
                last--
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel")
                UnoCommandHelper.postAndWait(doc, ".uno:Delete", "", UnoCommandHelper.HEAVY_TIMEOUT_MS)
            }
        }
    }
}
