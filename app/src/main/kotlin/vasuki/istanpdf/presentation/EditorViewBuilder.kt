package vasuki.istanpdf.presentation

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
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
import vasuki.istanpdf.model.PageItem

class EditorViewBuilder(
    private val activity: Activity,
    private val regularFont: Typeface,
    private val boldFont: Typeface
) {

    interface EditorActions {
        fun onBack()
        fun onSave(titleText: String, docxExport: Boolean)
        fun onAddItems(titleText: String)
        fun onTakePhoto()
        fun onCropPage(position: Int, onCropped: Runnable?)
        fun onShowCustomDialog(title: String, content: View, negativeStr: String?, negativeAction: Runnable?, positiveStr: String?, positiveAction: Runnable?)
        fun toast(message: String)
        fun getPages(): List<PageItem>
        fun getPendingUris(): List<Uri>
        fun isPagesAdded(): Boolean
    }

    companion object {
        private const val WAITING_TEXT = "Ready"
    }

    var status: TextView? = null
        private set
    var statusIndicator: ImageView? = null
        private set
    var pageList: RecyclerView? = null
        private set

    fun build(titleText: String, saveLabelText: String, docxExport: Boolean, allowReorder: Boolean, actions: EditorActions): View {
        val outer = LinearLayout(activity)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(color(R.color.istan_background))

        val titleRow = LinearLayout(activity)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        titleRow.setPadding(dp(12), dp(16), dp(22), dp(16))
        outer.addView(titleRow)

        val backArrow = text("←", 28, R.color.istan_text, true)
        backArrow.setPadding(0, 0, dp(16), dp(4))
        backArrow.setOnClickListener { actions.onBack() }
        titleRow.addView(backArrow)

        val title = text(titleText, 22, R.color.istan_text, true)
        titleRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val separator = View(activity)
        separator.setBackgroundColor(if (ThemePrefs.isAmoled(activity)) 0xFF333333.toInt() else 0xFFB4B8AA.toInt())
        outer.addView(separator, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        val header = LinearLayout(activity)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(22), dp(16), dp(22), 0)
        outer.addView(header)

        val statusCard = LinearLayout(activity)
        statusCard.orientation = LinearLayout.HORIZONTAL
        statusCard.gravity = Gravity.CENTER
        statusCard.isBaselineAligned = false
        statusCard.setPadding(dp(16), dp(10), dp(20), dp(10))
        val statusBg = GradientDrawable()
        statusBg.setColor(color(R.color.istan_surface))
        statusBg.cornerRadius = dp(28).toFloat()
        statusBg.setStroke(dp(1), color(R.color.istan_outline))
        statusCard.background = statusBg

        statusIndicator = ImageView(activity)
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(color(R.color.istan_olive))
        dot.setSize(dp(12), dp(12))
        statusIndicator!!.setImageDrawable(dot)
        statusCard.addView(statusIndicator)

        status = text(WAITING_TEXT, 16, R.color.istan_olive, false)
        status!!.maxLines = 1
        status!!.gravity = Gravity.CENTER
        status!!.setPadding(dp(8), 0, dp(4), 0)
        statusCard.addView(status)

        val scParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scParams.setMargins(0, 0, 0, dp(12))
        header.addView(statusCard, scParams)

        val recycler = RecyclerView(activity)
        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.setPadding(dp(8), dp(8), dp(8), dp(8))
        recycler.clipToPadding = false
        val listParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        outer.addView(recycler, listParams)
        pageList = recycler

        val updateCountRef = arrayOfNulls<Runnable>(1)

        if (titleText == "Remove/Reorder PDF" || titleText == "Remove Pages from DOCX" || titleText == "Reorder Pages from DOCX") {
            val selectedRow = LinearLayout(activity)
            selectedRow.orientation = LinearLayout.HORIZONTAL
            selectedRow.gravity = Gravity.CENTER_VERTICAL
            selectedRow.setPadding(dp(22), dp(8), dp(22), dp(8))

            val textCol = LinearLayout(activity)
            textCol.orientation = LinearLayout.VERTICAL
            selectedRow.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val selTitle = text("Selected Pages", 18, R.color.istan_text, true)
            textCol.addView(selTitle)

            val selSub = text("", 14, R.color.istan_olive, false)
            selSub.setPadding(0, dp(4), 0, 0)
            textCol.addView(selSub)

            val editBtn = MaterialCardView(activity)
            editBtn.setCardBackgroundColor(Color.TRANSPARENT)
            editBtn.radius = dp(100).toFloat()
            editBtn.strokeWidth = dp(1)
            editBtn.strokeColor = color(R.color.istan_outline)
            editBtn.cardElevation = 0f
            editBtn.setRippleColorResource(android.R.color.transparent)

            val editLayout = LinearLayout(activity)
            editLayout.orientation = LinearLayout.HORIZONTAL
            editLayout.gravity = Gravity.CENTER_VERTICAL
            editLayout.setPadding(dp(12), dp(6), dp(12), dp(6))

            val editIcon = ImageView(activity)
            editIcon.setImageResource(R.drawable.edit_minimal_24px)
            editIcon.setColorFilter(color(R.color.istan_text_muted))
            editLayout.addView(editIcon, LinearLayout.LayoutParams(dp(16), dp(16)))

            val editTxt = text("Edit Range", 14, R.color.istan_text_muted, false)
            editTxt.setPadding(dp(6), 0, 0, 0)
            editLayout.addView(editTxt)

            editBtn.addView(editLayout)
            selectedRow.addView(editBtn)
            outer.addView(selectedRow, 2)

            updateCountRef[0] = Runnable {
                var count = 0
                for (p in actions.getPages()) if (p.keep) count++
                selSub.text = "$count of ${actions.getPages().size} pages selected"
            }
            updateCountRef[0]!!.run()

            editBtn.setOnClickListener {
                val input = EditText(activity)
                input.setTextColor(color(R.color.istan_text))
                input.hint = "Type range (e.g. 1-3, 5)..."
                input.setHintTextColor(color(R.color.istan_text_muted))
                input.setPadding(dp(16), dp(16), dp(16), dp(16))

                val accentColor = color(R.color.istan_olive)
                input.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)
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

                actions.onShowCustomDialog("Edit Range", input, "Cancel", null, "Apply") {
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
                                        if ((titleText == "Merge PDF" || titleText == "Images to PDF") && i < pendingUris.size) {
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
                                    if ((titleText == "Merge PDF" || titleText == "Images to PDF") && i < pendingUris.size) {
                                        newUris.add(pendingUris[i])
                                    }
                                }
                            }

                            (currentPages as MutableList<PageItem>).clear()
                            currentPages.addAll(newPages)

                            if (titleText == "Merge PDF" || titleText == "Images to PDF") {
                                (pendingUris as MutableList<Uri>).clear()
                                pendingUris.addAll(newUris)
                            }

                        } catch (ignored: Exception) {
                            Toast.makeText(activity, "Invalid range format", Toast.LENGTH_SHORT).show()
                            return@onShowCustomDialog
                        }
                    }
                    recycler.adapter?.notifyDataSetChanged()
                    updateCountRef[0]?.run()
                }
            }
        }

        val isRemoveDocx = titleText == "Remove Pages from DOCX"
        val hideRotate = isRemoveDocx || titleText == "Merge PDF"
        val hideDrag = isRemoveDocx
        val isImg = titleText == "Images to PDF"
                || titleText == "Remove/Reorder PDF"
                || titleText == "Reorder Pages from DOCX"
                || isRemoveDocx
                || titleText == "Merge PDF"

        val isMerge = titleText == "Merge PDF"
        val adapter = PagesAdapter(actions, {
            updateCountRef[0]?.run()
        }, isImg, hideRotate, hideDrag, isMerge)
        recycler.adapter = adapter

        if (allowReorder) {
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

                    if (titleText == "Merge PDF") {
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

        if (titleText == "Images to PDF" || titleText == "Reorder Pages from DOCX" || titleText == "Remove/Reorder PDF" || titleText == "Merge PDF") {
            if (titleText == "Images to PDF" || titleText == "Reorder Pages from DOCX" || titleText == "Remove/Reorder PDF") {
                val row = LinearLayout(activity)
                row.orientation = LinearLayout.HORIZONTAL
                val rowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                rowLp.setMargins(0, 0, 0, dp(12))
                footer.addView(row, 0, rowLp)

                var addLabel = "Add Images / PDF"
                if (titleText == "Reorder Pages from DOCX") addLabel = "Add DOCX / PDF"

                row.addView(
                    buildSourceCard(R.drawable.add_24px, addLabel, 8) { actions.onAddItems(titleText) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                )

                val camLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                camLp.setMargins(dp(8), 0, 0, 0)
                row.addView(
                    buildSourceCard(R.drawable.camera_24px, "Take Photo") { actions.onTakePhoto() },
                    camLp
                )
            } else {
                val addCard = MaterialCardView(activity)
                addCard.setCardBackgroundColor(color(R.color.istan_surface))
                addCard.radius = dp(28).toFloat()
                addCard.cardElevation = 0f

                val dashBg = GradientDrawable()
                dashBg.setColor(Color.TRANSPARENT)
                dashBg.cornerRadius = dp(28).toFloat()
                dashBg.setStroke(dp(1), color(R.color.istan_outline), dp(4).toFloat(), dp(4).toFloat())
                addCard.background = dashBg

                val addRow = LinearLayout(activity)
                addRow.orientation = LinearLayout.HORIZONTAL
                addRow.gravity = Gravity.CENTER
                addRow.setPadding(dp(16), dp(12), dp(16), dp(12))
                addCard.addView(addRow)

                val circleBg = GradientDrawable()
                circleBg.shape = GradientDrawable.OVAL
                circleBg.setColor(color(R.color.istan_olive))

                val plusText = text("+", 20, R.color.istan_text, false)
                plusText.setTextColor(Color.WHITE)
                plusText.gravity = Gravity.CENTER
                plusText.background = circleBg
                addRow.addView(plusText, LinearLayout.LayoutParams(dp(28), dp(28)))

                var labelStr = "Tap to Add Images / PDF"
                if (titleText == "Reorder Pages from DOCX") labelStr = "Tap to Add Images / DOCX / PDF"
                else if (titleText == "Merge PDF") labelStr = "Tap to Add PDF"
                val addTitle = text(labelStr, 15, R.color.istan_text, false)
                addTitle.setPadding(dp(12), 0, 0, 0)
                addRow.addView(addTitle)

                addCard.setOnClickListener { actions.onAddItems(titleText) }

                val acLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                acLp.setMargins(0, 0, 0, dp(12))
                footer.addView(addCard, 0, acLp)
            }
        }

        val save = actionButton(saveLabelText, true) { actions.onSave(titleText, docxExport) }
        val saveLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        saveLp.gravity = Gravity.CENTER_HORIZONTAL
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
        textView.typeface = if (bold) boldFont else regularFont
        textView.includeFontPadding = true
        return textView
    }

    private fun color(colorRes: Int): Int = ThemePrefs.resolveColor(activity, colorRes)

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)

    private fun buildSourceCard(iconResId: Int, label: String, iconInsetDp: Int = 5, action: () -> Unit): View {
        val card = MaterialCardView(activity)
        card.setCardBackgroundColor(color(R.color.istan_surface))
        card.radius = dp(28).toFloat()
        card.cardElevation = 0f

        val dashBg = GradientDrawable()
        dashBg.setColor(Color.TRANSPARENT)
        dashBg.cornerRadius = dp(28).toFloat()
        dashBg.setStroke(dp(1), color(R.color.istan_outline), dp(4).toFloat(), dp(4).toFloat())
        card.background = dashBg

        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(dp(16), dp(12), dp(16), dp(12))
        card.addView(col)

        val icon = ImageView(activity)
        icon.setImageResource(iconResId)
        icon.setColorFilter(Color.WHITE)
        val circleBg = GradientDrawable()
        circleBg.shape = GradientDrawable.OVAL
        circleBg.setColor(color(R.color.istan_olive))
        icon.background = circleBg
        icon.setPadding(dp(iconInsetDp), dp(iconInsetDp), dp(iconInsetDp), dp(iconInsetDp))
        col.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))

        val labelView = text(label, 13, R.color.istan_text, false)
        labelView.gravity = Gravity.CENTER
        labelView.maxLines = 2
        labelView.ellipsize = android.text.TextUtils.TruncateAt.END
        labelView.setPadding(0, dp(8), 0, 0)
        col.addView(labelView)

        card.setOnClickListener { action() }
        return card
    }

    private fun actionButton(title: String, primary: Boolean, action: () -> Unit): View {
        val card = MaterialCardView(activity)
        val cardColor = color(if (primary) R.color.istan_olive else R.color.istan_surface)
        card.setCardBackgroundColor(cardColor)
        card.radius = dp(16).toFloat()
        if (!primary) {
            card.strokeWidth = dp(1)
            card.strokeColor = color(R.color.istan_outline)
        } else {
            card.strokeWidth = 0
        }
        card.cardElevation = 0f
        card.useCompatPadding = true

        val row = LinearLayout(activity)
        row.gravity = Gravity.CENTER
        row.setPadding(dp(22), dp(12), dp(22), dp(12))
        row.orientation = LinearLayout.HORIZONTAL
        card.addView(row)

        val label = text(title, 15, R.color.istan_text, false)
        label.setTextColor(if (primary) (if (ThemePrefs.isAmoled(activity)) Color.BLACK else Color.WHITE) else color(R.color.istan_text))
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
            val info: TextView,
            val titleText: TextView?,
            val cropBtn: ImageView,
            val rotateLeft: ImageView,
            val rotateRight: ImageView,
            val crossBtn: TextView?
        ) : RecyclerView.ViewHolder(itemView)

        override fun getItemCount(): Int = actions.getPages().size

        override fun getItemViewType(position: Int): Int = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val card = MaterialCardView(activity)
            card.setCardBackgroundColor(color(R.color.istan_surface))
            card.radius = dp(16).toFloat()
            card.strokeWidth = dp(1)
            card.strokeColor = color(R.color.istan_outline)
            card.cardElevation = 0f
            card.useCompatPadding = true

            if (isImg) {
                val row = LinearLayout(activity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(dp(12), dp(12), dp(20), dp(12))
                card.addView(row)

                val dragHandle = text("⋮⋮", 24, R.color.istan_olive, false)
                dragHandle.setPadding(dp(8), dp(4), dp(16), dp(4))
                if (hideDrag) dragHandle.visibility = View.GONE
                row.addView(dragHandle)

                val preview = ImageView(activity)
                preview.setBackgroundColor(Color.WHITE)
                preview.scaleType = ImageView.ScaleType.FIT_CENTER
                preview.adjustViewBounds = true

                val previewFrame = FrameLayout(activity)
                val frameBg = GradientDrawable()
                frameBg.setColor(Color.WHITE)
                frameBg.setStroke(dp(1), color(R.color.istan_outline))
                previewFrame.background = frameBg
                previewFrame.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                row.addView(previewFrame, LinearLayout.LayoutParams(dp(54), dp(72)))

                val infoBox = LinearLayout(activity)
                infoBox.orientation = LinearLayout.VERTICAL
                infoBox.setPadding(dp(16), 0, dp(8), 0)
                row.addView(infoBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                val titleRowInner = LinearLayout(activity)
                titleRowInner.orientation = LinearLayout.HORIZONTAL
                titleRowInner.gravity = Gravity.CENTER_VERTICAL
                titleRowInner.isBaselineAligned = false
                infoBox.addView(titleRowInner)

                val titleText = text("", 15, R.color.istan_text, true)
                titleText.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                titleText.isSingleLine = true
                titleRowInner.addView(titleText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

                val cbColors = android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(-android.R.attr.state_checked),
                        intArrayOf(android.R.attr.state_checked)
                    ),
                    intArrayOf(
                        color(R.color.istan_outline),
                        color(R.color.istan_olive)
                    )
                )
                val keepBox = CheckBox(activity)
                keepBox.buttonTintList = cbColors

                val cbParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                cbParams.setMargins(dp(8), 0, 0, 0)
                cbParams.gravity = Gravity.CENTER_VERTICAL

                var crossBtn: TextView? = null
                if (isMerge) {
                    keepBox.visibility = View.GONE
                    crossBtn = TextView(activity)
                    crossBtn.text = "✕"
                    crossBtn.textSize = 22f
                    crossBtn.setTextColor(color(R.color.istan_olive))
                    crossBtn.setPadding(dp(12), dp(4), dp(8), dp(4))
                }

                val infoText = text("", 13, R.color.istan_olive, false)
                infoText.setPadding(0, dp(2), 0, 0)
                if (isMerge) infoText.visibility = View.GONE
                infoBox.addView(infoText)

                val cropBtn = ImageView(activity)
                cropBtn.setImageResource(R.drawable.crop_24px)
                cropBtn.setColorFilter(color(R.color.istan_olive_dark))
                cropBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
                if (hideRotate) cropBtn.visibility = View.GONE

                val rotateLeft = ImageView(activity)
                rotateLeft.setImageResource(R.drawable.rotate_left)
                rotateLeft.setColorFilter(color(R.color.istan_olive_dark))
                rotateLeft.setPadding(dp(8), dp(8), dp(8), dp(8))
                if (hideRotate) rotateLeft.visibility = View.GONE

                val rotateRight = ImageView(activity)
                rotateRight.setImageResource(R.drawable.rotate_right)
                rotateRight.setColorFilter(color(R.color.istan_olive_dark))
                rotateRight.setPadding(dp(8), dp(8), dp(8), dp(8))
                if (hideRotate) rotateRight.visibility = View.GONE

                val actionsBox = LinearLayout(activity)
                actionsBox.orientation = LinearLayout.HORIZONTAL
                actionsBox.gravity = Gravity.CENTER_VERTICAL
                actionsBox.addView(cropBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
                actionsBox.addView(rotateLeft, LinearLayout.LayoutParams(dp(40), dp(40)))
                actionsBox.addView(rotateRight, LinearLayout.LayoutParams(dp(40), dp(40)))
                if (isMerge) {
                    actionsBox.addView(crossBtn)
                } else {
                    actionsBox.addView(keepBox, cbParams)
                }
                row.addView(actionsBox)

                val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, dp(8))
                card.layoutParams = lp

                return PageViewHolder(card, preview, keepBox, infoText, titleText, cropBtn, rotateLeft, rotateRight, crossBtn)
            } else {
                val row = LinearLayout(activity)
                row.orientation = LinearLayout.VERTICAL
                row.setPadding(dp(12), dp(12), dp(12), dp(12))
                card.addView(row)

                val preview = ImageView(activity)
                preview.setBackgroundColor(Color.WHITE)
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
                rotateLeft.setPadding(dp(8), dp(8), dp(8), dp(8))
                infoBox.addView(rotateLeft, LinearLayout.LayoutParams(dp(40), dp(40)))

                val rotateRight = ImageView(activity)
                rotateRight.setImageResource(R.drawable.rotate_right)
                rotateRight.setColorFilter(color(R.color.istan_olive_dark))
                rotateRight.setPadding(dp(8), dp(8), dp(8), dp(8))
                infoBox.addView(rotateRight, LinearLayout.LayoutParams(dp(40), dp(40)))

                val keepBox = CheckBox(activity)
                keepBox.text = "Keep"
                keepBox.textSize = 18f
                keepBox.setTextColor(color(R.color.istan_text))
                keepBox.typeface = regularFont
                val keepLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                keepLp.gravity = Gravity.CENTER_VERTICAL
                infoBox.addView(keepBox, keepLp)

                val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, dp(8))
                card.layoutParams = lp

                return PageViewHolder(card, preview, keepBox, infoText, null, ImageView(activity), rotateLeft, rotateRight, null)
            }
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val pages = actions.getPages()
            val item = pages[position]
            holder.preview.setImageBitmap(item.thumbnail)

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

            if (isImg) {
                holder.titleText?.let {
                    it.visibility = View.VISIBLE
                    it.text = if (item.displayName != null && item.displayName.trim().isNotEmpty()) item.displayName else "Page ${item.originalIndex + 1}"
                }
                holder.info.text = if (item.keep) "Selected" else "Unselected"
                holder.info.setTextColor(color(if (item.keep) R.color.istan_olive else R.color.istan_text_muted))
            } else {
                holder.info.text = "Page ${item.originalIndex + 1}"
            }

            holder.preview.setOnClickListener {
                showPreviewDialog(holder.bindingAdapterPosition)
            }

            holder.keep.setOnCheckedChangeListener(null)
            holder.keep.isChecked = item.keep
            holder.keep.setOnCheckedChangeListener { _, checked ->
                item.keep = checked
                if (isImg && holder.info != null) {
                    holder.info.text = if (checked) "Selected" else "Unselected"
                    holder.info.setTextColor(color(if (checked) R.color.istan_olive else R.color.istan_text_muted))
                }
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
                item.rotation = (item.rotation - 90) % 360
                val matrix = android.graphics.Matrix()
                matrix.postRotate(-90f)
                val newThumb = Bitmap.createBitmap(item.thumbnail, 0, 0, item.thumbnail.width, item.thumbnail.height, matrix, true)
                item.thumbnail.recycle()
                item.thumbnail = newThumb
                holder.preview.setImageBitmap(item.thumbnail)
            }
            holder.rotateRight.setOnClickListener {
                item.rotation = (item.rotation + 90) % 360
                val matrix = android.graphics.Matrix()
                matrix.postRotate(90f)
                val newThumb = Bitmap.createBitmap(item.thumbnail, 0, 0, item.thumbnail.width, item.thumbnail.height, matrix, true)
                item.thumbnail.recycle()
                item.thumbnail = newThumb
                holder.preview.setImageBitmap(item.thumbnail)
            }
        }

        private fun showPreviewDialog(startPosition: Int) {
            val dialog = android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.window!!.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            dialog.window!!.statusBarColor = Color.parseColor("#E6252525")
            dialog.window!!.navigationBarColor = Color.parseColor("#E6252525")
            dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val dialogRoot = LinearLayout(activity)
            dialogRoot.orientation = LinearLayout.VERTICAL
            dialogRoot.setBackgroundColor(Color.parseColor("#E6252525"))

            val topBar = FrameLayout(activity)
            val closeBtn = TextView(activity)
            closeBtn.text = "✕"
            closeBtn.setTextColor(Color.WHITE)
            closeBtn.textSize = 26f
            closeBtn.setPadding(dp(16), dp(12), dp(16), dp(12))
            val clsLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
            pillBg.setColor(Color.parseColor("#22FFFFFF"))
            pillBg.cornerRadius = dp(32).toFloat()
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
            backBtn.typeface = boldFont
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
                intArrayOf(Color.parseColor("#888888"), color(R.color.istan_olive))
            )
            val keepBox = CheckBox(activity)
            keepBox.buttonTintList = cbColors
            keepBox.text = "Keep"
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
                pageCounter.text = "${currentPos[0] + 1} / ${pgs.size}"
                keepBox.setOnCheckedChangeListener(null)
                keepBox.isChecked = p.keep
                keepBox.setOnCheckedChangeListener { _, checked ->
                    p.keep = checked
                    checkboxSyncListener?.run()
                    val vh = pageList?.findViewHolderForAdapterPosition(currentPos[0])
                    if (vh is PageViewHolder) {
                        vh.keep.isChecked = checked
                        if (isImg) {
                            vh.info.text = if (checked) "Selected" else "Unselected"
                            vh.info.setTextColor(color(if (checked) R.color.istan_olive else R.color.istan_text_muted))
                        }
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
                p.rotation = (p.rotation - 90) % 360
                val matrix = android.graphics.Matrix()
                matrix.postRotate(-90f)
                val newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.width, p.thumbnail.height, matrix, true)
                p.thumbnail.recycle()
                p.thumbnail = newThumb
                val vh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                if (vh is PreviewPagerAdapter.PreviewVH) {
                    vh.image.setImageBitmapAndReset(p.thumbnail)
                }
                val listVh = pageList?.findViewHolderForAdapterPosition(currentPos[0])
                if (listVh is PageViewHolder) listVh.preview.setImageBitmap(p.thumbnail)
            }
            rotRight.setOnClickListener {
                val pgs = actions.getPages()
                val p = pgs[currentPos[0]]
                p.rotation = (p.rotation + 90) % 360
                val matrix = android.graphics.Matrix()
                matrix.postRotate(90f)
                val newThumb = Bitmap.createBitmap(p.thumbnail, 0, 0, p.thumbnail.width, p.thumbnail.height, matrix, true)
                p.thumbnail.recycle()
                p.thumbnail = newThumb
                val vh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                if (vh is PreviewPagerAdapter.PreviewVH) {
                    vh.image.setImageBitmapAndReset(p.thumbnail)
                }
                val listVh = pageList?.findViewHolderForAdapterPosition(currentPos[0])
                if (listVh is PageViewHolder) listVh.preview.setImageBitmap(p.thumbnail)
            }

            cropBtn.setOnClickListener {
                actions.onCropPage(currentPos[0]) {
                    val pgs = actions.getPages()
                    val p = pgs[currentPos[0]]
                    val vh = (viewPager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(currentPos[0])
                    if (vh is PreviewPagerAdapter.PreviewVH) {
                        vh.image.setImageBitmapAndReset(p.thumbnail)
                    }
                    val listVh = pageList?.findViewHolderForAdapterPosition(currentPos[0])
                    if (listVh is PageViewHolder) listVh.preview.setImageBitmap(p.thumbnail)
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
            ) : RecyclerView.ViewHolder(container)

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
                holder.image.setImageBitmapAndReset(p.thumbnail)
            }
        }
    }
}
