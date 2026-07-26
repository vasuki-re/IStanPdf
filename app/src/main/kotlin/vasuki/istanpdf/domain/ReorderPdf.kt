package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.PdfEngine
import vasuki.istanpdf.model.PageItem

class ReorderPdf(private val pdfEngine: PdfEngine) {
    @Throws(Exception::class)
    fun execute(source: Uri, pages: List<PageItem>, destination: Uri): Int {
        return pdfEngine.reorder(source, pages, destination)
    }
}
