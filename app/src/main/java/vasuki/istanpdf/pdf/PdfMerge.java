package vasuki.istanpdf.pdf;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class PdfMerge {
    private PdfMerge() {
    }

    public static void run(Context ctx, List<Uri> src, List<Integer> rotations, Uri dst) throws Exception {
        PdfStore.init(ctx);
        com.tom_roush.pdfbox.multipdf.PDFMergerUtility merger = new com.tom_roush.pdfbox.multipdf.PDFMergerUtility();
        java.io.File tempOut = java.io.File.createTempFile("merged_", ".pdf", ctx.getCacheDir());
        merger.setDestinationFileName(tempOut.getAbsolutePath());
        List<java.io.File> tempIns = new ArrayList<>();
        try {
            for (int i = 0; i < src.size(); i++) {
                Uri uri = src.get(i);
                int rotation = rotations != null && i < rotations.size() ? rotations.get(i) : 0;
                java.io.File tempIn = vasuki.istanpdf.util.ContentFiles.copyUriToCache(ctx, uri, ".pdf");
                
                if (rotation != 0) {
                    try (PDDocument doc = PDDocument.load(tempIn)) {
                        for (int p = 0; p < doc.getNumberOfPages(); p++) {
                            com.tom_roush.pdfbox.pdmodel.PDPage page = doc.getPage(p);
                            page.setRotation(page.getRotation() + rotation);
                        }
                        doc.save(tempIn);
                    }
                }
                tempIns.add(tempIn);
                merger.addSource(tempIn);
            }
            com.tom_roush.pdfbox.io.MemoryUsageSetting memSetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024);
            merger.mergeDocuments(memSetting);
            vasuki.istanpdf.util.ContentFiles.copyFileToUri(ctx, tempOut, dst);
        } finally {
            if (tempOut.exists()) tempOut.delete();
            for (java.io.File tempIn : tempIns) {
                if (tempIn.exists()) tempIn.delete();
            }
        }
    }
}
