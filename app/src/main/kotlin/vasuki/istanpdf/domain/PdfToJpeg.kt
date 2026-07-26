package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.PdfEngine

class PdfToJpeg(private val pdfEngine: PdfEngine) {
    @Throws(Exception::class)
    fun execute(source: Uri, destination: Uri) {
        pdfEngine.pdfToJpegZip(source, destination)
    }
}
