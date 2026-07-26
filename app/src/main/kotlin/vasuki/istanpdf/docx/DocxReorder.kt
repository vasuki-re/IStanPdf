package vasuki.istanpdf.docx

import android.content.Context
import android.net.Uri
import org.libreoffice.kit.Document
import vasuki.istanpdf.libreoffice.LibreOfficeDocumentEngine
import vasuki.istanpdf.libreoffice.UnoCommandHelper
import vasuki.istanpdf.util.ContentFiles
import java.io.File

object DocxReorder {
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
            output = File.createTempFile("reordered_uno_", ".docx", ctx.cacheDir)

            doc = office.documentLoad(Uri.fromFile(input).toString())
                ?: throw IllegalStateException("LO couldn't load DOCX")

            doc.initializeForRendering()

            var total = doc.parts
            if (total <= 0) total = pages.size

            val toDelete = mutableListOf<Int>()
            for (p in pages) {
                if (!p.keep) toDelete.add(p.originalIndex)
            }
            toDelete.sort()

            deletePages(doc, toDelete, total)

            val keptTotal = total - toDelete.size
            copyKeptToEnd(doc, pages, toDelete, keptTotal)

            deleteOriginals(doc, keptTotal)

            UnoCommandHelper.postFireAndForget(doc, ".uno:GoToStartOfDoc")
            UnoCommandHelper.postAndWait(doc, ".uno:Delete")

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

    private fun deletePages(doc: Document, indices: List<Int>, currentTotal: Int) {
        var total = currentTotal
        for (i in indices.indices.reversed()) {
            if (Thread.interrupted()) throw InterruptedException("Operation cancelled")
            val pageIdx = indices[i]
            doc.setPart(pageIdx)
            if (pageIdx == total - 1) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel")
                UnoCommandHelper.postAndWait(doc, ".uno:Delete")
                if (pageIdx > 0) {
                    UnoCommandHelper.postAndWait(doc, ".uno:SwBackspace")
                }
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel")
                UnoCommandHelper.postAndWait(doc, ".uno:Delete")
            }
            total--
        }
    }

    private fun copyKeptToEnd(
        doc: Document,
        pages: List<PageSelection>,
        deletedIndices: List<Int>,
        keptTotal: Int
    ) {
        for (p in pages) {
            if (!p.keep) continue
            if (Thread.interrupted()) throw InterruptedException("Operation cancelled")
            var currentPos = p.originalIndex
            for (deleted in deletedIndices) {
                if (deleted < p.originalIndex) currentPos--
            }

            doc.setPart(currentPos)
            if (currentPos == keptTotal - 1) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel")
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel")
            }
            UnoCommandHelper.postAndWait(doc, ".uno:Copy")
            UnoCommandHelper.postFireAndForget(doc, ".uno:GoToEndOfDoc")
            UnoCommandHelper.postAndWait(doc, ".uno:InsertPagebreak")
            UnoCommandHelper.postAndWait(doc, ".uno:Paste")
        }
    }

    private fun deleteOriginals(doc: Document, count: Int) {
        for (k in count - 1 downTo 0) {
            if (Thread.interrupted()) throw InterruptedException("Operation cancelled")
            doc.setPart(k)
            if (k == count - 1) {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToEndOfPageSel")
                UnoCommandHelper.postAndWait(doc, ".uno:Delete")
                if (k > 0) {
                    UnoCommandHelper.postAndWait(doc, ".uno:SwBackspace")
                }
            } else {
                UnoCommandHelper.postAndWait(doc, ".uno:GoToStartOfNextPageSel")
                UnoCommandHelper.postAndWait(doc, ".uno:Delete")
            }
        }
    }
}
