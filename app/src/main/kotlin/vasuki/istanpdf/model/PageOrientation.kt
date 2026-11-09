package vasuki.istanpdf.model

import androidx.annotation.StringRes
import vasuki.istanpdf.R

enum class PageOrientation(@StringRes val labelRes: Int) {
    AUTO(R.string.orientation_auto),
    PORTRAIT(R.string.orientation_portrait),
    LANDSCAPE(R.string.orientation_landscape)
}
