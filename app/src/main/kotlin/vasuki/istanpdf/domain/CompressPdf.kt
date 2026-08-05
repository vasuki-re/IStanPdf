package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.PdfEngine

class CompressPdf(private val pdfEngine: PdfEngine) {

    fun hasEmbeddedImages(source: Uri): Boolean = pdfEngine.hasEmbeddedImages(source)

    @Throws(Exception::class)
    fun executeByResolution(source: Uri, destination: Uri, dpi: Int, quality: Int) {
        pdfEngine.compressByResolution(source, destination, dpi, quality)
    }

    @Throws(Exception::class)
    fun executeBySize(source: Uri, destination: Uri, targetBytes: Long): Long {
        return pdfEngine.compressBySize(source, destination, targetBytes)
    }
}
