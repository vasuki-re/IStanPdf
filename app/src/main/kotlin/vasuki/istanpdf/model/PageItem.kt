package vasuki.istanpdf.model

import android.graphics.Bitmap

class PageItem(
    val originalIndex: Int,
    var thumbnail: Bitmap,
    var displayName: String = "",
    var keep: Boolean = true,
    var rotation: Int = 0
)
