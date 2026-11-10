package vasuki.istanpdf.data

import android.content.Context
import android.net.Uri
import vasuki.istanpdf.docx.DocxServiceBridge
import java.io.File

class XlsxEngine(context: Context) {
    private val context = context.applicationContext

    @Throws(Exception::class)
    fun exportToPdf(xlsxUri: Uri): File =
        DocxServiceBridge.exportXlsxToPdfViaLibreOffice(context, xlsxUri)
}
