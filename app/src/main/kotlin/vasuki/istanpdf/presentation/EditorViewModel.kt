package vasuki.istanpdf.presentation

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import vasuki.istanpdf.model.NormalizeTarget
import vasuki.istanpdf.model.PageItem
import vasuki.istanpdf.model.PageOrientation
import java.io.File
import java.util.Collections

class EditorViewModel : ViewModel() {
    val pages: MutableList<PageItem> = mutableListOf()
    val pendingUris: MutableList<Uri> = mutableListOf()
    val pendingImageUris: MutableList<Uri> = mutableListOf()
    val tempImageFiles: MutableList<File> = mutableListOf()

    var reorderSource: Uri? = null
    var originalFileName: String? = null
    var pagesAdded: Boolean = false
    var normalizeTarget: NormalizeTarget = NormalizeTarget.NONE
    var pageOrientation: PageOrientation = PageOrientation.AUTO
    var exportCompressMode: ExportCompressMode = ExportCompressMode.OFF
    var exportTargetBytes: Long = 0L


    val thumbCache = object : LruCache<String, Bitmap>(maxCacheBytes(8)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val fullResCache = object : LruCache<String, Bitmap>(maxCacheBytes(6)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val rendersInFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    val renderWaiters: MutableMap<String, MutableList<(Bitmap) -> Unit>> =
        Collections.synchronizedMap(mutableMapOf())

    fun clearPages() {
        thumbCache.evictAll()
        fullResCache.evictAll()
        rendersInFlight.clear()
        renderWaiters.clear()
        pages.clear()
    }

    fun clearPendingUris() = pendingUris.clear()
    fun clearPendingImageUris() = pendingImageUris.clear()

    fun cleanupTempFiles() {
        for (f in tempImageFiles) {
            if (f.exists()) f.delete()
        }
        tempImageFiles.clear()
    }

    fun resetForHome() {
        clearPages()
        clearPendingUris()
        pendingImageUris.clear()
        reorderSource = null
        originalFileName = null
        pagesAdded = false
        normalizeTarget = NormalizeTarget.NONE
        pageOrientation = PageOrientation.AUTO
        exportCompressMode = ExportCompressMode.OFF
        exportTargetBytes = 0L
        cleanupTempFiles()
    }

    override fun onCleared() {
        super.onCleared()
        clearPages()
        cleanupTempFiles()
    }

    private fun maxCacheBytes(fraction: Int) = (Runtime.getRuntime().maxMemory() / fraction).toInt()
}

enum class ExportCompressMode(val label: String) {
    OFF("Off"),
    BALANCED("Balanced"),
    HIGH_COMPRESSION("High"),
    CUSTOM_SIZE("Custom size")
}