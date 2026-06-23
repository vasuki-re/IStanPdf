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
        String base = PdfStore.base(ctx, src);
        java.io.File tempPdf = null;
        ParcelFileDescriptor fd = null;
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
            OutputStream out = ctx.getContentResolver().openOutputStream(dst);
            if (out == null) {
                fd.close();
                throw new IllegalStateException("Cannot open output file");
            }
            try (ParcelFileDescriptor pdf = fd;
                 OutputStream raw = out;
                 ZipOutputStream zip = new ZipOutputStream(raw)) {
                try (PdfRenderer rnd = new PdfRenderer(pdf)) {
                for (int i = 0; i < rnd.getPageCount(); i++) {
                    try (PdfRenderer.Page page = rnd.openPage(i)) {
                        int w = page.getWidth() * 2;
                        int h = page.getHeight() * 2;
                        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        try {
                            bmp.eraseColor(Color.WHITE);
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            String name = String.format("%s-%03d.jpg", base, i + 1);
                            zip.putNextEntry(new ZipEntry(name));
                            boolean success = bmp.compress(Bitmap.CompressFormat.JPEG, 100, zip);
                            zip.closeEntry();
                            if (!success) {
                                throw new IllegalStateException("Failed to compress PNG to JPEG for page " + (i + 1));
                            }
                        } finally {
                            bmp.recycle();
                        }
                    }
                }
            }
        }
        } finally {
            if (fd != null) {
                try { fd.close(); } catch (Exception ignored) {}
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }
}
