package vasuki.istanpdf.pdf;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import vasuki.istanpdf.util.ContentFiles;

public final class PdfMerge {
    private PdfMerge() {
    }

    public static void run(Context ctx, List<Uri> src, List<Integer> rotations, Uri dst) throws Exception {
        run(ctx, src, rotations, dst, null);
    }

    public static void run(Context ctx, List<Uri> src, List<Integer> rotations, Uri dst,
                            PdfProgressCallback callback) throws Exception {
        PdfStore.init(ctx);
        File tempOut = File.createTempFile("merged_", ".pdf", ctx.getCacheDir());
        List<File> tempIns = new ArrayList<>();
        PDFMergerUtility merger = new PDFMergerUtility();
        PDDocument merged = new PDDocument();
        try {
            com.tom_roush.pdfbox.io.MemoryUsageSetting memSetting =
                    com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024);

            for (int i = 0; i < src.size(); i++) {
                Uri uri = src.get(i);
                int rotation = rotations != null && i < rotations.size() ? rotations.get(i) : 0;
                File tempIn = ContentFiles.copyUriToCache(ctx, uri, ".pdf");
                tempIns.add(tempIn);

                try (PDDocument doc = PDDocument.load(tempIn, memSetting)) {
                    if (rotation != 0) {
                        for (int p = 0; p < doc.getNumberOfPages(); p++) {
                            PDPage page = doc.getPage(p);
                            page.setRotation(page.getRotation() + rotation);
                        }
                    }
                    merger.appendDocument(merged, doc);
                }

                if (callback != null) {
                    callback.onProgress(i + 1, src.size());
                }
            }
            merged.save(tempOut);
            ContentFiles.copyFileToUri(ctx, tempOut, dst);
        } finally {
            merged.close();
            if (tempOut.exists()) tempOut.delete();
            for (File tempIn : tempIns) {
                if (tempIn.exists()) tempIn.delete();
            }
        }
    }
}
