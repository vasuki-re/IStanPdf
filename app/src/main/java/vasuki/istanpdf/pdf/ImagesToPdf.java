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

import java.io.InputStream;
import java.util.List;

public final class ImagesToPdf {
    private ImagesToPdf() {
    }

    public static void run(Context ctx, List<Uri> src, List<vasuki.istanpdf.model.PageItem> pages, Uri dst) throws Exception {
        PdfStore.init(ctx);
        try (PDDocument out = new PDDocument()) {
            for (vasuki.istanpdf.model.PageItem pageItem : pages) {
                if (!pageItem.keep) continue;
                Uri uri = src.get(pageItem.originalIndex);
                byte[] img;
                try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                    if (in == null) {
                        throw new IllegalArgumentException("Cannot open image");
                    }
                    img = PdfStore.bytes(in);
                }

                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(img, 0, img.length, opt);
                
                String mime = opt.outMimeType;
                byte[] pdfImageBytes = img;
                if (mime != null && !mime.equals("image/jpeg") && !mime.equals("image/png") && !mime.equals("image/gif")) {
                    Bitmap bmp = BitmapFactory.decodeByteArray(img, 0, img.length);
                    if (bmp == null) throw new IllegalArgumentException("Cannot decode image");
                    java.io.ByteArrayOutputStream jpegOut = new java.io.ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, jpegOut);
                    pdfImageBytes = jpegOut.toByteArray();
                    bmp.recycle();
                }

                float pw = opt.outWidth * 72f / 300f;
                float ph = opt.outHeight * 72f / 300f;
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
                img = null;
                pdfImageBytes = null;
            }
            PdfStore.save(ctx, out, dst);
        }
    }
}
