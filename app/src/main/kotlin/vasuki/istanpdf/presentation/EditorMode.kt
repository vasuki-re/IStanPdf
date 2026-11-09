package vasuki.istanpdf.presentation

import androidx.annotation.StringRes
import vasuki.istanpdf.R

enum class AddTargets {
    PDF_ONLY,
    IMAGES_AND_PDF,
    DOCX_IMAGES_AND_PDF
}

enum class EditorMode(
    @StringRes val titleRes: Int,
    @StringRes val saveLabelRes: Int,
    val docxExport: Boolean,
    val allowReorder: Boolean,
    val showNormalization: Boolean,
    val showSelectionControls: Boolean,
    val showCompression: Boolean,
    val imageAdapter: Boolean,
    val syncPendingUris: Boolean,
    val hideRotate: Boolean,
    val hideDrag: Boolean,
    val showAddCard: Boolean,
    @StringRes val addLabelRes: Int,
    val addTargets: AddTargets
) {
    MERGE_PDF(
        titleRes = R.string.editor_title_merge,
        saveLabelRes = R.string.editor_save_merge,
        docxExport = false,
        allowReorder = true,
        showNormalization = false,
        showSelectionControls = false,
        showCompression = false,
        imageAdapter = true,
        syncPendingUris = true,
        hideRotate = true,
        hideDrag = false,
        showAddCard = true,
        addLabelRes = R.string.editor_add_pdf,
        addTargets = AddTargets.PDF_ONLY
    ),
    IMAGES_TO_PDF(
        titleRes = R.string.editor_title_images,
        saveLabelRes = R.string.action_save_pdf,
        docxExport = false,
        allowReorder = true,
        showNormalization = true,
        showSelectionControls = false,
        showCompression = true,
        imageAdapter = true,
        syncPendingUris = false,
        hideRotate = false,
        hideDrag = false,
        showAddCard = true,
        addLabelRes = R.string.editor_add_images_pdf,
        addTargets = AddTargets.IMAGES_AND_PDF
    ),
    MODIFY_PDF(
        titleRes = R.string.editor_title_modify,
        saveLabelRes = R.string.action_save_pdf,
        docxExport = false,
        allowReorder = true,
        showNormalization = true,
        showSelectionControls = true,
        showCompression = true,
        imageAdapter = true,
        syncPendingUris = false,
        hideRotate = false,
        hideDrag = false,
        showAddCard = true,
        addLabelRes = R.string.editor_add_images_pdf,
        addTargets = AddTargets.IMAGES_AND_PDF
    ),
    DOCX_REORDER(
        titleRes = R.string.editor_title_reorder,
        saveLabelRes = R.string.action_save_pdf,
        docxExport = false,
        allowReorder = true,
        showNormalization = true,
        showSelectionControls = true,
        showCompression = true,
        imageAdapter = true,
        syncPendingUris = false,
        hideRotate = false,
        hideDrag = false,
        showAddCard = true,
        addLabelRes = R.string.editor_add_docx_pdf,
        addTargets = AddTargets.DOCX_IMAGES_AND_PDF
    ),
    DOCX_REMOVE(
        titleRes = R.string.editor_title_remove,
        saveLabelRes = R.string.action_save_docx,
        docxExport = true,
        allowReorder = false,
        showNormalization = false,
        showSelectionControls = true,
        showCompression = false,
        imageAdapter = true,
        syncPendingUris = false,
        hideRotate = true,
        hideDrag = true,
        showAddCard = false,
        addLabelRes = R.string.editor_add_images_pdf,
        addTargets = AddTargets.IMAGES_AND_PDF
    )
}
