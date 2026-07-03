package vasuki.istanpdf.libreoffice;

import android.content.Context;
import android.net.Uri;

import org.libreoffice.kit.Document;
import org.libreoffice.kit.LibreOfficeKit;
import org.libreoffice.kit.Office;

import java.io.File;
import java.io.IOException;

import vasuki.istanpdf.util.ContentFiles;

public final class LibreOfficeDocumentEngine {
    private LibreOfficeDocumentEngine() {
    }

    public static File exportDocxToPdf(Context context, Uri docxUri) throws Exception {
        File input = null;
        File output = null;
        try {
            input = ContentFiles.copyUriToCache(context, docxUri, ".docx");
            requireNonEmptyDocx(input);
            output = File.createTempFile("istanpdf_docx_preview_", ".pdf", context.getCacheDir());
            saveAs(context, input, output, "pdf");
            
            if (!output.exists() || output.length() == 0) {
                throw new IllegalStateException("LibreOffice failed to generate output PDF");
            }
            return output;
        } finally {
            if (input != null && input.exists()) {
                input.delete();
            }
        }
    }

    private static void requireNonEmptyDocx(File input) throws IOException {
        if (input == null || !input.exists() || input.length() == 0L) {
            throw new IOException("Corrupt DOCX file");
        }
    }

    private static void saveAs(Context context, File input, File output, String format) throws Exception {
        Office office = getOffice(context);
        Document document = null;
        try {
            document = office.documentLoad(Uri.fromFile(input).toString());
            if (document == null) {
                throw new IllegalStateException("LibreOffice could not open the document");
            }
            document.saveAs(Uri.fromFile(output).toString(), format, null);
        } finally {
            if (document != null) {
                document.destroy();
            }
        }
    }

    private static boolean isInitialized = false;
    private static Office cachedOffice;

    public static synchronized void initialize(Context context) {
        if (isInitialized) return;
        try {
            LibreOfficeRuntime.prepare(context);
        } catch (Exception exception) {
            throw new IllegalStateException("LibreOffice runtime setup failed: " + exception.getMessage(), exception);
        }
        LibreOfficeKit.putenv("SAL_LOG=+WARN");
        LibreOfficeKit.putenv("SAL_LOK_OPTIONS=compact_fonts");
        LibreOfficeKit.init(context);
        if (LibreOfficeKit.getLibreOfficeKitHandle() == null) {
            throw new IllegalStateException("LibreOffice native initialization failed");
        }
        isInitialized = true;
    }

    public static synchronized Office getOffice(Context context) {
        initialize(context);
        if (cachedOffice == null) {
            cachedOffice = new Office(LibreOfficeKit.getLibreOfficeKitHandle());
        }
        return cachedOffice;
    }
}
