package vasuki.istanpdf.pdf;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class PdfStore {
    private PdfStore() {
    }

    static OutputStream openDst(Context ctx, Uri dst) throws IOException {
        OutputStream raw = ctx.getContentResolver().openOutputStream(dst, "wt");
        if (raw == null) throw new IOException("Cannot open destination file");
        return new BufferedOutputStream(raw, 64 * 1024);
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
