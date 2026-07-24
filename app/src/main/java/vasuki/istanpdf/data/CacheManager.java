package vasuki.istanpdf.data;

import android.content.Context;

import java.io.File;

public final class CacheManager {
    private static final long STALE_CUTOFF_MS = 24L * 60 * 60 * 1000;
    private final Context context;

    public CacheManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void pruneStaleCacheFiles() {
        File cache = context.getCacheDir();
        File[] files = cache.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - STALE_CUTOFF_MS;
        for (File f : files) {
            String n = f.getName();
            if (n.startsWith("istanpdf_") || n.startsWith("merged_")
                    || n.startsWith("img_") || n.startsWith("pdf_to_img_")) {
                if (f.lastModified() < cutoff) f.delete();
            }
        }
    }
}
