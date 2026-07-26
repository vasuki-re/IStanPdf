package vasuki.istanpdf.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import vasuki.istanpdf.model.PageItem
import java.io.File

class EditorViewModel : ViewModel() {
    val pages: MutableList<PageItem> = mutableListOf()
    val pendingUris: MutableList<Uri> = mutableListOf()
    val pendingImageUris: MutableList<Uri> = mutableListOf()
    val tempImageFiles: MutableList<File> = mutableListOf()

    var reorderSource: Uri? = null
    var originalFileName: String? = null
    var pagesAdded: Boolean = false

    fun clearPages() {
        for (p in pages) {
            if (!p.thumbnail.isRecycled) p.thumbnail.recycle()
        }
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
}
