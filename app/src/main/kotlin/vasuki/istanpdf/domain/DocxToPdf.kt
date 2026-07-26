package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.DocxEngine
import java.io.File

class DocxToPdf(private val docxEngine: DocxEngine) {
    @Throws(Exception::class)
    fun execute(docxUri: Uri): File {
        return docxEngine.exportToPdf(docxUri)
    }
}
