package vasuki.istanpdf.model

import android.net.Uri
import java.io.File

class PageItem(
    val originalIndex: Int,
    var sourceUri: Uri? = null,
    var renderIndex: Int = originalIndex,
    var displayName: String = "",
    var keep: Boolean = true,
    var rotation: Int = 0,
    var replacementFile: File? = null,
    var uri: Uri? = null
) {
    val cacheKey: String get() {
        val baseId = replacementFile?.name ?: sourceUri?.toString() ?: uri?.toString() ?: "none"
        return "${baseId}_${renderIndex}_r${(rotation % 360 + 360) % 360}"
    }
}