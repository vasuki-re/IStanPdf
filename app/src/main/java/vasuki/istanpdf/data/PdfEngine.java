package vasuki.istanpdf.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import vasuki.istanpdf.model.PageItem;
import vasuki.istanpdf.pdf.ImagesToPdf;
import vasuki.istanpdf.pdf.PdfMerge;
import vasuki.istanpdf.pdf.PdfReorder;
import vasuki.istanpdf.pdf.PdfToJpegZip;

public final class PdfEngine {
    private final Context context;

    public PdfEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public Bitmap renderFirstPage(Uri uri, int targetWidth) throws Exception {
        File tempPdf = null;
        ParcelFileDescriptor fd = null;
        try {
            if ("file".equals(uri.getScheme())) {
                fd = ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                tempPdf = vasuki.istanpdf.util.ContentFiles.copyUriToCache(context, uri, ".pdf");
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            if (fd == null) {
                throw new IllegalArgumentException("Cannot open PDF file");
            }
            try (PdfRenderer renderer = new PdfRenderer(fd)) {
                if (renderer.getPageCount() > 0) {
                    try (PdfRenderer.Page page = renderer.openPage(0)) {
                        int width = targetWidth;
                        int height = Math.max(1, width * page.getHeight() / Math.max(1, page.getWidth()));
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        return bitmap;
                    }
                }
            }
            return null;
        } finally {
            if (fd != null) {
                try { fd.close(); } catch (Exception ignored) {}
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

    public interface RenderProgressListener {
        void onPageRendered(int current, int total);
    }

    public List<PageItem> renderAllPages(Uri uri, int targetWidth,
                                          AtomicBoolean cancelFlag,
                                          RenderProgressListener listener) throws Exception {
        List<PageItem> rendered = new ArrayList<>();
        File tempPdf = null;
        ParcelFileDescriptor fd = null;
        try {
            if ("file".equals(uri.getScheme())) {
                fd = ParcelFileDescriptor.open(new File(uri.getPath()), ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                tempPdf = vasuki.istanpdf.util.ContentFiles.copyUriToCache(context, uri, ".pdf");
                fd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            if (fd == null) {
                throw new IllegalArgumentException("Cannot open PDF file");
            }
            try (PdfRenderer renderer = new PdfRenderer(fd)) {
                for (int i = 0; i < renderer.getPageCount(); i++) {
                    if ((cancelFlag != null && cancelFlag.get()) || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Cancelled by user");
                    }
                    if (listener != null) {
                        listener.onPageRendered(i + 1, renderer.getPageCount());
                    }
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int width = targetWidth;
                        int height = Math.max(1, width * page.getHeight() / Math.max(1, page.getWidth()));
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        rendered.add(new PageItem(i, bitmap));
                    }
                }
            }
        } catch (Exception e) {
            for (PageItem p : rendered) {
                if (p.thumbnail != null && !p.thumbnail.isRecycled()) p.thumbnail.recycle();
            }
            rendered.clear();
            throw e;
        } finally {
            if (fd != null) {
                try { fd.close(); } catch (Exception ignored) {}
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
        return rendered;
    }

    public void merge(List<Uri> sources, List<Integer> rotations, Uri destination) throws Exception {
        PdfMerge.run(context, sources, rotations, destination);
    }

    public int reorder(Uri source, List<PageItem> pages, Uri destination) throws Exception {
        return PdfReorder.run(context, source, pages, destination);
    }

    public void imagesToPdf(List<Uri> sources, List<PageItem> pages, Uri destination) throws Exception {
        ImagesToPdf.run(context, sources, pages, destination);
    }

    public void pdfToJpegZip(Uri source, Uri destination) throws Exception {
        PdfToJpegZip.run(context, source, destination);
    }
}
