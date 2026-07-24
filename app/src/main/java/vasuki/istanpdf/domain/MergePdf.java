package vasuki.istanpdf.domain;

import android.net.Uri;

import java.util.List;

import vasuki.istanpdf.data.PdfEngine;

public final class MergePdf {
    private final PdfEngine pdfEngine;

    public MergePdf(PdfEngine pdfEngine) {
        this.pdfEngine = pdfEngine;
    }

    public void execute(List<Uri> sources, List<Integer> rotations, Uri destination) throws Exception {
        if (sources == null || sources.size() < 2) {
            throw new IllegalArgumentException("At least 2 PDFs are required to merge");
        }
        pdfEngine.merge(sources, rotations, destination);
    }
}
