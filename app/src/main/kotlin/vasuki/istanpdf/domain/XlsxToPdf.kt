package vasuki.istanpdf.domain

import android.net.Uri
import vasuki.istanpdf.data.XlsxEngine
import java.io.File

class XlsxToPdf(private val xlsxEngine: XlsxEngine) {
    @Throws(Exception::class)
    fun execute(xlsxUri: Uri): File = xlsxEngine.exportToPdf(xlsxUri)
}
