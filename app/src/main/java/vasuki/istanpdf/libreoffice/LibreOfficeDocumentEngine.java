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

    public static void saveDocx(Context context, File input, Uri destination) throws Exception {
        requireNonEmptyDocx(input);
        File output = null;
        try {
            output = File.createTempFile("istanpdf_libreoffice_", ".docx", context.getCacheDir());
            saveAs(context, input, output, "docx");
            
            if (!output.exists() || output.length() == 0) {
                throw new IllegalStateException("LibreOffice failed to generate output DOCX");
            }
            ContentFiles.copyFileToUri(context, output, destination);
        } finally {
            if (output != null && output.exists()) {
                output.delete();
            }
        }
    }

    private static void requireNonEmptyDocx(File input) throws IOException {
        if (input == null || !input.exists() || input.length() == 0L) {
            throw new IOException("Corrupt DOCX file");
        }
    }

    private static void saveAs(Context context, File input, File output, String format) throws Exception {
        initialize(context);

        Office office = new Office(LibreOfficeKit.getLibreOfficeKitHandle());
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
}
