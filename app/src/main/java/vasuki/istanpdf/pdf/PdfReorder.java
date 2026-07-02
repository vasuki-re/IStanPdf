package vasuki.istanpdf.pdf;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageTree;

import java.util.ArrayList;
import java.util.List;

import vasuki.istanpdf.model.PageItem;

public final class PdfReorder {
    private PdfReorder() {
    }

    public static int run(Context ctx, Uri src, List<PageItem> pages, Uri dst) throws Exception {
        return run(ctx, src, pages, dst, null);
    }

    public static int run(Context ctx, Uri src, List<PageItem> pages, Uri dst,
                           PdfProgressCallback callback) throws Exception {
        PdfStore.init(ctx);
        int kept = 0;
        java.io.File tempIn = vasuki.istanpdf.util.ContentFiles.copyUriToCache(ctx, src, ".pdf");
        try {
            com.tom_roush.pdfbox.io.MemoryUsageSetting memSetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024);
            try (PDDocument doc = PDDocument.load(tempIn, memSetting)) {
                List<PDPage> newPages = new ArrayList<>();
                boolean isReordered = false;
                int lastIdx = -1;
                List<Integer> keptIndices = new ArrayList<>();
                
                for (PageItem page : pages) {
                    if (page.keep) {
                        PDPage pdPage = doc.getPage(page.originalIndex);
                        if (page.rotation != 0) {
                            int r = (pdPage.getRotation() + page.rotation) % 360;
                            if (r < 0) r += 360;
                            pdPage.setRotation(r);
                        }
                        newPages.add(pdPage);
                        kept++;
                        keptIndices.add(page.originalIndex);
                        
                        if (lastIdx != -1 && page.originalIndex < lastIdx) {
                            isReordered = true;
                        }
                        lastIdx = page.originalIndex;
                    }
                }
                if (kept == 0) {
                    throw new IllegalArgumentException("Keep at least one page");
                }
                
                PDPageTree tree = doc.getPages();
                if (!isReordered) {
                    // Fast path: Just remove discarded pages
                    for (int i = tree.getCount() - 1; i >= 0; i--) {
                        if (!keptIndices.contains(i)) {
                            tree.remove(i);
                        }
                    }
                    if (callback != null) {
                        callback.onProgress(kept, kept);
                    }
                } else {
                    // Slow path: Rebuild tree chronologically
                    for (int i = tree.getCount() - 1; i >= 0; i--) {
                        tree.remove(i);
                    }
                    int progress = 0;
                    for (PDPage p : newPages) {
                        tree.add(p);
                        progress++;
                        if (callback != null) {
                            callback.onProgress(progress, newPages.size());
                        }
                    }
                }
                PdfStore.save(ctx, doc, dst);
            }
        } finally {
            if (tempIn.exists()) tempIn.delete();
        }
        return kept;
    }
}
