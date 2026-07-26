package vasuki.istanpdf.di

import android.content.Context
import vasuki.istanpdf.data.CacheManager
import vasuki.istanpdf.data.DocxEngine
import vasuki.istanpdf.data.DocumentManager
import vasuki.istanpdf.data.PdfEngine
import vasuki.istanpdf.domain.DocxToPdf
import vasuki.istanpdf.domain.ImagesToPdf
import vasuki.istanpdf.domain.MergePdf
import vasuki.istanpdf.domain.PdfToJpeg
import vasuki.istanpdf.domain.ReorderPdf
import vasuki.istanpdf.domain.SaveDocx

class AppModule private constructor(context: Context) {
    private val ctx = context.applicationContext

    val documentManager = DocumentManager(ctx)
    val cacheManager = CacheManager(ctx)
    val pdfEngine = PdfEngine(ctx)
    val docxEngine = DocxEngine(ctx)

    val mergePdf = MergePdf(pdfEngine)
    val reorderPdf = ReorderPdf(pdfEngine)
    val imagesToPdf = ImagesToPdf(pdfEngine)
    val pdfToJpeg = PdfToJpeg(pdfEngine)
    val docxToPdf = DocxToPdf(docxEngine)
    val saveDocx = SaveDocx(docxEngine)

    companion object {
        @Volatile
        private var instance: AppModule? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(AppModule::class.java) {
                    if (instance == null) {
                        instance = AppModule(context)
                    }
                }
            }
        }

        fun get(): AppModule = instance
            ?: error("AppModule not initialised. Call AppModule.init(context) in Application.onCreate()")
    }
}
