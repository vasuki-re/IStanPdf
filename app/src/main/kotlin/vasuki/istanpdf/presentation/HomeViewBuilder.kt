package vasuki.istanpdf.presentation

import android.app.Activity
import vasuki.istanpdf.AppFont
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import vasuki.istanpdf.R
import vasuki.istanpdf.ThemePrefs
class HomeViewBuilder(
    private val activity: Activity
) {

    interface HomeActions {
        fun onMergePdf()
        fun onModifyPdf()
        fun onCompressPdf()
        fun onImageToPdf()
        fun onPdfToImage()
        fun onDocxToPdf()
        fun onMdToPdf()
        fun onPptxToPdf()
        fun onXlsxToPdf()
        fun onDocxRemovePages()
        fun onDocxReorderPages()
        fun onSupportDeveloper()
        fun onOpenSettings()
    }

    var status: TextView? = null
        private set
    var statusIndicator: ImageView? = null
        private set

    fun build(actions: HomeActions): View {
        val mainContainer = LinearLayout(activity)
        mainContainer.orientation = LinearLayout.VERTICAL
        mainContainer.setBackgroundColor(color(R.color.istan_background))

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, windowInsets ->
            val insets: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, insets.top, 0, 0)
            WindowInsetsCompat.CONSUMED
        }

        val scrollView = ScrollView(activity)
        scrollView.isFillViewport = true
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.overScrollMode = View.OVER_SCROLL_NEVER
        val scrollParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f)
        mainContainer.addView(scrollView, scrollParams)

        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(24), dp(12), dp(16))

        scrollView.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val heroRow = LinearLayout(activity)
        heroRow.orientation = LinearLayout.HORIZONTAL
        heroRow.gravity = Gravity.CENTER_VERTICAL
        heroRow.setPadding(dp(4), 0, dp(4), 0)
        root.addView(heroRow)

        val title = text(activity.getString(R.string.app_name), 40, R.color.istan_text, true).apply { letterSpacing = -0.02f }
        heroRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val settingsButton = MaterialCardView(activity)
        ShadcnDrawables.applyButton(settingsButton, ShadcnDrawables.ButtonVariant.OUTLINE, ShadcnDrawables.BUTTON_RADIUS)
        settingsButton.setOnClickListener { actions.onOpenSettings() }

        val menuIcon = ImageView(activity)
        menuIcon.setImageResource(R.drawable.menu)
        menuIcon.setColorFilter(color(R.color.istan_text))
        menuIcon.contentDescription = activity.getString(R.string.settings_title)
        menuIcon.setPadding(dp(12), dp(12), dp(12), dp(12))
        settingsButton.addView(menuIcon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))

        val settingsLp = LinearLayout.LayoutParams(dp(48), dp(48))
        settingsLp.setMargins(dp(16), 0, 0, 0)
        heroRow.addView(settingsButton, settingsLp)

        val subtitle = text(activity.getString(R.string.home_subtitle), 16, R.color.istan_text_muted, false)
        subtitle.setPadding(dp(4), dp(4), dp(4), dp(36))
        root.addView(subtitle)

        val campaign = buildCampaignCard(actions::onSupportDeveloper)
        if (campaign != null) {
            subtitle.setPadding(dp(4), dp(4), dp(4), dp(16))
            root.addView(campaign)
        }
        root.addView(createSectionHeader(activity.getString(R.string.home_section_pdf_tools)))
        val pdfRow1 = LinearLayout(activity)
        pdfRow1.orientation = LinearLayout.HORIZONTAL
        pdfRow1.addView(dashboardCard(activity.getString(R.string.home_tool_merge_pdf), R.drawable.merge_24px, actions::onMergePdf))
        pdfRow1.addView(dashboardCard(activity.getString(R.string.home_tool_modify_pdf), R.drawable.modify_pdf_24px, actions::onModifyPdf))
        root.addView(pdfRow1)

        val compressCard = dashboardCard(activity.getString(R.string.home_tool_compress_pdf), R.drawable.compress_pdf_24px, actions::onCompressPdf)
        (compressCard.layoutParams as LinearLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            weight = 0f
        }
        root.addView(compressCard)

        root.addView(createSectionHeader(activity.getString(R.string.home_section_conversions)))
        val convRow1 = LinearLayout(activity)
        convRow1.orientation = LinearLayout.HORIZONTAL
        convRow1.addView(dashboardCard(activity.getString(R.string.home_tool_image_to_pdf), R.drawable.img2pdf_24px, actions::onImageToPdf))
        convRow1.addView(dashboardCard(activity.getString(R.string.home_tool_pdf_to_image), R.drawable.pdf2img_24px, actions::onPdfToImage))
        root.addView(convRow1)

        val convRow2 = LinearLayout(activity)
        convRow2.orientation = LinearLayout.HORIZONTAL
        convRow2.addView(dashboardCard(activity.getString(R.string.home_tool_docx_to_pdf), R.drawable.docx2pdf_24px, actions::onDocxToPdf))
        convRow2.addView(dashboardCard(activity.getString(R.string.home_tool_md_to_pdf), R.drawable.md2pdf_24px, actions::onMdToPdf))
        root.addView(convRow2)

        val convRow3 = LinearLayout(activity)
        convRow3.orientation = LinearLayout.HORIZONTAL
        convRow3.addView(dashboardCard(activity.getString(R.string.home_tool_pptx_to_pdf), R.drawable.pptx2pdf_24px, actions::onPptxToPdf))
        convRow3.addView(dashboardCard(activity.getString(R.string.home_tool_xlsx_to_pdf), R.drawable.xlsx2pdf_24px, actions::onXlsxToPdf))
        root.addView(convRow3)

        root.addView(createSectionHeader(activity.getString(R.string.home_section_docx_tools)))
        val docxRow1 = LinearLayout(activity)
        docxRow1.orientation = LinearLayout.HORIZONTAL
        docxRow1.addView(dashboardCard(activity.getString(R.string.home_tool_remove_pages), R.drawable.remove_page_docx_24px, actions::onDocxRemovePages))
        docxRow1.addView(dashboardCard(activity.getString(R.string.home_tool_reorder_pages), R.drawable.reorder_docx_24px, actions::onDocxReorderPages))
        root.addView(docxRow1)

        root.addView(kofiCard(activity.getString(R.string.home_support_card), R.drawable.ic_kofi, actions::onSupportDeveloper))

        val topSpacer = View(activity)
        root.addView(topSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f))

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
        statusIndicator!!.setImageDrawable(dot)
        statusCard.addView(statusIndicator, LinearLayout.LayoutParams(dp(12), dp(12)))

        status = text(activity.getString(R.string.status_ready), 14, R.color.istan_olive, false)
        status!!.gravity = Gravity.CENTER_VERTICAL
        status!!.setPadding(dp(8), 0, 0, 0)
        statusCard.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        scParams.setMargins(0, dp(12), 0, dp(20))
        scParams.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(statusCard, scParams)

        return mainContainer
    }

    private fun dashboardCard(title: String, iconResId: Int, action: () -> Unit): View {
        val card = MaterialCardView(activity)
        ShadcnDrawables.applyCard(card, ShadcnDrawables.CARD_RADIUS)
        card.minimumHeight = dp(64)

        val row = LinearLayout(activity)
        row.gravity = Gravity.CENTER_VERTICAL
        row.isBaselineAligned = false
        row.setPadding(dp(14), dp(10), dp(14), dp(10))
        row.orientation = LinearLayout.HORIZONTAL

        val rowLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        )
        card.addView(row, rowLp)

        if (iconResId != 0) {
            val icon = ImageView(activity)
            icon.setImageResource(iconResId)
            icon.setColorFilter(color(R.color.istan_olive_dark))
            val iconParams = LinearLayout.LayoutParams(dp(26), dp(26))
            iconParams.setMargins(0, 0, dp(6), 0)
            row.addView(icon, iconParams)
        }

        val label = text(title, 15, R.color.istan_text, false)
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        val labelParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        labelParams.gravity = Gravity.CENTER_VERTICAL
        row.addView(label, labelParams)
        card.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
        params.setMargins(dp(4), dp(3), dp(4), dp(3))
        card.layoutParams = params
        return card
    }

    private fun kofiCard(title: String, iconResId: Int, action: () -> Unit): View {
        val card = MaterialCardView(activity)
        ShadcnDrawables.applyCard(card, ShadcnDrawables.CARD_RADIUS)

        val row = LinearLayout(activity)
        row.gravity = Gravity.CENTER_VERTICAL
        row.isBaselineAligned = false
        row.setPadding(dp(14), dp(18), dp(14), dp(18))
        row.orientation = LinearLayout.HORIZONTAL
        card.addView(row)

        if (iconResId != 0) {
            val icon = FrameLayout(activity)
            addKofiIconLayer(icon, R.drawable.ic_kofi_background, R.color.istan_surface)
            addKofiIconLayer(icon, R.drawable.ic_kofi_body, R.color.istan_olive_dark)
            addKofiIconLayer(icon, R.drawable.ic_kofi_cutout, R.color.istan_surface)
            addKofiIconLayer(icon, R.drawable.ic_kofi_handle, R.color.istan_olive_dark)
            addKofiIconLayer(icon, R.drawable.ic_kofi_heart, R.color.istan_olive)
            val iconParams = LinearLayout.LayoutParams(dp(32), dp(26))
            iconParams.setMargins(0, 0, dp(10), 0)
            row.addView(icon, iconParams)
        }

        val label = text(title, 15, R.color.istan_text, false)
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        card.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(dp(4), dp(20), dp(4), dp(12))
        card.layoutParams = params
        return card
    }

    private fun addKofiIconLayer(icon: FrameLayout, drawableRes: Int, colorRes: Int) {
        val layer = ImageView(activity)
        layer.setImageResource(drawableRes)
        layer.setColorFilter(color(colorRes))
        layer.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.addView(layer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }
    private fun buildCampaignCard(onAction: () -> Unit): View? {
        val prefs = activity.getSharedPreferences("app_prefs", Activity.MODE_PRIVATE)
        if (prefs.getBoolean("playstore_campaign_dismissed", false)) return null

        val dismissCount = prefs.getInt("campaign_dismiss_count", 0)
        if (dismissCount >= 2) return null

        val snoozeUntil = prefs.getLong("campaign_snooze_until", 0L)
        if (System.currentTimeMillis() < snoozeUntil) return null

        val card = MaterialCardView(activity)
        ShadcnDrawables.applyCard(card, ShadcnDrawables.CARD_RADIUS)

        val container = LinearLayout(activity)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(14), dp(12), dp(12), dp(12))
        card.addView(container)

        val headerRow = LinearLayout(activity)
        headerRow.orientation = LinearLayout.HORIZONTAL
        headerRow.gravity = Gravity.CENTER_VERTICAL
        headerRow.isBaselineAligned = false

        val icon = FrameLayout(activity)
        addKofiIconLayer(icon, R.drawable.ic_kofi_background, R.color.istan_surface)
        addKofiIconLayer(icon, R.drawable.ic_kofi_body, R.color.istan_olive_dark)
        addKofiIconLayer(icon, R.drawable.ic_kofi_cutout, R.color.istan_surface)
        addKofiIconLayer(icon, R.drawable.ic_kofi_handle, R.color.istan_olive_dark)
        addKofiIconLayer(icon, R.drawable.ic_kofi_heart, R.color.istan_olive)
        val iconParams = LinearLayout.LayoutParams(dp(26), dp(20))
        iconParams.setMargins(0, 0, dp(10), 0)
        headerRow.addView(icon, iconParams)

        val title = text(activity.getString(R.string.home_campaign_title), 14, R.color.istan_text, true)
        headerRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = ImageView(activity)
        closeBtn.setImageResource(R.drawable.close_24px)
        closeBtn.setColorFilter(color(R.color.istan_text_muted))
        closeBtn.setPadding(dp(6), dp(6), dp(6), dp(6))
        closeBtn.setOnClickListener {
            val count = dismissCount + 1
            val editor = prefs.edit().putInt("campaign_dismiss_count", count)
            if (count >= 2) {
                editor.putBoolean("playstore_campaign_dismissed", true)
            } else {
                editor.putLong("campaign_snooze_until", System.currentTimeMillis() + (14L * 24 * 60 * 60 * 1000))
            }
            editor.apply()
            card.visibility = View.GONE

            val root = activity.findViewById<View>(android.R.id.content) ?: card.rootView
            val snackbar = Snackbar.make(root, activity.getString(R.string.home_campaign_dismissed), Snackbar.LENGTH_LONG)
            snackbar.duration = 4000
            snackbar.view.background = ShadcnDrawables.card(activity, ShadcnDrawables.BUTTON_RADIUS)
            snackbar.view.backgroundTintList = null
            snackbar.view.elevation = 0f
            val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            textView?.apply {
                setTextColor(color(R.color.istan_text))
                typeface = AppFont.regular
                textSize = 14f
                includeFontPadding = false
            }
            val lp = snackbar.view.layoutParams as? ViewGroup.MarginLayoutParams
            lp?.setMargins(dp(16), 0, dp(16), dp(24))
            if (lp != null) snackbar.view.layoutParams = lp
            snackbar.show()
        }
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(dp(32), dp(32)))

        container.addView(headerRow)

        val actionBtn = text(activity.getString(R.string.home_campaign_action), 13, R.color.istan_text, true)
        actionBtn.gravity = Gravity.CENTER
        actionBtn.setTextColor(ShadcnDrawables.primaryForeground(activity))
        val btnGd = GradientDrawable()
        btnGd.setColor(color(R.color.istan_olive))
        btnGd.cornerRadius = dp(ShadcnDrawables.BUTTON_RADIUS).toFloat()
        actionBtn.background = btnGd
        actionBtn.minimumHeight = dp(36)
        actionBtn.setPadding(dp(14), dp(6), dp(14), dp(6))
        val actionLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        actionLp.topMargin = dp(10)
        actionBtn.layoutParams = actionLp
        actionBtn.setOnClickListener { onAction() }
        container.addView(actionBtn)

        val cardParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cardParams.setMargins(dp(4), dp(4), dp(4), dp(12))
        card.layoutParams = cardParams

        return card
    }

    private fun createSectionHeader(title: String): TextView {
        val header = text(title, 12, R.color.istan_text_muted, true)
        header.isAllCaps = true
        header.letterSpacing = 0.1f
        header.setPadding(dp(4), dp(20), dp(4), dp(6))
        return header
    }

    private fun text(value: String, sp: Int, colorRes: Int, bold: Boolean): TextView {
        val textView = TextView(activity)
        textView.text = value
        textView.textSize = sp.toFloat()
        textView.setTextColor(color(colorRes))
        textView.typeface = if (bold) AppFont.semiBold else AppFont.regular
        textView.includeFontPadding = false
        return textView
    }

    private fun color(colorRes: Int): Int = ThemePrefs.resolveColor(activity, colorRes)

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)
}
