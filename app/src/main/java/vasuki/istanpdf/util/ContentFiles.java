package vasuki.istanpdf.util;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ContentFiles {
    private static final int BUFFER_SIZE = 64 * 1024;

    private ContentFiles() {
    }

    public static File copyUriToCache(Context context, Uri uri, String suffix) throws IOException {
        File file = File.createTempFile("istanpdf_", suffix, context.getCacheDir());
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(file)) {
            if (input == null) {
                throw new IOException("Cannot open selected file");
            }
            copy(input, output);
        }
        return file;
    }

    public static void copyFileToUri(Context context, File file, Uri uri) throws IOException {
        try (InputStream input = new java.io.FileInputStream(file);
             OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IOException("Cannot open destination file");
            }
            copy(input, output);
        }
    }

    public static String displayName(Context context, Uri uri) {
        try (android.database.Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return "Selected file";
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
