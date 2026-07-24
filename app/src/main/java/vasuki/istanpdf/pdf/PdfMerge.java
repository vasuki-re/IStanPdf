package vasuki.istanpdf.pdf;

import android.content.Context;
import android.net.Uri;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import vasuki.istanpdf.util.ContentFiles;

public final class PdfMerge {
    private PdfMerge() {
    }

    public static void run(Context ctx, List<Uri> src, List<Integer> rotations, Uri dst) throws Exception {
        File tempOut = File.createTempFile("merged_", ".pdf", ctx.getCacheDir());
        List<File> tempIns = new ArrayList<>();
        try {
            PdfDocument merged = new PdfDocument(new PdfWriter(new FileOutputStream(tempOut)));
            PdfMerger merger = new PdfMerger(merged);
            List<int[]> rotationRanges = new ArrayList<>();
            int pageOffset = 0;
            try {
                for (int i = 0; i < src.size(); i++) {
                    Uri uri = src.get(i);
                    int rotation = rotations != null && i < rotations.size() ? rotations.get(i) : 0;
                    File tempIn = ContentFiles.copyUriToCache(ctx, uri, ".pdf");
                    tempIns.add(tempIn);

                    PdfDocument srcDoc = new PdfDocument(new PdfReader(tempIn));
                    try {
                        int srcPages = srcDoc.getNumberOfPages();
                        if (rotation != 0) {
                            rotationRanges.add(new int[]{pageOffset + 1, pageOffset + srcPages, rotation});
                        }
                        merger.merge(srcDoc, 1, srcPages);
                        pageOffset += srcPages;
                    } finally {
                        srcDoc.close();
                    }
                }

                for (int[] range : rotationRanges) {
                    for (int p = range[0]; p <= range[1]; p++) {
                        PdfPage page = merged.getPage(p);
                        int existing = page.getRotation();
                        page.put(PdfName.Rotate, new PdfNumber(existing + range[2]));
                    }
                }
            } finally {
                merged.close();
            }
            ContentFiles.copyFileToUri(ctx, tempOut, dst);
        } finally {
            if (tempOut.exists()) tempOut.delete();
            for (File tempIn : tempIns) {
                if (tempIn.exists()) tempIn.delete();
            }
        }
    }
}
