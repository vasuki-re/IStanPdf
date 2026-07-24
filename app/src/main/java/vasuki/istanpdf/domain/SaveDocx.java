package vasuki.istanpdf.domain;

import android.net.Uri;

import java.util.List;

import vasuki.istanpdf.data.DocxEngine;
import vasuki.istanpdf.model.PageItem;

public final class SaveDocx {
    private final DocxEngine docxEngine;

    public SaveDocx(DocxEngine docxEngine) {
        this.docxEngine = docxEngine;
    }

    public void execute(Uri source, List<PageItem> snapshot, Uri destination) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("Source DOCX is required");
        }
        docxEngine.saveEdited(source, snapshot, destination);
    }
}
