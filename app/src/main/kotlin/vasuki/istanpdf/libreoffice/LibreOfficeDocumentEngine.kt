package vasuki.istanpdf.libreoffice

import android.content.Context
import android.net.Uri
import org.libreoffice.kit.Document
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import vasuki.istanpdf.util.ContentFiles
import java.io.File
import java.io.IOException

object LibreOfficeDocumentEngine {
    private var isInitialized = false
    private var cachedOffice: Office? = null

    @Throws(Exception::class)
    fun exportDocxToPdf(context: Context, docxUri: Uri): File {
        var input: File? = null
        val output: File
        try {
            input = ContentFiles.copyUriToCache(context, docxUri, ".docx")
            requireNonEmptyDocx(input)
            output = File.createTempFile("istanpdf_docx_preview_", ".pdf", context.cacheDir)
            saveAs(context, input, output, "pdf")

            check(output.exists() && output.length() > 0) {
                "LibreOffice failed to generate output PDF"
            }
            return output
        } finally {
            if (input?.exists() == true) input.delete()
        }
    }

    private fun requireNonEmptyDocx(input: File?) {
        if (input == null || !input.exists() || input.length() == 0L) {
            throw IOException("Corrupt DOCX file")
        }
    }

    @Throws(Exception::class)
    private fun saveAs(context: Context, input: File, output: File, format: String) {
        val office = getOffice(context)
        var document: Document? = null
        try {
            document = office.documentLoad(Uri.fromFile(input).toString())
                ?: throw IllegalStateException("LibreOffice could not open the document")
            document.saveAs(Uri.fromFile(output).toString(), format, null)
        } finally {
            document?.destroy()
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            LibreOfficeKit.loadNativeLibraries(LibreOfficeManager.libDir(context).absolutePath)
            LibreOfficeRuntime.prepare(context)
        } catch (e: Exception) {
            throw IllegalStateException("LibreOffice runtime setup failed: ${e.message}", e)
        }
        LibreOfficeKit.putenv("SAL_LOG=+WARN")
        LibreOfficeKit.putenv("SAL_LOK_OPTIONS=compact_fonts")
        LibreOfficeKit.init(context)
        checkNotNull(LibreOfficeKit.getLibreOfficeKitHandle()) {
            "LibreOffice native initialization failed"
        }
        isInitialized = true
    }

    @Synchronized
    fun getOffice(context: Context): Office {
        initialize(context)
        if (cachedOffice == null) {
            cachedOffice = Office(LibreOfficeKit.getLibreOfficeKitHandle())
        }
        return cachedOffice!!
    }
}
