package vasuki.istanpdf.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

public final class DocumentManager {
    private final Context context;

    public DocumentManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getDisplayName(Uri uri) {
        if (uri == null) return "IStanPdf";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1) {
                    String name = cursor.getString(index);
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) return name.substring(0, dot);
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        return "IStanPdf";
    }
}
