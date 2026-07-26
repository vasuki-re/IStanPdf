package vasuki.istanpdf.data

import android.content.Context
import android.net.Uri
import vasuki.istanpdf.docx.DocxServiceBridge
import vasuki.istanpdf.model.PageItem
import java.io.File

class DocxEngine(context: Context) {
    private val context = context.applicationContext

    fun preLoad() = DocxServiceBridge.preLoadEngine(context)

    @Throws(Exception::class)
    fun exportToPdf(docxUri: Uri): File =
        DocxServiceBridge.exportDocxToPdfViaLibreOffice(context, docxUri)

    @Throws(Exception::class)
    fun saveEdited(source: Uri, snapshot: List<PageItem>, destination: Uri) =
        DocxServiceBridge.saveDocxViaLibreOffice(context, source, snapshot, destination)
}
