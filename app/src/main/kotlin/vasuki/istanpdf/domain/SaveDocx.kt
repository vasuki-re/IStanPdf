package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.DocxEngine
import vasuki.istanpdf.model.PageItem

class SaveDocx(private val docxEngine: DocxEngine) {
    @Throws(Exception::class)
    fun execute(source: Uri, snapshot: List<PageItem>, destination: Uri) {
        docxEngine.saveEdited(source, snapshot, destination)
    }
}
