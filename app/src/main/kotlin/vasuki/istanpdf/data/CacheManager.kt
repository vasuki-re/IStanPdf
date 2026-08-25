package vasuki.istanpdf.data

import android.content.Context
import java.io.File

class CacheManager(context: Context) {
    private val context = context.applicationContext

    fun pruneStaleCacheFiles() {
        try {
            val cutoff = System.currentTimeMillis() - STALE_CUTOFF_MS
            val stalePrefixes = listOf(
                "istanpdf_", "merged_", "img_", "pdf_to_img_",
                "camera_", "crop_", "photo_pdf_", "compressed_",
                "compress_attempt_", "replaced_", "istan_cam_",
                "removed_uno_", "reordered_uno_"
            )
            context.cacheDir.listFiles()
                ?.filter { f ->
                    !f.isDirectory &&
                            stalePrefixes.any { f.name.startsWith(it) } &&
                            f.lastModified() < cutoff
                }
                ?.forEach { it.delete() }

            val captureDir = File(context.cacheDir, "camera_capture")
            captureDir.listFiles()
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    companion object {
        private const val STALE_CUTOFF_MS = 24L * 60 * 60 * 1000
    }
}
