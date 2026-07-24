package vasuki.istanpdf.util;

import android.content.Context;
import android.net.Uri;

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

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
