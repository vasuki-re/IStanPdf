package vasuki.istanpdf.pdf;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class PdfStore {
    private PdfStore() {
    }

    static void init(Context ctx) {
        PDFBoxResourceLoader.init(ctx);
    }

    static void save(Context ctx, PDDocument doc, Uri dst) throws Exception {
        try (OutputStream out = ctx.getContentResolver().openOutputStream(dst, "wt")) {
            if (out == null) {
                throw new IllegalStateException("Cannot open output file");
            }
            doc.save(out);
        }
    }

    static String base(Context ctx, Uri uri) {
        if (uri == null) {
            return "Document";
        }
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx != -1) {
                    String name = c.getString(idx);
                    int dot = name.lastIndexOf('.');
                    return dot > 0 ? name.substring(0, dot) : name;
                }
            }
        }
        return "Document";
    }

    static byte[] bytes(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] data = new byte[16384];
        int n;
        while ((n = in.read(data, 0, data.length)) != -1) {
            buf.write(data, 0, n);
        }
        return buf.toByteArray();
    }
}
