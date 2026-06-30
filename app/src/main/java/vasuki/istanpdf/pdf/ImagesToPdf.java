package vasuki.istanpdf.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import vasuki.istanpdf.model.PageItem;
import vasuki.istanpdf.util.ContentFiles;

public final class ImagesToPdf {
    private static final int MAX_IMAGE_DIMENSION = 4096;

    private ImagesToPdf() {
    }

    public static void run(Context ctx, List<Uri> src, List<PageItem> pages, Uri dst) throws Exception {
        run(ctx, src, pages, dst, null);
    }

    public static void run(Context ctx, List<Uri> src, List<PageItem> pages, Uri dst,
                            PdfProgressCallback callback) throws Exception {
        PdfStore.init(ctx);
        try (PDDocument out = new PDDocument()) {
            int progress = 0;
            int total = 0;
            for (PageItem p : pages) {
                if (p.keep) total++;
            }

            for (PageItem pageItem : pages) {
                if (!pageItem.keep) continue;
                Uri uri = src.get(pageItem.originalIndex);

                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inJustDecodeBounds = true;
                try (InputStream boundsIn = ctx.getContentResolver().openInputStream(uri)) {
                    if (boundsIn == null) throw new IllegalArgumentException("Cannot open image");
                    BitmapFactory.decodeStream(boundsIn, null, opt);
                }

                String mime = opt.outMimeType;
                boolean needsConversion = mime != null
                        && !mime.equals("image/jpeg")
                        && !mime.equals("image/png")
                        && !mime.equals("image/gif");

                byte[] pdfImageBytes;
                float imgWidth = opt.outWidth;
                float imgHeight = opt.outHeight;

                if (needsConversion) {
                    opt.inJustDecodeBounds = false;
                    opt.inSampleSize = calcSampleSize(opt.outWidth, opt.outHeight);
                    Bitmap bmp;
                    try (InputStream pixelIn = ctx.getContentResolver().openInputStream(uri)) {
                        if (pixelIn == null) throw new IllegalArgumentException("Cannot open image");
                        bmp = BitmapFactory.decodeStream(pixelIn, null, opt);
                    }
                    if (bmp == null) throw new IllegalArgumentException("Cannot decode image");
                    try {
                        imgWidth = bmp.getWidth() * opt.inSampleSize;
                        imgHeight = bmp.getHeight() * opt.inSampleSize;
                        java.io.ByteArrayOutputStream jpegOut = new java.io.ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpegOut);
                        pdfImageBytes = jpegOut.toByteArray();
                    } finally {
                        bmp.recycle();
                    }
                } else {
                    try (InputStream rawIn = ctx.getContentResolver().openInputStream(uri)) {
                        if (rawIn == null) throw new IllegalArgumentException("Cannot open image");
                        pdfImageBytes = PdfStore.bytes(rawIn);
                    }
                }

                float pw = imgWidth * 72f / 300f;
                float ph = imgHeight * 72f / 300f;
                if (pw <= 0 || ph <= 0) {
                    pw = 595;
                    ph = 842;
                }

                PDPage page = new PDPage(new PDRectangle(pw, ph));
                if (pageItem.rotation != 0) {
                    page.setRotation(page.getRotation() + pageItem.rotation);
                }
                out.addPage(page);
                PDImageXObject pdfImg = PDImageXObject.createFromByteArray(out, pdfImageBytes, "image");
                try (PDPageContentStream stream = new PDPageContentStream(out, page)) {
                    stream.drawImage(pdfImg, 0, 0, pw, ph);
                }
                pdfImageBytes = null;

                progress++;
                if (callback != null) {
                    callback.onProgress(progress, total);
                }
            }
            PdfStore.save(ctx, out, dst);
        }
    }

    private static int calcSampleSize(int width, int height) {
        int sample = 1;
        while (width / (sample * 2) > MAX_IMAGE_DIMENSION
                || height / (sample * 2) > MAX_IMAGE_DIMENSION) {
            sample *= 2;
        }
        return sample;
    }
}
