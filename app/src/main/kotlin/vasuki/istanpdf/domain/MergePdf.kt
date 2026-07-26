package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.PdfEngine

class MergePdf(private val pdfEngine: PdfEngine) {
    @Throws(Exception::class)
    fun execute(sources: List<Uri>, rotations: List<Int>, destination: Uri) {
        require(sources.size >= 2) { "At least 2 PDFs are required to merge" }
        pdfEngine.merge(sources, rotations, destination)
    }
}
