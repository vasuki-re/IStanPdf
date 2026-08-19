package vasuki.istanpdf.presentation

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import vasuki.istanpdf.model.PageItem
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

    val thumbCache = object : LruCache<String, Bitmap>(maxCacheBytes(8)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val fullResCache = object : LruCache<String, Bitmap>(maxCacheBytes(6)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val rendersInFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun clearPages() {
        thumbCache.evictAll()
        fullResCache.evictAll()
        rendersInFlight.clear()
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
        cleanupTempFiles()
    }

    override fun onCleared() {
        super.onCleared()
        clearPages()
        cleanupTempFiles()
    }

    private fun maxCacheBytes(fraction: Int) = (Runtime.getRuntime().maxMemory() / fraction).toInt()
}