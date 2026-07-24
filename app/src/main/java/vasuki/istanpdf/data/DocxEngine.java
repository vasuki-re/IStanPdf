package vasuki.istanpdf.data;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.List;

import vasuki.istanpdf.docx.DocxServiceBridge;
import vasuki.istanpdf.model.PageItem;

public final class DocxEngine {
    private final Context context;

    public DocxEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public void preLoad() {
        DocxServiceBridge.preLoadEngine(context);
    }

    public File exportToPdf(Uri docxUri) throws Exception {
        return DocxServiceBridge.exportDocxToPdfViaLibreOffice(context, docxUri);
    }

    public void saveEdited(Uri source, List<PageItem> snapshot, Uri destination) throws Exception {
        DocxServiceBridge.saveDocxViaLibreOffice(context, source, snapshot, destination);
    }
}
