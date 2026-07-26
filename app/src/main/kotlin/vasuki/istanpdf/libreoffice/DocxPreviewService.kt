package vasuki.istanpdf.libreoffice

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import androidx.core.content.IntentCompat
import vasuki.istanpdf.docx.DocxRemove
import vasuki.istanpdf.docx.DocxReorder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DocxPreviewService : Service() {
    companion object {
        const val EXTRA_OPERATION = "operation"
        const val EXTRA_DOCX_URI = "docx_uri"
        const val EXTRA_DESTINATION_URI = "destination_uri"
        const val EXTRA_PAGE_INDICES = "page_indices"
        const val EXTRA_PAGE_KEEP = "page_keep"
        const val EXTRA_RECEIVER = "receiver"
        const val EXTRA_PDF_PATH = "pdf_path"
        const val EXTRA_ERROR = "error"
        const val OP_EXPORT_DOCX_TO_PDF = "export_docx_to_pdf"
        const val OP_SAVE_EDITED_DOCX = "save_edited_docx"
        const val OP_INIT = "init_engine"
        const val RESULT_OK = 1
        const val RESULT_ERROR = 2
    }

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val binder: IBinder = Binder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val receiver = IntentCompat.getParcelableExtra(intent, EXTRA_RECEIVER, ResultReceiver::class.java)
        val operation = intent.getStringExtra(EXTRA_OPERATION)

        worker.execute {
            val result = Bundle()
            try {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Service was cancelled via timeout")
                }
                when (operation) {
                    OP_INIT -> {
                        LibreOfficeDocumentEngine.initialize(this)
                        if (Thread.currentThread().isInterrupted) return@execute
                    }
                    OP_EXPORT_DOCX_TO_PDF -> {
                        val pdf = LibreOfficeDocumentEngine.exportDocxToPdf(
                            this, Uri.parse(requireStringExtra(intent, EXTRA_DOCX_URI))
                        )
                        if (Thread.currentThread().isInterrupted) {
                            if (pdf.exists()) pdf.delete()
                            return@execute
                        }
                        result.putString(EXTRA_PDF_PATH, pdf.absolutePath)
                    }
                    OP_SAVE_EDITED_DOCX -> {
                        val source = Uri.parse(requireStringExtra(intent, EXTRA_DOCX_URI))
                        val destination = Uri.parse(requireStringExtra(intent, EXTRA_DESTINATION_URI))
                        val originalIndices = intent.getIntArrayExtra(EXTRA_PAGE_INDICES)
                        val keepFlags = intent.getBooleanArrayExtra(EXTRA_PAGE_KEEP)
                        require(originalIndices != null && keepFlags != null && originalIndices.size == keepFlags.size) {
                            "Invalid page selection"
                        }

                        val removeSelections = mutableListOf<DocxRemove.PageSelection>()
                        val reorderSelections = mutableListOf<DocxReorder.PageSelection>()
                        var last = -1
                        var isPlainRemove = true
                        for (i in originalIndices.indices) {
                            removeSelections.add(DocxRemove.PageSelection(originalIndices[i], keepFlags[i]))
                            reorderSelections.add(DocxReorder.PageSelection(originalIndices[i], keepFlags[i]))
                            if (keepFlags[i]) {
                                if (last != -1 && originalIndices[i] < last) {
                                    isPlainRemove = false
                                }
                                last = originalIndices[i]
                            }
                        }

                        if (isPlainRemove) {
                            DocxRemove.run(this, source, removeSelections, destination)
                        } else {
                            DocxReorder.run(this, source, reorderSelections, destination)
                        }
                        if (Thread.currentThread().isInterrupted) return@execute
                    }
                    else -> throw IllegalArgumentException("Unsupported LibreOffice operation")
                }
                receiver?.send(RESULT_OK, result)
            } catch (throwable: Throwable) {
                if (Thread.currentThread().isInterrupted) return@execute
                result.putString(EXTRA_ERROR, message(throwable))
                receiver?.send(RESULT_ERROR, result)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun message(throwable: Throwable): String =
        throwable.message ?: throwable.javaClass.simpleName

    private fun requireStringExtra(intent: Intent, key: String): String {
        val value = intent.getStringExtra(key)
        require(!value.isNullOrEmpty()) { "Missing $key" }
        return value
    }
}
