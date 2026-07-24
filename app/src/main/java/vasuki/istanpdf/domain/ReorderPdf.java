package vasuki.istanpdf.domain;

import android.net.Uri;

import java.util.List;

import vasuki.istanpdf.data.PdfEngine;
import vasuki.istanpdf.model.PageItem;

public final class ReorderPdf {
    private final PdfEngine pdfEngine;

    public ReorderPdf(PdfEngine pdfEngine) {
        this.pdfEngine = pdfEngine;
    }

    public int execute(Uri source, List<PageItem> pages, Uri destination) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("Source PDF is required");
        }
        return pdfEngine.reorder(source, pages, destination);
    }
}
