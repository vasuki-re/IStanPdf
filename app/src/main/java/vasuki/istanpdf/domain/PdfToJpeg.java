package vasuki.istanpdf.domain;

import android.net.Uri;

import vasuki.istanpdf.data.PdfEngine;

public final class PdfToJpeg {
    private final PdfEngine pdfEngine;

    public PdfToJpeg(PdfEngine pdfEngine) {
        this.pdfEngine = pdfEngine;
    }

    public void execute(Uri source, Uri destination) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("Source PDF is required");
        }
        pdfEngine.pdfToJpegZip(source, destination);
    }
}
