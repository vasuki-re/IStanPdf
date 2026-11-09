package vasuki.istanpdf.presentation

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import vasuki.istanpdf.AppFont
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import vasuki.istanpdf.R
import vasuki.istanpdf.ThemePrefs
import vasuki.istanpdf.model.NormalizeTarget
import vasuki.istanpdf.model.PageItem
import vasuki.istanpdf.model.PageOrientation

class EditorViewBuilder(
    private val activity: Activity
) {

    interface EditorActions {
        fun onBack()
        fun onSave(mode: EditorMode)
        fun onAddItems(mode: EditorMode)
        fun onCropPage(position: Int, onCropped: Runnable?)
        fun onShowCustomDialog(title: String, content: View, negativeStr: String?, negativeAction: Runnable?, positiveStr: String?, positiveAction: Runnable?)
        fun toast(message: String)
        fun getPages(): List<PageItem>
        fun getPendingUris(): List<Uri>
        fun isPagesAdded(): Boolean
        fun getThumbnail(item: PageItem): Bitmap?
        fun requestThumbnail(item: PageItem, onReady: (Bitmap) -> Unit)
        fun getFullRes(item: PageItem): Bitmap?
        fun requestFullRes(item: PageItem, onReady: (Bitmap) -> Unit)
        fun rotatePage(item: PageItem, degrees: Int, onReady: (thumb: Bitmap, fullRes: Bitmap?) -> Unit)
        fun getNormalization(): NormalizeTarget
        fun setNormalization(target: NormalizeTarget)
        fun getOrientation(): PageOrientation
        fun setOrientation(orientation: PageOrientation)
        fun getExportCompressMode(): ExportCompressMode
        fun getExportTargetBytes(): Long
        fun getSourceBytes(): Long
        fun setExportCompressMode(mode: ExportCompressMode, targetBytes: Long)
    }

    var status: TextView? = null
        private set
    var statusIndicator: ImageView? = null
        private set
    var statusCard: View? = null
        private set
    var pageList: RecyclerView? = null
        private set

    fun build(mode: EditorMode, actions: EditorActions): View {
        val outer = LinearLayout(activity)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(color(R.color.istan_background))

        val titleRow = LinearLayout(activity)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        titleRow.setPadding(dp(4), dp(24), dp(20), dp(8))
        outer.addView(titleRow)

        val backArrow = ImageView(activity).apply {
            setImageResource(R.drawable.arrow_back_24px)
            setColorFilter(color(R.color.istan_text))
            contentDescription = activity.getString(R.string.action_navigate_up)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { actions.onBack() }
        }
        titleRow.addView(backArrow, LinearLayout.LayoutParams(dp(48), dp(48)))

        val title = text(activity.getString(mode.titleRes), 28, R.color.istan_text, true).apply {
            includeFontPadding = false
            typeface = AppFont.medium
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })


        val header = LinearLayout(activity)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(22), dp(16), dp(22), 0)
        outer.addView(header)

        val statusCard = LinearLayout(activity)
        statusCard.orientation = LinearLayout.HORIZONTAL
        statusCard.gravity = Gravity.CENTER
        statusCard.isBaselineAligned = false
        statusCard.setPadding(dp(12), dp(6), dp(12), dp(6))
        statusCard.background = ShadcnDrawables.badge(activity, ShadcnDrawables.PILL_RADIUS)

        statusIndicator = ImageView(activity)
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(color(R.color.istan_olive))
        dot.setSize(dp(12), dp(12))
        statusIndicator!!.setImageDrawable(dot)
        statusCard.addView(statusIndicator)

        status = text(activity.getString(R.string.status_ready), 14, R.color.istan_olive, false)
        status!!.maxLines = 1
        status!!.gravity = Gravity.CENTER
        status!!.setPadding(dp(8), 0, dp(4), 0)
        statusCard.addView(status)

        val scParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scParams.setMargins(0, 0, 0, dp(12))
        header.addView(statusCard, scParams)
        statusCard.visibility = View.GONE
        this.statusCard = statusCard

        val showNormalization = mode.showNormalization
        val showSelectionControls = mode.showSelectionControls

        val selectedCountView = if (showSelectionControls) {
            TextView(activity).apply {
                textSize = 13f
                typeface = AppFont.regular
                gravity = Gravity.START
                setTextColor(color(R.color.istan_text))
                setPadding(0, dp(4), 0, dp(8))
            }
        } else null

        fun refreshCount() {
            val counter = selectedCountView ?: return
            val pages = actions.getPages()
            var count = 0
            for (p in pages) if (p.keep) count++
            counter.text = activity.getString(R.string.editor_selected_count, count, pages.size)
        }

        selectedCountView?.let {
            header.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val recycler = RecyclerView(activity)
        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.setPadding(dp(8), dp(8), dp(8), dp(8))
        recycler.clipToPadding = false
        val listParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        outer.addView(recycler, listParams)
        pageList = recycler

        fun showRangeDialog() {
                val input = EditText(activity)
                input.setTextColor(color(R.color.istan_text))
                input.hint = activity.getString(R.string.editor_range_hint)
                input.setHintTextColor(color(R.color.istan_text_muted))
                input.setPadding(dp(16), dp(16), dp(16), dp(16))
                input.background = ShadcnDrawables.input(activity, ShadcnDrawables.BUTTON_RADIUS)

                val accentColor = color(R.color.istan_olive)
                input.highlightColor = Color.argb(76, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    input.textCursorDrawable?.let { cursor -> cursor.setTint(accentColor); input.textCursorDrawable = cursor }
                    input.textSelectHandle?.let { handle -> handle.setTint(accentColor); input.setTextSelectHandle(handle) }
                    input.textSelectHandleLeft?.let { handle -> handle.setTint(accentColor); input.setTextSelectHandleLeft(handle) }
                    input.textSelectHandleRight?.let { handle -> handle.setTint(accentColor); input.setTextSelectHandleRight(handle) }
                }

                val pages = actions.getPages()
                val sb = StringBuilder()
                var start = -1
                var end = -1
                for (p in pages) {
                    if (!p.keep) continue
                    val num = p.originalIndex + 1
                    if (start == -1) {
                        start = num
                        end = num
                    } else if (num == end + 1) {
                        end = num
                    } else {
                        if (start == end) sb.append(start).append(",")
                        else sb.append(start).append("-").append(end).append(",")
                        start = num
                        end = num
                    }
                }
                if (start != -1) {
                    if (start == end) sb.append(start)
                    else sb.append(start).append("-").append(end)
                } else if (sb.isNotEmpty() && sb[sb.length - 1] == ',') {
                    sb.setLength(sb.length - 1)
                }
                input.setText(sb.toString())

                actions.onShowCustomDialog(
                    activity.getString(R.string.editor_edit_range),
                    input,
                    activity.getString(R.string.action_cancel),
                    null,
                    activity.getString(R.string.action_apply)
                ) {
                    val rangeStr = input.text.toString()
                    val currentPages = actions.getPages()
                    val pendingUris = actions.getPendingUris()
                    if (rangeStr.trim().isEmpty()) {
                        for (p in currentPages) p.keep = false
                    } else {
                        try {
                            val pagesToKeep = mutableListOf<Int>()
                            val parts = rangeStr.split(",")
                            for (part in parts) {
                                val p = part.trim()
                                if (p.isEmpty()) continue
                                if (p.contains("-")) {
                                    val bounds = p.split("-")
                                    if (bounds.size == 2) {
                                        val startIdx = bounds[0].trim().toInt()
                                        val endIdx = bounds[1].trim().toInt()
                                        if (startIdx <= endIdx) {
                                            for (k in startIdx..endIdx) pagesToKeep.add(k - 1)
                                        } else {
                                            for (k in startIdx downTo endIdx) pagesToKeep.add(k - 1)
                                        }
                                    }
                                } else {
                                    pagesToKeep.add(p.toInt() - 1)
                                }
                            }

                            val newPages = mutableListOf<PageItem>()
                            val newUris = mutableListOf<Uri>()
                            val processedIndices = mutableListOf<Int>()

                            for (originalIdx in pagesToKeep) {
                                if (processedIndices.contains(originalIdx)) continue
                                for (i in currentPages.indices) {
                                    val p = currentPages[i]
                                    if (p.originalIndex == originalIdx) {
                                        p.keep = true
                                        newPages.add(p)
                                        if ((mode == EditorMode.MERGE_PDF || mode == EditorMode.IMAGES_TO_PDF) && i < pendingUris.size) {
                                            newUris.add(pendingUris[i])
                                        }
                                        processedIndices.add(originalIdx)
                                        break
                                    }
                                }
                            }

                            for (i in currentPages.indices) {
                                val p = currentPages[i]
                                if (!processedIndices.contains(p.originalIndex)) {
                                    p.keep = false
                                    newPages.add(p)
                                    if ((mode == EditorMode.MERGE_PDF || mode == EditorMode.IMAGES_TO_PDF) && i < pendingUris.size) {
                                        newUris.add(pendingUris[i])
                                    }
                                }
                            }

                            (currentPages as MutableList<PageItem>).clear()
                            currentPages.addAll(newPages)

                            if (mode == EditorMode.MERGE_PDF || mode == EditorMode.IMAGES_TO_PDF) {
                                (pendingUris as MutableList<Uri>).clear()
                                pendingUris.addAll(newUris)
                            }

                        } catch (ignored: Exception) {
                            Toast.makeText(activity, activity.getString(R.string.error_invalid_range), Toast.LENGTH_SHORT).show()
                            return@onShowCustomDialog
                        }
                    }
                    recycler.adapter?.notifyDataSetChanged()
                    refreshCount()
                }
        }

        val hideRotate = mode.hideRotate
        val hideDrag = mode.hideDrag
        val isImg = mode.imageAdapter

        val isMerge = mode == EditorMode.MERGE_PDF
        val adapter = PagesAdapter(actions, {
            refreshCount()
        }, isImg, hideRotate, hideDrag, isMerge)
        recycler.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { refreshCount() }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) { refreshCount() }
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { refreshCount() }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { refreshCount() }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { refreshCount() }
        })
        refreshCount()

        if (showNormalization || showSelectionControls) {
            val tuneButton = ImageView(activity)
            tuneButton.setImageResource(R.drawable.tune_24px)
            tuneButton.setColorFilter(color(R.color.istan_text))
            tuneButton.contentDescription = activity.getString(R.string.editor_document_options)
            tuneButton.setPadding(dp(12), dp(12), dp(12), dp(12))
            val ripple = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            tuneButton.setBackgroundResource(ripple.resourceId)
            tuneButton.setOnClickListener {
                val showComp = mode.showCompression
                DocumentOptionsSheet(
                    activity = activity,
                    showPageSize = showNormalization,
                    showOrientation = showNormalization,
                    showEditRange = showSelectionControls,
                    showCompression = showComp,
                    normalization = actions.getNormalization(),
                    orientation = actions.getOrientation(),
                    compressMode = actions.getExportCompressMode(),
                    targetBytes = actions.getExportTargetBytes(),
                    sourceBytes = actions.getSourceBytes(),
                    onNormalization = { actions.setNormalization(it) },
                    onOrientation = { actions.setOrientation(it) },
                    onCompressMode = { mode, bytes -> actions.setExportCompressMode(mode, bytes) },
                    onEditRange = { showRangeDialog() }
                ).show()
            }
            titleRow.setPadding(dp(4), dp(24), dp(8), dp(8))
            titleRow.addView(tuneButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        if (mode.allowReorder) {
            val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    if (viewHolder.itemViewType == 1) return 0
                    return super.getDragDirs(recyclerView, viewHolder)
                }

                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    if (target.itemViewType == 1) return false
                    val from = viewHolder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                    val pagesMutable = actions.getPages() as MutableList<PageItem>
                    val item = pagesMutable.removeAt(from)
                    pagesMutable.add(to, item)

                    if (mode.syncPendingUris) {
                        val pendingUris = actions.getPendingUris() as MutableList<Uri>
                        val u = pendingUris.removeAt(from)
                        pendingUris.add(to, u)
                    }

                    adapter.notifyItemMoved(from, to)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            })
            touchHelper.attachToRecyclerView(recycler)
        }

        val footer = LinearLayout(activity)
        footer.orientation = LinearLayout.VERTICAL
        footer.setPadding(dp(22), dp(8), dp(22), dp(18))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(footer) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            v.setPadding(dp(22), dp(8), dp(22), dp(18) + bars.bottom)
            insets
        }

        if (mode.showAddCard) {
            val addCard = LinearLayout(activity)
            addCard.orientation = LinearLayout.HORIZONTAL
            addCard.gravity = Gravity.CENTER
            addCard.minimumHeight = dp(48)
            addCard.setPadding(dp(16), dp(12), dp(16), dp(12))
            addCard.background = ShadcnDrawables.dashedCard(activity, ShadcnDrawables.CARD_RADIUS)
            val ripple = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            if (ripple.resourceId != 0) {
                addCard.foreground = activity.getDrawable(ripple.resourceId)
            }

            val circleBg = GradientDrawable()
            circleBg.shape = GradientDrawable.OVAL
            circleBg.setColor(color(R.color.istan_olive))

            val plusText = text("+", 20, R.color.istan_text, false)
            plusText.setTextColor(ShadcnDrawables.primaryForeground(activity))
            plusText.gravity = Gravity.CENTER
            plusText.background = circleBg
            addCard.addView(plusText, LinearLayout.LayoutParams(dp(28), dp(28)))

            val addTitle = text(activity.getString(mode.addLabelRes), 15, R.color.istan_text, false)
            addTitle.setPadding(dp(12), 0, 0, 0)
            addCard.addView(addTitle)

            addCard.isClickable = true
            addCard.setOnClickListener { actions.onAddItems(mode) }

            val acLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            acLp.setMargins(0, 0, 0, dp(12))
            footer.addView(addCard, acLp)
        }

        val save = actionButton(activity.getString(mode.saveLabelRes), true) { actions.onSave(mode) }
        val saveLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        saveLp.setMargins(0, 0, 0, dp(8))
        footer.addView(save, saveLp)

        outer.addView(footer)
        return outer
    }

    private fun text(value: String, sp: Int, colorRes: Int, bold: Boolean): TextView {
        val textView = TextView(activity)
        textView.text = value
        textView.textSize = sp.toFloat()
        textView.setTextColor(color(colorRes))
        textView.typeface = if (bold) AppFont.semiBold else AppFont.regular
        textView.includeFontPadding = true
        return textView
    }

    private fun color(colorRes: Int): Int = ThemePrefs.resolveColor(activity, colorRes)

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)

    private fun actionButton(title: String, primary: Boolean, action: () -> Unit): View {
        val card = MaterialCardView(activity)
        val variant = if (primary) ShadcnDrawables.ButtonVariant.DEFAULT else ShadcnDrawables.ButtonVariant.OUTLINE
        ShadcnDrawables.applyButton(card, variant, ShadcnDrawables.BUTTON_RADIUS)

        val row = LinearLayout(activity)
        row.gravity = Gravity.CENTER
        row.minimumHeight = dp(48)
        row.setPadding(dp(22), dp(14), dp(22), dp(14))
        row.orientation = LinearLayout.HORIZONTAL
        card.addView(row)

        val label = text(title, 15, R.color.istan_text, false)
        label.setTextColor(ShadcnDrawables.buttonTextColor(activity, variant))
        label.gravity = Gravity.CENTER
        row.addView(label)

        card.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, dp(6))
        card.layoutParams = params
        return card
    }

    private inner class PagesAdapter(
        private val actions: EditorActions,
        private val checkboxSyncListener: Runnable?,
        private val isImg: Boolean,
        private val hideRotate: Boolean,
        private val hideDrag: Boolean,
        private val isMerge: Boolean
    ) : RecyclerView.Adapter<PagesAdapter.PageViewHolder>() {

        inner class PageViewHolder(
            itemView: View,
            val preview: ImageView,
            val keep: CheckBox,
            val info: TextView?,
            val titleText: TextView?,
            val cropBtn: ImageView,
            val rotateLeft: ImageView,
            val rotateRight: ImageView,
            val crossBtn: ImageView?
        ) : RecyclerView.ViewHolder(itemView) {
            var boundItem: PageItem? = null
            var bindGeneration: Int = 0
        }

        override fun getItemCount(): Int = actions.getPages().size

        override fun getItemViewType(position: Int): Int = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val card = MaterialCardView(activity)
            ShadcnDrawables.applyCard(card, ShadcnDrawables.CARD_RADIUS)

            if (isImg) {
                val row = LinearLayout(activity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.isBaselineAligned = false
                row.setPadding(dp(12), dp(12), dp(20), dp(12))
                card.addView(row)

                val dragHandle = ImageView(activity)
                dragHandle.setImageResource(R.drawable.drag_indicator_24px)
                dragHandle.setColorFilter(color(R.color.istan_text_muted))
                dragHandle.contentDescription = activity.getString(R.string.editor_reorder_page)
                dragHandle.setPadding(dp(12), dp(12), dp(12), dp(12))
                if (hideDrag) dragHandle.visibility = View.GONE
                row.addView(dragHandle, LinearLayout.LayoutParams(dp(48), dp(48)))

                val preview = ImageView(activity)
                preview.setBackgroundColor(color(R.color.istan_placeholder))
                preview.scaleType = ImageView.ScaleType.FIT_CENTER
                preview.adjustViewBounds = true

                val previewFrame = FrameLayout(activity)
                previewFrame.background = ShadcnDrawables.thumbnailFrame(activity)
                previewFrame.clipToOutline = true
                previewFrame.elevation = dp(2).toFloat()
                previewFrame.setPadding(dp(2), dp(2), dp(2), dp(2))
                previewFrame.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                row.addView(previewFrame, LinearLayout.LayoutParams(dp(54), dp(72)))

                val infoBox = LinearLayout(activity)
                infoBox.orientation = LinearLayout.VERTICAL
                infoBox.gravity = Gravity.CENTER_VERTICAL
                infoBox.setPadding(dp(16), 0, dp(8), 0)
                row.addView(infoBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

                val titleText = text("", 15, R.color.istan_text, true)
                titleText.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                titleText.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                titleText.isSingleLine = true
                infoBox.addView(titleText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

                val cropBtn = ImageView(activity)
                cropBtn.setImageResource(R.drawable.crop_24px)
                cropBtn.setColorFilter(color(R.color.istan_olive_dark))
                cropBtn.contentDescription = activity.getString(R.string.editor_crop_page)
                cropBtn.setPadding(dp(8), dp(8), dp(8), dp(8))

                val rotateLeft = ImageView(activity)
                rotateLeft.setImageResource(R.drawable.rotate_left)
                rotateLeft.setColorFilter(color(R.color.istan_olive_dark))
                rotateLeft.contentDescription = activity.getString(R.string.editor_rotate_left)
                rotateLeft.setPadding(dp(8), dp(8), dp(8), dp(8))

                val rotateRight = ImageView(activity)
                rotateRight.setImageResource(R.drawable.rotate_right)
                rotateRight.setColorFilter(color(R.color.istan_olive_dark))
                rotateRight.contentDescription = activity.getString(R.string.editor_rotate_right)
                rotateRight.setPadding(dp(8), dp(8), dp(8), dp(8))

                val actionsBox = LinearLayout(activity)
                actionsBox.orientation = LinearLayout.HORIZONTAL
                actionsBox.gravity = Gravity.CENTER_VERTICAL
                actionsBox.isBaselineAligned = false

                fun addAction(view: ImageView, size: Int) {
                    view.background = ShadcnDrawables.button(activity, ShadcnDrawables.ButtonVariant.GHOST, ShadcnDrawables.BUTTON_RADIUS)
                    actionsBox.addView(view, LinearLayout.LayoutParams(dp(size), dp(size)))
                }

                addAction(cropBtn, 40)
                addAction(rotateLeft, 40)
                addAction(rotateRight, 40)

                val cbColors = android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_checked),
                        intArrayOf(android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        color(R.color.istan_outline_emphasis),
                        color(R.color.istan_olive)
                    )
                )
                val keepBox = CheckBox(activity)
                keepBox.buttonTintList = cbColors
                keepBox.gravity = Gravity.CENTER

                var crossBtn: ImageView? = null
                if (isMerge) {
                    crossBtn = ImageView(activity)
                    crossBtn.setImageResource(R.drawable.close_24px)
                    crossBtn.setColorFilter(color(R.color.istan_destructive))
                    crossBtn.contentDescription = activity.getString(R.string.editor_remove_page)
                    crossBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
                    addAction(crossBtn, 40)
                } else {
                    val cbParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    cbParams.gravity = Gravity.CENTER_VERTICAL
                    actionsBox.addView(keepBox, cbParams)
                }

                row.addView(actionsBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

                val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, dp(8))
                card.layoutParams = lp

                return PageViewHolder(card, preview, keepBox, null, titleText, cropBtn, rotateLeft, rotateRight, crossBtn)
            } else {
                val row = LinearLayout(activity)
                row.orientation = LinearLayout.VERTICAL
                row.setPadding(dp(12), dp(12), dp(12), dp(12))
                card.addView(row)

                val preview = ImageView(activity)
                preview.setBackgroundColor(color(R.color.istan_placeholder))
                preview.scaleType = ImageView.ScaleType.FIT_CENTER
                preview.adjustViewBounds = true
                preview.maxHeight = dp(560)
                row.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

                val infoBox = LinearLayout(activity)
                infoBox.orientation = LinearLayout.HORIZONTAL
                infoBox.gravity = Gravity.CENTER_VERTICAL
                infoBox.isBaselineAligned = false
                infoBox.setPadding(0, dp(12), 0, 0)
                row.addView(infoBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

                val infoText = text("", 16, R.color.istan_text, true)
                infoBox.addView(infoText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                val rotateLeft = ImageView(activity)
                rotateLeft.setImageResource(R.drawable.rotate_left)
                rotateLeft.setColorFilter(color(R.color.istan_olive_dark))
                rotateLeft.setPadding(dp(12), dp(12), dp(12), dp(12))
                infoBox.addView(rotateLeft, LinearLayout.LayoutParams(dp(48), dp(48)))

                val rotateRight = ImageView(activity)
                rotateRight.setImageResource(R.drawable.rotate_right)
                rotateRight.setColorFilter(color(R.color.istan_olive_dark))
                rotateRight.setPadding(dp(12), dp(12), dp(12), dp(12))
                infoBox.addView(rotateRight, LinearLayout.LayoutParams(dp(48), dp(48)))

                val tcColors = android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_checked),
                        intArrayOf(android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        color(R.color.istan_outline_emphasis),
                        color(R.color.istan_olive)
                    )
                )
                val keepBox = CheckBox(activity)
                keepBox.text = activity.getString(R.string.editor_keep)
                keepBox.textSize = 18f
                keepBox.setTextColor(color(R.color.istan_text))
                keepBox.buttonTintList = tcColors
                keepBox.typeface = AppFont.regular
                val keepLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                keepLp.gravity = Gravity.CENTER_VERTICAL
                infoBox.addView(keepBox, keepLp)

                val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, dp(10))
                card.layoutParams = lp

                return PageViewHolder(card, preview, keepBox, infoText, null, ImageView(activity), rotateLeft, rotateRight, null)
            }
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val pages = actions.getPages()
            val item = pages[position]
            val generation = ++holder.bindGeneration
            holder.boundItem = item
            val expectedKey = item.cacheKey
            val cached = actions.getThumbnail(item)
            if (cached != null) {
                holder.preview.setImageBitmap(cached)
            } else {
                holder.preview.setImageBitmap(null)
                actions.requestThumbnail(item) { bmp ->
                    if (holder.boundItem === item &&
                        holder.bindGeneration == generation &&
                        item.cacheKey == expectedKey) {
                        holder.preview.setImageBitmap(bmp)
                    }
                }
            }

            if (isMerge && holder.crossBtn != null) {
                holder.crossBtn.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        (actions.getPages() as MutableList<PageItem>).removeAt(pos)
                        (actions.getPendingUris() as MutableList<Uri>).removeAt(pos)
                        notifyItemRemoved(pos)
                        checkboxSyncListener?.run()
                    }
                }
            }

            holder.preview.contentDescription = activity.getString(R.string.editor_preview_page, item.originalIndex + 1)

            if (isImg) {
                holder.titleText?.let {
                    it.visibility = View.VISIBLE
                    it.text = if (item.displayName != null && item.displayName.trim().isNotEmpty()) item.displayName else activity.getString(R.string.editor_page, item.originalIndex + 1)
                }
                holder.keep.contentDescription = activity.getString(R.string.editor_select_page, item.originalIndex + 1)
            } else {
                holder.info?.text = activity.getString(R.string.editor_page, item.originalIndex + 1)
            }

            holder.preview.setOnClickListener {
                showPreviewDialog(holder.bindingAdapterPosition)
            }

            holder.keep.setOnCheckedChangeListener(null)
            holder.keep.isChecked = item.keep
            holder.keep.setOnCheckedChangeListener { _, checked ->
                item.keep = checked
                checkboxSyncListener?.run()
            }

            if (isImg) {
                holder.keep.visibility = View.VISIBLE
                holder.cropBtn.visibility = if (hideRotate) View.GONE else View.VISIBLE
                if (hideRotate) {
                    holder.rotateLeft.visibility = View.GONE
                    holder.rotateRight.visibility = View.GONE
                } else {
                    holder.rotateLeft.visibility = View.VISIBLE
                    holder.rotateRight.visibility = View.VISIBLE
                }
            } else {
                holder.keep.visibility = if (actions.getPages().size <= 1) View.GONE else View.VISIBLE
                if (hideRotate) {
                    holder.rotateLeft.visibility = View.GONE
                    holder.rotateRight.visibility = View.GONE
                } else {
                    holder.rotateLeft.visibility = View.VISIBLE
                    holder.rotateRight.visibility = View.VISIBLE
                }
            }

            holder.cropBtn.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    actions.onCropPage(pos, null)
                }
            }

            holder.rotateLeft.setOnClickListener {
                actions.rotatePage(item, -90) { bmp, _ ->
                    if (holder.boundItem === item && holder.bindGeneration == generation) {
                        holder.preview.setImageBitmap(bmp)
                    }
                }
            }
            holder.rotateRight.setOnClickListener {
                actions.rotatePage(item, 90) { bmp, _ ->
                    if (holder.boundItem === item && holder.bindGeneration == generation) {
                        holder.preview.setImageBitmap(bmp)
                    }
                }
            }
        }

        private fun showPreviewDialog(startPosition: Int) {
            val dialog = android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.window!!.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            val immersiveScrim = color(R.color.istan_scrim_immersive)
            dialog.window!!.statusBarColor = immersiveScrim
            dialog.window!!.navigationBarColor = immersiveScrim
            dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val dialogRoot = LinearLayout(activity)
            dialogRoot.orientation = LinearLayout.VERTICAL
            dialogRoot.setBackgroundColor(immersiveScrim)

            val topBar = FrameLayout(activity)
            val closeBtn = ImageView(activity)
            closeBtn.setImageResource(R.drawable.close_24px)
            closeBtn.setColorFilter(Color.WHITE)
            closeBtn.setPadding(dp(16), dp(16), dp(16), dp(16))
            val clsLp = FrameLayout.LayoutParams(dp(60), dp(60))
            clsLp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            topBar.addView(closeBtn, clsLp)
            dialogRoot.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val pages = actions.getPages()
            val pagerAdapter = PreviewPagerAdapter(pages)
            val viewPager = ViewPager2(activity)
            viewPager.adapter = pagerAdapter
            viewPager.overScrollMode = View.OVER_SCROLL_NEVER
            val pagerLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            dialogRoot.addView(viewPager, pagerLp)

            val bottomBar = FrameLayout(activity)
            val pageCounter = text("", 16, R.color.istan_surface, true)
            pageCounter.setTextColor(Color.WHITE)
            val pcLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            pcLp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            pcLp.setMargins(dp(24), 0, 0, 0)
            bottomBar.addView(pageCounter, pcLp)

            val pillBg = GradientDrawable()
            pillBg.setColor(color(R.color.istan_glass))
            pillBg.cornerRadius = dp(ShadcnDrawables.BUTTON_RADIUS).toFloat()
            val controlsPill = LinearLayout(activity)
            controlsPill.orientation = LinearLayout.HORIZONTAL
            controlsPill.gravity = Gravity.CENTER_VERTICAL
            controlsPill.isBaselineAligned = false
            controlsPill.background = pillBg
            controlsPill.setPadding(dp(4), dp(4), dp(4), dp(4))

            val backBtn = TextView(activity)
            backBtn.text = "‹"
            backBtn.setTextColor(Color.WHITE)
            backBtn.textSize = 28f
            backBtn.gravity = Gravity.CENTER
            backBtn.typeface = AppFont.semiBold
            backBtn.setPadding(0, 0, dp(2), dp(2))
            controlsPill.addView(backBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

            val rotLeft = ImageView(activity)
            rotLeft.setImageResource(R.drawable.rotate_left)
            rotLeft.setColorFilter(Color.WHITE)
            rotLeft.setPadding(dp(11), dp(11), dp(11), dp(11))
            if (hideRotate) rotLeft.visibility = View.GONE
            controlsPill.addView(rotLeft, LinearLayout.LayoutParams(dp(48), dp(48)))

            val rotRight = ImageView(activity)
            rotRight.setImageResource(R.drawable.rotate_right)
            rotRight.setColorFilter(Color.WHITE)
            rotRight.setPadding(dp(11), dp(11), dp(11), dp(11))
            if (hideRotate) rotRight.visibility = View.GONE
            controlsPill.addView(rotRight, LinearLayout.LayoutParams(dp(48), dp(48)))

            val cropBtn = ImageView(activity)
            cropBtn.setImageResource(R.drawable.crop_24px)
            cropBtn.setColorFilter(Color.WHITE)
            cropBtn.setPadding(dp(12), dp(12), dp(12), dp(12))
            if (hideRotate) cropBtn.visibility = View.GONE
            controlsPill.addView(cropBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

            val cbColors = android.content.res.ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)),
                intArrayOf(color(R.color.istan_outline_emphasis), color(R.color.istan_olive))
            )
            val keepBox = CheckBox(activity)
            keepBox.buttonTintList = cbColors
            keepBox.text = activity.getString(R.string.editor_keep)
            keepBox.textSize = 16f
            keepBox.setTextColor(Color.WHITE)
            keepBox.setPadding(dp(2), 0, dp(14), 0)
            if (!isImg && pages.size <= 1) keepBox.visibility = View.GONE
            val keepLpDialog = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48))
            keepLpDialog.gravity = Gravity.CENTER_VERTICAL
            controlsPill.addView(keepBox, keepLpDialog)

            val pillLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            pillLp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            pillLp.setMargins(0, 0, dp(16), 0)
            bottomBar.addView(controlsPill, pillLp)

            val bottomBarLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
            bottomBarLp.setMargins(0, dp(4), 0, dp(8))
            dialogRoot.addView(bottomBar, bottomBarLp)

            val currentPos = intArrayOf(startPosition)

            val syncUi = Runnable {
                val pgs = actions.getPages()
                val p = pgs[currentPos[0]]
                pageCounter.text = activity.getString(R.string.editor_page_counter, currentPos[0] + 1, pgs.size)
                keepBox.setOnCheckedChangeListener(null)
                keepBox.isChecked = p.keep
                keepBox.setOnCheckedChangeListener { _, checked ->
                    p.keep = checked
                    checkboxSyncListener?.run()
                    val vh = pageList?.findViewHolderForAdapterPosition(currentPos[0])
                    if (vh is PageViewHolder) {
                        vh.keep.isChecked = checked
                    }
                }
            }

            syncUi.run()
            viewPager.setCurrentItem(startPosition, false)

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val oldVh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                    if (oldVh is PreviewPagerAdapter.PreviewVH) {
                        oldVh.image.resetZoom()
                    }
                    currentPos[0] = position
                    syncUi.run()
                }
            })

            rotLeft.setOnClickListener {
                val pgs = actions.getPages()
                val p = pgs[currentPos[0]]
                actions.rotatePage(p, -90) { thumb, full ->
                    val vh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                    if (vh is PreviewPagerAdapter.PreviewVH) {
                        vh.image.setImageBitmapAndReset(full ?: thumb)
                    }
                    val listVh = pageList?.findViewHolderForAdapterPosition(currentPos[0])
                    if (listVh is PageViewHolder) listVh.preview.setImageBitmap(thumb)
                }
                actions.requestFullRes(p) { bmp ->
                    val vh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                    if (vh is PreviewPagerAdapter.PreviewVH) {
                        vh.image.upgradeBitmap(bmp)
                    }
                }
            }
            rotRight.setOnClickListener {
                val pgs = actions.getPages()
                val p = pgs[currentPos[0]]
                actions.rotatePage(p, 90) { thumb, full ->
                    val vh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                    if (vh is PreviewPagerAdapter.PreviewVH) {
                        vh.image.setImageBitmapAndReset(full ?: thumb)
                    }
                    val listVh = pageList?.findViewHolderForAdapterPosition(currentPos[0])
                    if (listVh is PageViewHolder) listVh.preview.setImageBitmap(thumb)
                }
                actions.requestFullRes(p) { bmp ->
                    val vh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                    if (vh is PreviewPagerAdapter.PreviewVH) {
                        vh.image.upgradeBitmap(bmp)
                    }
                }
            }

            cropBtn.setOnClickListener {
                actions.onCropPage(currentPos[0]) {
                    viewPager.adapter?.notifyItemChanged(currentPos[0])
                }
            }

            backBtn.setOnClickListener { dialog.dismiss() }
            closeBtn.setOnClickListener { dialog.dismiss() }
            dialog.setContentView(dialogRoot)
            dialog.show()
        }

        private inner class PreviewPagerAdapter(
            private val pages: List<PageItem>
        ) : RecyclerView.Adapter<PreviewPagerAdapter.PreviewVH>() {

            inner class PreviewVH(
                val container: FrameLayout,
                val image: ZoomableImageView
            ) : RecyclerView.ViewHolder(container) {
                var boundItem: PageItem? = null
                var bindGeneration: Int = 0
            }

            override fun getItemCount(): Int = pages.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewVH {
                val container = FrameLayout(activity)
                container.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val image = ZoomableImageView(activity)
                val imgLp = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                imgLp.setMargins(dp(12), 0, dp(12), 0)
                container.addView(image, imgLp)

                return PreviewVH(container, image)
            }

            override fun onBindViewHolder(holder: PreviewVH, position: Int) {
                val p = pages[position]
                val generation = ++holder.bindGeneration
                holder.boundItem = p
                val expectedKey = p.cacheKey
                var fullApplied = false

                val full = actions.getFullRes(p)
                if (full != null) {
                    holder.image.setImageBitmapAndReset(full)
                    return
                }

                val cachedThumb = actions.getThumbnail(p)
                if (cachedThumb != null) {
                    holder.image.setImageBitmapAndReset(cachedThumb)
                } else {
                    holder.image.setImageBitmapAndReset(null)
                    actions.requestThumbnail(p) { bmp ->
                        if (!fullApplied &&
                            holder.boundItem === p &&
                            holder.bindGeneration == generation &&
                            p.cacheKey == expectedKey) {
                            holder.image.setImageBitmapAndReset(bmp)
                        }
                    }
                }

                actions.requestFullRes(p) { bmp ->
                    if (holder.boundItem === p &&
                        holder.bindGeneration == generation &&
                        p.cacheKey == expectedKey) {
                        fullApplied = true
                        holder.image.upgradeBitmap(bmp)
                    }
                }
            }
        }
    }
}
