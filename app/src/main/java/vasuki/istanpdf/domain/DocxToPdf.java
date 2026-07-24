package vasuki.istanpdf.domain;

import android.net.Uri;

import java.io.File;

import vasuki.istanpdf.data.DocxEngine;

public final class DocxToPdf {
    private final DocxEngine docxEngine;

    public DocxToPdf(DocxEngine docxEngine) {
        this.docxEngine = docxEngine;
    }

    public File execute(Uri docxUri) throws Exception {
        if (docxUri == null) {
            throw new IllegalArgumentException("DOCX URI is required");
        }
        return docxEngine.exportToPdf(docxUri);
    }
}
