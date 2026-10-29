package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.PptxEngine
import java.io.File

class PptxToPdf(private val pptxEngine: PptxEngine) {
    @Throws(Exception::class)
    fun execute(pptxUri: Uri): File = pptxEngine.exportToPdf(pptxUri)
}
