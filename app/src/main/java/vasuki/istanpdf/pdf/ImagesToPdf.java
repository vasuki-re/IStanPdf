package vasuki.istanpdf.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import vasuki.istanpdf.model.PageItem;

public final class ImagesToPdf {
    private static final int MAX_IMAGE_DIMENSION = 4096;

    private ImagesToPdf() {
    }

    public static void run(Context ctx, List<Uri> src, List<PageItem> pages, Uri dst) throws Exception {
        try (OutputStream out = PdfStore.openDst(ctx, dst)) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
            try {
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

                    ImageData imageData;
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
                            imageData = ImageDataFactory.createJpeg(jpegOut.toByteArray());
                        } finally {
                            bmp.recycle();
                        }
                    } else {
                        try (InputStream rawIn = ctx.getContentResolver().openInputStream(uri)) {
                            if (rawIn == null) throw new IllegalArgumentException("Cannot open image");
                            byte[] imgBytes = PdfStore.bytes(rawIn);
                            if ("image/jpeg".equals(mime)) {
                                imageData = ImageDataFactory.createJpeg(imgBytes);
                            } else {
                                imageData = ImageDataFactory.create(imgBytes);
                            }
                        }
                    }

                    float pw = imgWidth * 72f / 300f;
                    float ph = imgHeight * 72f / 300f;
                    if (pw <= 0 || ph <= 0) {
                        pw = 595;
                        ph = 842;
                    }

                    PageSize pageSize = new PageSize(pw, ph);
                    PdfPage page = pdfDoc.addNewPage(pageSize);
                    if (pageItem.rotation != 0) {
                        int r = pageItem.rotation % 360;
                        if (r < 0) r += 360;
                        page.put(PdfName.Rotate, new PdfNumber(r));
                    }

                    PdfCanvas canvas = new PdfCanvas(page);
                    canvas.addImageFittedIntoRectangle(imageData, new Rectangle(0, 0, pw, ph), false);
                }
            } finally {
                pdfDoc.close();
            }
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
