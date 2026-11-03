package vasuki.istanpdf.presentation

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import vasuki.istanpdf.AppFont
import vasuki.istanpdf.R
import vasuki.istanpdf.ThemePrefs
import vasuki.istanpdf.model.NormalizeTarget
import vasuki.istanpdf.model.PageOrientation

class DocumentOptionsSheet(
    private val activity: Activity,
    private val showPageSize: Boolean,
    private val showOrientation: Boolean,
    private val showEditRange: Boolean,
    private val showCompression: Boolean = false,
    normalization: NormalizeTarget,
    orientation: PageOrientation,
    compressMode: ExportCompressMode = ExportCompressMode.OFF,
    targetBytes: Long = 0L,
    private val sourceBytes: Long = 0L,
    private val onNormalization: (NormalizeTarget) -> Unit,
    private val onOrientation: (PageOrientation) -> Unit,
    private val onCompressMode: (ExportCompressMode, Long) -> Unit = { _, _ -> },
    private val onEditRange: () -> Unit
) {
    private var selectedNormalization = normalization
    private var selectedOrientation = orientation
    private var selectedCompressMode = compressMode
    private var currentTargetBytes = targetBytes
    private var activeDropdown: RoundedDropdown? = null
    fun show() {
        val dialog = BottomSheetStyle.dialog(activity)
        val scroll = ScrollView(activity)
        scroll.isFillViewport = true
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(24))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val title = text("Document Options", 20, R.color.istan_text, true)
        title.setPadding(dp(24), dp(8), dp(24), dp(16))
        root.addView(title)

        if (showPageSize) {
            val targets = listOf(NormalizeTarget.NONE) + NormalizeTarget.presets
            val setting = addSettingRow(
                root,
                R.drawable.crop_free_24px,
                "Page Size",
                selectedNormalization.label
            )
            val open = {
                activeDropdown?.dismiss()
                activeDropdown = RoundedDropdown(
                    activity = activity,
                    anchor = setting.pill,
                    options = targets.map { it.label },
                    selectedIndex = targets.indexOf(selectedNormalization),
                    onSelect = { index ->
                        val target = targets[index]
                        selectedNormalization = target
                        setting.value.text = "${target.label} \u25BE"
                        setting.pill.contentDescription = "Page Size: ${target.label}"
                        onNormalization(target)
                    }
                ).also { it.show() }
            }
            setting.row.setOnClickListener { open() }
            setting.pill.setOnClickListener { open() }
        }

        if (showOrientation) {
            val options = PageOrientation.values().toList()
            val setting = addSettingRow(
                root,
                R.drawable.crop_rotate_24px,
                "Orientation",
                selectedOrientation.label
            )
            val open = {
                activeDropdown?.dismiss()
                activeDropdown = RoundedDropdown(
                    activity = activity,
                    anchor = setting.pill,
                    options = options.map { it.label },
                    selectedIndex = options.indexOf(selectedOrientation),
                    onSelect = { index ->
                        val orientation = options[index]
                        selectedOrientation = orientation
                        setting.value.text = "${orientation.label} \u25BE"
                        setting.pill.contentDescription = "Orientation: ${orientation.label}"
                        onOrientation(orientation)
                    }
                ).also { it.show() }
            }
            setting.row.setOnClickListener { open() }
            setting.pill.setOnClickListener { open() }
        }

        if (showCompression) {
            val options = ExportCompressMode.values().toList()
            val formatModeLabel = { mode: ExportCompressMode ->
                if (mode == ExportCompressMode.CUSTOM_SIZE && currentTargetBytes > 0) {
                    val kb = currentTargetBytes / 1024L
                    val sizeStr = if (kb >= 1024) "%.1f MB".format(kb / 1024f) else "$kb KB"
                    "Custom size ($sizeStr)"
                } else {
                    mode.label
                }
            }

            val formatPillText = {
                if (selectedCompressMode == ExportCompressMode.CUSTOM_SIZE && currentTargetBytes > 0) {
                    val kb = currentTargetBytes / 1024L
                    val sizeStr = if (kb >= 1024) "%.1f MB".format(kb / 1024f) else "$kb KB"
                    "$sizeStr \u25BE"
                } else {
                    "${selectedCompressMode.label} \u25BE"
                }
            }

            val setting = addSettingRow(
                root,
                R.drawable.compress_pdf_24px,
                "Compress Output",
                formatPillText().removeSuffix(" \u25BE")
            )
            setting.value.text = formatPillText()

            val open = {
                activeDropdown?.dismiss()
                activeDropdown = RoundedDropdown(
                    activity = activity,
                    anchor = setting.pill,
                    options = options.map { formatModeLabel(it) },
                    selectedIndex = options.indexOf(selectedCompressMode),
                    onSelect = { index ->
                        val mode = options[index]
                        if (mode == ExportCompressMode.CUSTOM_SIZE) {
                            showCustomSizeInput(currentTargetBytes) { newTargetBytes ->
                                selectedCompressMode = mode
                                currentTargetBytes = newTargetBytes
                                setting.value.text = formatPillText()
                                setting.pill.contentDescription = "Compress Output: ${formatModeLabel(mode)}"
                                onCompressMode(mode, newTargetBytes)
                            }
                        } else {
                            selectedCompressMode = mode
                            setting.value.text = formatPillText()
                            setting.pill.contentDescription = "Compress Output: ${mode.label}"
                            onCompressMode(mode, 0L)
                        }
                    }
                ).also { it.show() }
            }
            setting.row.setOnClickListener { open() }
            setting.pill.setOnClickListener { open() }
        }

        if (showEditRange) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(24), 0)
                minimumHeight = dp(52)
                isFocusable = true
                setBackgroundResource(rowBackground())
                setOnClickListener {
                    activeDropdown?.dismiss()
                    activeDropdown = null
                    dialog.dismiss()
                    onEditRange()
                }
            }

            val icon = ImageView(activity)
            icon.setImageResource(R.drawable.edit_minimal_24px)
            icon.setColorFilter(color(R.color.istan_text_muted))
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            row.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))

            val label = text("Edit Range", 16, R.color.istan_text, false)
            label.setPadding(dp(16), 0, 0, 0)
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val chevron = ImageView(activity)
            chevron.setImageResource(R.drawable.chevron_right_24px)
            chevron.setColorFilter(color(R.color.istan_text_muted))
            chevron.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            row.addView(chevron, LinearLayout.LayoutParams(dp(22), dp(22)))

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            )
            params.topMargin = dp(8)
            root.addView(row, params)
        }

        dialog.setOnDismissListener {
            activeDropdown?.dismiss()
            activeDropdown = null
        }
        dialog.setContentView(scroll)
        dialog.show()
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        BottomSheetStyle.apply(activity, dialog, sheet)
        BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun addSettingRow(
        root: LinearLayout,
        iconRes: Int,
        label: String,
        value: String
    ): SettingRow {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            minimumHeight = dp(56)
            isFocusable = true
            setBackgroundResource(rowBackground())
        }

        val icon = ImageView(activity)
        icon.setImageResource(iconRes)
        icon.setColorFilter(color(R.color.istan_olive))
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))

        val labelView = text(label, 16, R.color.istan_text, false)
        labelView.setPadding(dp(16), 0, dp(12), 0)
        row.addView(labelView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val pill = MaterialCardView(activity).apply {
            ShadcnDrawables.applyButton(this, ShadcnDrawables.ButtonVariant.OUTLINE, ShadcnDrawables.BUTTON_RADIUS)
            minimumHeight = dp(48)
            minimumWidth = dp(96)
            isClickable = true
            isFocusable = true
            contentDescription = "$label: $value"
        }

        val valueView = TextView(activity).apply {
            text = "$value \u25BE"
            textSize = 14f
            typeface = AppFont.medium
            setTextColor(ShadcnDrawables.text(activity))
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        pill.addView(
            valueView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val pillParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        pillParams.gravity = Gravity.CENTER_VERTICAL
        row.addView(pill, pillParams)

        root.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        )

        return SettingRow(row, pill, valueView)
    }

    private fun rowBackground(): Int {
        val typed = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, typed, true)
        return typed.resourceId
    }

    private fun text(value: String, sp: Int, colorRes: Int, bold: Boolean): TextView {
        return TextView(activity).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(color(colorRes))
            typeface = if (bold) AppFont.semiBold else AppFont.regular
            includeFontPadding = true
        }
    }

    private fun color(colorRes: Int): Int = ThemePrefs.resolveColor(activity, colorRes)

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)

    private data class SettingRow(
        val row: LinearLayout,
        val pill: MaterialCardView,
        val value: TextView
    )
    private fun showCustomSizeInput(initialBytes: Long, onConfirm: (Long) -> Unit) {
        val dialog = android.app.Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val dialogRoot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ShadcnDrawables.card(activity, ShadcnDrawables.DIALOG_RADIUS)
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }
        val titleView = text("Target Size", 20, R.color.istan_text, true)
        dialogRoot.addView(titleView)

        val sourceKb = if (sourceBytes > 0) sourceBytes / 1024L else 0L
        if (sourceKb > 0) {
            val srcLabel = if (sourceKb >= 1024) "%.1f MB".format(sourceKb / 1024f) else "$sourceKb KB"
            val currentSizeView = text("Current size: $srcLabel", 14, R.color.istan_text_muted, false)
            currentSizeView.setPadding(0, dp(6), 0, dp(14))
            dialogRoot.addView(currentSizeView)
        } else {
            val emptySpacer = View(activity)
            dialogRoot.addView(emptySpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)))
        }

        val input = android.widget.EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Target size in KB"
            textSize = 15f
            typeface = AppFont.regular
            setTextColor(color(R.color.istan_text))
            setHintTextColor(color(R.color.istan_text_muted))
            background = ShadcnDrawables.input(activity, ShadcnDrawables.BUTTON_RADIUS)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (initialBytes > 0) {
                val initialKb = initialBytes / 1024L
                setText(initialKb.toString())
                setSelection(text.length)
            }
        }
        dialogRoot.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val actionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }

        val cancel = text("Cancel", 14, R.color.istan_text_muted, true).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { dialog.dismiss() }
        }

        val apply = text("Apply", 14, R.color.istan_olive, true).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                val kb = input.text.toString().toLongOrNull()
                if (kb == null || kb <= 0) {
                    android.widget.Toast.makeText(activity, "Please enter a valid size in KB.", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (sourceKb > 0) {
                    val capKb = sourceKb * 95L / 100L
                    val srcLabel = if (sourceKb >= 1024) "%.1f MB".format(sourceKb / 1024f) else "$sourceKb KB"
                    if (kb > capKb) {
                        android.widget.Toast.makeText(activity, "Target must not exceed 95% of the source file ($srcLabel).", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                dialog.dismiss()
                onConfirm(kb * 1024L)
            }
        }

        actionsRow.addView(cancel)
        actionsRow.addView(apply)
        dialogRoot.addView(actionsRow)
        dialog.setContentView(dialogRoot)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.85f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.setOnShowListener {
            input.requestFocus()
            val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }
}
