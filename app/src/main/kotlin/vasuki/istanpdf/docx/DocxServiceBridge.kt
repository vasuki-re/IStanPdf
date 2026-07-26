package vasuki.istanpdf.docx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import vasuki.istanpdf.libreoffice.DocxPreviewService
import vasuki.istanpdf.model.PageItem
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object DocxServiceBridge {
    private const val LIBREOFFICE_OPERATION_TIMEOUT_MS = 600000L

    fun preLoadEngine(ctx: Context) {
        val intent = Intent(ctx, DocxPreviewService::class.java)
        intent.putExtra(DocxPreviewService.EXTRA_OPERATION, DocxPreviewService.OP_INIT)
        ctx.startService(intent)
    }

    @Throws(Exception::class)
    fun exportDocxToPdfViaLibreOffice(ctx: Context, docx: Uri): File {
        val intent = Intent(ctx, DocxPreviewService::class.java)
        intent.putExtra(DocxPreviewService.EXTRA_OPERATION, DocxPreviewService.OP_EXPORT_DOCX_TO_PDF)
        intent.putExtra(DocxPreviewService.EXTRA_DOCX_URI, docx.toString())

        val result = runLibreOfficeDocxOperation(ctx, intent, "Converting DOCX to PDF")
        val pdfPath = result.getString(DocxPreviewService.EXTRA_PDF_PATH)
        check(!pdfPath.isNullOrEmpty()) { "LibreOffice did not return a PDF" }

        val pdfFile = File(pdfPath)
        check(pdfFile.exists()) { "LibreOffice returned a missing PDF file" }
        return pdfFile
    }

    @Throws(Exception::class)
    fun saveDocxViaLibreOffice(ctx: Context, source: Uri, snapshot: List<PageItem>, destination: Uri) {
        val originalIndices = IntArray(snapshot.size) { snapshot[it].originalIndex }
        val keepFlags = BooleanArray(snapshot.size) { snapshot[it].keep }

        val intent = Intent(ctx, DocxPreviewService::class.java)
        intent.putExtra(DocxPreviewService.EXTRA_OPERATION, DocxPreviewService.OP_SAVE_EDITED_DOCX)
        intent.putExtra(DocxPreviewService.EXTRA_DOCX_URI, source.toString())
        intent.putExtra(DocxPreviewService.EXTRA_DESTINATION_URI, destination.toString())
        intent.putExtra(DocxPreviewService.EXTRA_PAGE_INDICES, originalIndices)
        intent.putExtra(DocxPreviewService.EXTRA_PAGE_KEEP, keepFlags)

        runLibreOfficeDocxOperation(ctx, intent, "Saving DOCX")
    }

    private fun runLibreOfficeDocxOperation(ctx: Context, operationIntent: Intent, failureMessage: String): Bundle {
        val latch = CountDownLatch(1)
        val resultCode = AtomicInteger(DocxPreviewService.RESULT_ERROR)
        val resultData = AtomicReference<Bundle>()
        val completed = AtomicBoolean(false)

        val fail = {
            if (completed.compareAndSet(false, true)) {
                val error = Bundle()
                error.putString(DocxPreviewService.EXTRA_ERROR, "$failureMessage Failed")
                resultCode.set(DocxPreviewService.RESULT_ERROR)
                resultData.set(error)
                latch.countDown()
            }
        }

        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCodeValue: Int, resultDataValue: Bundle?) {
                if (completed.compareAndSet(false, true)) {
                    resultCode.set(resultCodeValue)
                    resultData.set(resultDataValue)
                    latch.countDown()
                }
            }
        }

        operationIntent.putExtra(DocxPreviewService.EXTRA_RECEIVER, receiver)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {}

            override fun onServiceDisconnected(name: ComponentName) {
                fail()
            }

            override fun onBindingDied(name: ComponentName) {
                fail()
            }
        }

        val appCtx = ctx.applicationContext
        val bound = appCtx.bindService(Intent(appCtx, DocxPreviewService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(bound) { "LibreOffice service could not be started" }

        try {
            val started = ctx.startService(operationIntent)
            checkNotNull(started) { "LibreOffice service could not be started" }

            if (!latch.await(LIBREOFFICE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw IllegalStateException("$failureMessage Failed")
            }

            check(resultCode.get() == DocxPreviewService.RESULT_OK) { "$failureMessage Failed" }

            return resultData.get() ?: Bundle()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("$failureMessage was interrupted", e)
        } finally {
            try { appCtx.unbindService(connection) } catch (_: Exception) {}
        }
    }
}
