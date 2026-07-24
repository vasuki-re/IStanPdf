package vasuki.istanpdf.pdf;

import android.content.Context;
import android.net.Uri;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import vasuki.istanpdf.model.PageItem;

public final class PdfReorder {
    private PdfReorder() {
    }

    public static int run(Context ctx, Uri src, List<PageItem> pages, Uri dst) throws Exception {
        int kept = 0;
        File tempIn = vasuki.istanpdf.util.ContentFiles.copyUriToCache(ctx, src, ".pdf");
        try {
            List<Integer> pageNumbers = new ArrayList<>();
            List<Integer> rotations = new ArrayList<>();

            for (PageItem page : pages) {
                if (page.keep) {
                    pageNumbers.add(page.originalIndex + 1);
                    rotations.add(page.rotation);
                    kept++;
                }
            }
            if (kept == 0) {
                throw new IllegalArgumentException("Keep at least one page");
            }

            try (OutputStream out = PdfStore.openDst(ctx, dst)) {
                PdfDocument srcDoc = new PdfDocument(new PdfReader(tempIn));
                PdfDocument dstDoc = new PdfDocument(new PdfWriter(out));
                try {
                    srcDoc.copyPagesTo(pageNumbers, dstDoc);

                    for (int i = 1; i <= dstDoc.getNumberOfPages(); i++) {
                        int rotation = rotations.get(i - 1);
                        if (rotation != 0) {
                            PdfPage dstPage = dstDoc.getPage(i);
                            int existing = dstPage.getRotation();
                            int r = (existing + rotation) % 360;
                            if (r < 0) r += 360;
                            dstPage.put(PdfName.Rotate, new PdfNumber(r));
                        }
                    }
                } finally {
                    dstDoc.close();
                    srcDoc.close();
                }
            }
        } finally {
            if (tempIn.exists()) tempIn.delete();
        }
        return kept;
    }
}
