package vasuki.istanpdf.data

import android.content.Context
import android.net.Uri
import vasuki.istanpdf.docx.DocxServiceBridge
import java.io.File

class PptxEngine(context: Context) {
    private val context = context.applicationContext

    @Throws(Exception::class)
    fun exportToPdf(pptxUri: Uri): File =
        DocxServiceBridge.exportPptxToPdfViaLibreOffice(context, pptxUri)
}
