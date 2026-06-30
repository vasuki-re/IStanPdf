package vasuki.istanpdf.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PdfToJpegZip {
    private PdfToJpegZip() {
    }

    public static void run(Context ctx, Uri src, Uri dst) throws Exception {
        run(ctx, src, dst, null);
    }

    public static void run(Context ctx, Uri src, Uri dst, PdfProgressCallback callback) throws Exception {
        String base = PdfStore.base(ctx, src);
        java.io.File tempPdf = null;
        ParcelFileDescriptor fd = null;
        OutputStream out = null;
        try {
            if ("file".equals(src.getScheme())) {
                fd = ParcelFileDescriptor.open(new java.io.File(src.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                tempPdf = vasuki.istanpdf.util.ContentFiles.copyUriToCache(ctx, src, ".pdf");
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            if (fd == null) {
                throw new IllegalArgumentException("Cannot open PDF file");
            }
            out = ctx.getContentResolver().openOutputStream(dst);
            if (out == null) {
                throw new IllegalStateException("Cannot open output file");
            }
            PdfRenderer rnd = new PdfRenderer(fd);
            fd = null;
            try (PdfRenderer renderer = rnd;
                 ZipOutputStream zip = new ZipOutputStream(out)) {
                out = null;
                int total = renderer.getPageCount();
                for (int i = 0; i < total; i++) {
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int w = page.getWidth() * 2;
                        int h = page.getHeight() * 2;
                        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        try {
                            bmp.eraseColor(Color.WHITE);
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            String name = String.format("%s-%03d.jpg", base, i + 1);
                            zip.putNextEntry(new ZipEntry(name));
                            boolean success = bmp.compress(Bitmap.CompressFormat.JPEG, 90, zip);
                            zip.closeEntry();
                            if (!success) {
                                throw new IllegalStateException("Failed to compress page " + (i + 1) + " to JPEG");
                            }
                        } finally {
                            bmp.recycle();
                        }
                    }
                    if (callback != null) {
                        callback.onProgress(i + 1, total);
                    }
                }
            }
        } finally {
            if (fd != null) {
                try { fd.close(); } catch (Exception ignored) {}
            }
            if (out != null) {
                try { out.close(); } catch (Exception ignored) {}
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }
}
