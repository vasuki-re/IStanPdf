package vasuki.istanpdf.domain;

import android.net.Uri;

import java.util.List;

import vasuki.istanpdf.data.PdfEngine;
import vasuki.istanpdf.model.PageItem;

public final class ImagesToPdf {
    private final PdfEngine pdfEngine;

    public ImagesToPdf(PdfEngine pdfEngine) {
        this.pdfEngine = pdfEngine;
    }

    public void execute(List<Uri> sources, List<PageItem> pages, Uri destination) throws Exception {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one image is required");
        }
        pdfEngine.imagesToPdf(sources, pages, destination);
    }
}
