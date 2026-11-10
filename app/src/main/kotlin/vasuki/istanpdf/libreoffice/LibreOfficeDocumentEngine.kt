package vasuki.istanpdf.libreoffice

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.libreoffice.kit.Document
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import vasuki.istanpdf.util.ContentFiles
import java.io.File
import java.io.IOException

object LibreOfficeDocumentEngine {
    private val PRESENTATION_SUFFIXES = setOf("ppt", "pptx")
    private val SPREADSHEET_SUFFIXES = setOf("xls", "xlsx")
    private var isInitialized = false
    private var nativesLoaded = false
    private var cachedOffice: Office? = null

    @Throws(Exception::class)
    fun exportDocxToPdf(context: Context, docxUri: Uri): File =
        exportToPdf(
            context,
            docxUri,
            ".docx",
            "istanpdf_docx_preview_",
            "Corrupt DOCX file"
        )

    @Throws(Exception::class)
    fun exportPptxToPdf(context: Context, pptxUri: Uri): File =
        exportToPdf(
            context,
            pptxUri,
            sourceSuffix(context, pptxUri, ".pptx", PRESENTATION_SUFFIXES),
            "istanpdf_pptx_preview_",
            "Corrupt presentation file"
        )

    @Throws(Exception::class)
    fun exportXlsxToPdf(context: Context, xlsxUri: Uri): File =
        exportToPdf(
            context,
            xlsxUri,
            sourceSuffix(context, xlsxUri, ".xlsx", SPREADSHEET_SUFFIXES),
            "istanpdf_xlsx_preview_",
            "Corrupt spreadsheet file"
        )

    @Throws(Exception::class)
    private fun exportToPdf(
        context: Context,
        sourceUri: Uri,
        suffix: String,
        prefix: String,
        corruptMessage: String
    ): File {
        var input: File? = null
        try {
            input = ContentFiles.copyUriToCache(context, sourceUri, suffix)
            requireNonEmpty(input, corruptMessage)
            val output = File.createTempFile(prefix, ".pdf", context.cacheDir)
            saveAs(context, input, output, "pdf")

            check(output.exists() && output.length() > 0) {
                "LibreOffice failed to generate output PDF"
            }
            return output
        } finally {
            if (input?.exists() == true) input.delete()
        }
    }

    private fun sourceSuffix(context: Context, uri: Uri, fallback: String, allowed: Set<String>): String {
        val displayName = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
        val extension = displayName?.substringAfterLast('.', "")?.lowercase()
        return if (extension != null && extension in allowed) ".$extension" else fallback
    }

    private fun requireNonEmpty(input: File?, message: String) {
        if (input == null || !input.exists() || input.length() == 0L) {
            throw IOException(message)
        }
    }

    @Throws(Exception::class)
    private fun saveAs(context: Context, input: File, output: File, format: String) {
        val office = getOffice(context)
        var document: Document? = null
        try {
            document = office.documentLoad(Uri.fromFile(input).toString())
                ?: throw IllegalStateException("LibreOffice could not open the document")
            if (format == "pdf" && document.getDocumentType() == Document.DOCTYPE_SPREADSHEET) {
                SpreadsheetPageFitter.fit(document)
            }
            document.saveAs(Uri.fromFile(output).toString(), format, null)
        } finally {
            document?.destroy()
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        if (!nativesLoaded) {
            try {
                LibreOfficeKit.loadNativeLibraries(LibreOfficeManager.libDir(context).absolutePath)
                LibreOfficeRuntime.prepare(context)
                nativesLoaded = true
            } catch (e: Exception) {
                throw IllegalStateException("LibreOffice runtime setup failed: ${e.message}", e)
            }
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
