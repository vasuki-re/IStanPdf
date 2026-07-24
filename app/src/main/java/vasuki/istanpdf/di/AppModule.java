package vasuki.istanpdf.di;

import android.content.Context;

import vasuki.istanpdf.data.CacheManager;
import vasuki.istanpdf.data.DocxEngine;
import vasuki.istanpdf.data.DocumentManager;
import vasuki.istanpdf.data.PdfEngine;
import vasuki.istanpdf.domain.DocxToPdf;
import vasuki.istanpdf.domain.ImagesToPdf;
import vasuki.istanpdf.domain.MergePdf;
import vasuki.istanpdf.domain.PdfToJpeg;
import vasuki.istanpdf.domain.ReorderPdf;
import vasuki.istanpdf.domain.SaveDocx;

public final class AppModule {
    private static AppModule instance;

    private final DocumentManager documentManager;
    private final CacheManager cacheManager;
    private final PdfEngine pdfEngine;
    private final DocxEngine docxEngine;

    private final MergePdf mergePdf;
    private final ReorderPdf reorderPdf;
    private final ImagesToPdf imagesToPdf;
    private final PdfToJpeg pdfToJpeg;
    private final DocxToPdf docxToPdf;
    private final SaveDocx saveDocx;

    private AppModule(Context context) {
        Context appContext = context.getApplicationContext();

        documentManager = new DocumentManager(appContext);
        cacheManager = new CacheManager(appContext);
        pdfEngine = new PdfEngine(appContext);
        docxEngine = new DocxEngine(appContext);

        mergePdf = new MergePdf(pdfEngine);
        reorderPdf = new ReorderPdf(pdfEngine);
        imagesToPdf = new ImagesToPdf(pdfEngine);
        pdfToJpeg = new PdfToJpeg(pdfEngine);
        docxToPdf = new DocxToPdf(docxEngine);
        saveDocx = new SaveDocx(docxEngine);
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (AppModule.class) {
                if (instance == null) {
                    instance = new AppModule(context);
                }
            }
        }
    }

    public static AppModule get() {
        if (instance == null) {
            throw new IllegalStateException("AppModule not initialised. Call AppModule.init(context) in Application.onCreate()");
        }
        return instance;
    }

    public DocumentManager documentManager() { return documentManager; }
    public CacheManager cacheManager() { return cacheManager; }
    public PdfEngine pdfEngine() { return pdfEngine; }
    public DocxEngine docxEngine() { return docxEngine; }

    public MergePdf mergePdf() { return mergePdf; }
    public ReorderPdf reorderPdf() { return reorderPdf; }
    public ImagesToPdf imagesToPdf() { return imagesToPdf; }
    public PdfToJpeg pdfToJpeg() { return pdfToJpeg; }
    public DocxToPdf docxToPdf() { return docxToPdf; }
    public SaveDocx saveDocx() { return saveDocx; }
}
