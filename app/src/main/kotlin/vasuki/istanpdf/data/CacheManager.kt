package vasuki.istanpdf.data

import android.content.Context
import java.io.File

class CacheManager(context: Context) {
    private val context = context.applicationContext

    fun pruneStaleCacheFiles() {
        val cutoff = System.currentTimeMillis() - STALE_CUTOFF_MS
        context.cacheDir.listFiles()
            ?.filter { f ->
                val n = f.name
                (n.startsWith("istanpdf_") || n.startsWith("merged_") ||
                        n.startsWith("img_") || n.startsWith("pdf_to_img_")) &&
                        f.lastModified() < cutoff
            }
            ?.forEach { it.delete() }
    }

    companion object {
        private const val STALE_CUTOFF_MS = 24L * 60 * 60 * 1000
    }
}
