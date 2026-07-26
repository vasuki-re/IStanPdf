package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.PdfEngine
import vasuki.istanpdf.model.PageItem

class ImagesToPdf(private val pdfEngine: PdfEngine) {
    @Throws(Exception::class)
    fun execute(sources: List<Uri>, pages: List<PageItem>, destination: Uri) {
        require(sources.isNotEmpty()) { "At least one image is required" }
        pdfEngine.imagesToPdf(sources, pages, destination)
    }
}
