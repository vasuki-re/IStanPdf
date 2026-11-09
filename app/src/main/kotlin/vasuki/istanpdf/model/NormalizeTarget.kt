package vasuki.istanpdf.model

import androidx.annotation.StringRes
import vasuki.istanpdf.R

enum class NormalizeTarget(
    @StringRes val labelRes: Int,
    val widthPt: Float,
    val heightPt: Float
) {
    NONE(R.string.normalize_off, 0f, 0f),
    A4(R.string.normalize_a4, 595f, 842f),
    A3(R.string.normalize_a3, 842f, 1191f),
    LETTER(R.string.normalize_letter, 612f, 792f),
    LEGAL(R.string.normalize_legal, 612f, 1008f);

    val isEnabled: Boolean get() = this != NONE

    companion object {
        val presets: List<NormalizeTarget> = listOf(A4, A3, LETTER, LEGAL)
    }
}
