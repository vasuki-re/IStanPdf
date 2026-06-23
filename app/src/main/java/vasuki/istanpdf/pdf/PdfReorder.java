package vasuki.istanpdf.pdf;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.InputStream;
import java.util.List;

import vasuki.istanpdf.model.PageItem;

public final class PdfReorder {
    private PdfReorder() {
    }

    public static int run(Context ctx, Uri src, List<PageItem> pages, Uri dst) throws Exception {
        PdfStore.init(ctx);
        int kept = 0;
        java.io.File tempIn = vasuki.istanpdf.util.ContentFiles.copyUriToCache(ctx, src, ".pdf");
        try {
            com.tom_roush.pdfbox.io.MemoryUsageSetting memSetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(50 * 1024 * 1024);
            try (PDDocument doc = PDDocument.load(tempIn, memSetting)) {
                List<com.tom_roush.pdfbox.pdmodel.PDPage> newPages = new java.util.ArrayList<>();
                for (PageItem page : pages) {
                    if (page.keep) {
                        com.tom_roush.pdfbox.pdmodel.PDPage pdPage = doc.getPage(page.originalIndex);
                        if (page.rotation != 0) {
                            int r = (pdPage.getRotation() + page.rotation) % 360;
                            if (r < 0) r += 360;
                            pdPage.setRotation(r);
                        }
                        newPages.add(pdPage);
                        kept++;
                    }
                }
                if (kept == 0) {
                    throw new IllegalArgumentException("Keep at least one page");
                }
                
                com.tom_roush.pdfbox.pdmodel.PDPageTree tree = doc.getPages();
                while (tree.getCount() > 0) {
                    tree.remove(0);
                }
                for (com.tom_roush.pdfbox.pdmodel.PDPage p : newPages) {
                    tree.add(p);
                }
                PdfStore.save(ctx, doc, dst);
            }
        } finally {
            if (tempIn.exists()) tempIn.delete();
        }
        return kept;
    }
}
