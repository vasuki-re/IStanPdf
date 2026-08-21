package vasuki.istanpdf.presentation

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
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
import vasuki.istanpdf.R
import vasuki.istanpdf.ThemePrefs

class HomeViewBuilder(
    private val activity: Activity,
    private val regularFont: Typeface,
    private val boldFont: Typeface
) {

    interface HomeActions {
        fun onMergePdf()
        fun onModifyPdf()
        fun onCompressPdf()
        fun onImageToPdf()
        fun onPdfToImage()
        fun onDocxToPdf()
        fun onMdToPdf()
        fun onDocxRemovePages()
        fun onDocxReorderPages()
        fun onSupportDeveloper()
        fun onOpenSettings()
    }

    companion object {
        private const val WAITING_TEXT = "Ready"
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
            v.setPadding(0, insets.top, 0, insets.bottom)
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
        root.setPadding(dp(20), dp(32), dp(20), dp(16))

        scrollView.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val heroRow = LinearLayout(activity)
        heroRow.orientation = LinearLayout.HORIZONTAL
        heroRow.gravity = Gravity.CENTER_VERTICAL
        root.addView(heroRow)

        val title = text("", 40, R.color.istan_text, true).apply { letterSpacing = -0.02f }
        val ss = SpannableString("IStanPdf")
        ss.setSpan(ForegroundColorSpan(color(R.color.istan_text)), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val isMonochrome = ThemePrefs.accent(activity).name == "Monochrome"
        val isDark = ThemePrefs.isAmoled(activity)
        val pdfColor = if (isMonochrome) {
            if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        } else {
            color(R.color.istan_olive)
        }
        ss.setSpan(ForegroundColorSpan(pdfColor), 5, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        title.text = ss
        heroRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val settingsButton = MaterialCardView(activity)
        settingsButton.setCardBackgroundColor(color(R.color.istan_surface))
        settingsButton.radius = dp(100).toFloat()
        settingsButton.strokeWidth = dp(1)
        settingsButton.strokeColor = color(R.color.istan_outline)
        settingsButton.cardElevation = 0f
        settingsButton.useCompatPadding = false
        settingsButton.setOnClickListener { actions.onOpenSettings() }

        val menuIcon = ImageView(activity)
        menuIcon.setImageResource(R.drawable.menu)
        menuIcon.setColorFilter(color(R.color.istan_olive_dark))
        menuIcon.setPadding(dp(8), dp(8), dp(8), dp(8))
        settingsButton.addView(menuIcon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))

        val settingsLp = LinearLayout.LayoutParams(dp(48), dp(48))
        settingsLp.setMargins(dp(16), 0, 0, 0)
        heroRow.addView(settingsButton, settingsLp)

        val subtitle = text("Offline app for PDF and DOCX operations", 16, R.color.istan_text_muted, false)
        subtitle.setPadding(0, dp(4), 0, dp(48))
        root.addView(subtitle)

        root.addView(createSectionHeader("PDF TOOLS"))
        val pdfRow1 = LinearLayout(activity)
        pdfRow1.orientation = LinearLayout.HORIZONTAL
        pdfRow1.addView(dashboardCard("Merge PDF", R.drawable.merge_24px, actions::onMergePdf))
        pdfRow1.addView(dashboardCard("Modify PDF", R.drawable.modify_pdf_24px, actions::onModifyPdf))
        root.addView(pdfRow1)

        val compressCard = dashboardCard("Compress PDF", R.drawable.compress_pdf_24px, actions::onCompressPdf)
        (compressCard.layoutParams as LinearLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            weight = 0f
        }
        root.addView(compressCard)

        root.addView(createSectionHeader("CONVERSIONS"))
        val convRow1 = LinearLayout(activity)
        convRow1.orientation = LinearLayout.HORIZONTAL
        convRow1.addView(dashboardCard("Image to PDF", R.drawable.img2pdf_24px, actions::onImageToPdf))
        convRow1.addView(dashboardCard("PDF to Image", R.drawable.pdf2img_24px, actions::onPdfToImage))
        root.addView(convRow1)

        val convRow2 = LinearLayout(activity)
        convRow2.orientation = LinearLayout.HORIZONTAL
        convRow2.addView(dashboardCard("DOCX to PDF", R.drawable.docx2pdf_24px, actions::onDocxToPdf))
        convRow2.addView(dashboardCard("MD to PDF", R.drawable.md2pdf_24px, actions::onMdToPdf))
        root.addView(convRow2)

        root.addView(createSectionHeader("DOCX TOOLS"))
        val docxRow1 = LinearLayout(activity)
        docxRow1.orientation = LinearLayout.HORIZONTAL
        docxRow1.addView(dashboardCard("Remove Pages", R.drawable.remove_page_docx_24px, actions::onDocxRemovePages))
        docxRow1.addView(dashboardCard("Reorder Pages", R.drawable.reorder_docx_24px, actions::onDocxReorderPages))
        root.addView(docxRow1)

        root.addView(kofiCard("Support the Developer", R.drawable.ic_kofi, actions::onSupportDeveloper))

        val topSpacer = View(activity)
        root.addView(topSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f))

        val statusCard = LinearLayout(activity)
        statusCard.orientation = LinearLayout.HORIZONTAL
        statusCard.gravity = Gravity.CENTER
        statusCard.isBaselineAligned = false
        statusCard.setPadding(dp(16), dp(8), dp(24), dp(8))
        val statusBg = GradientDrawable()
        statusBg.setColor(color(R.color.istan_surface))
        statusBg.cornerRadius = dp(32).toFloat()
        statusBg.setStroke(dp(1), color(R.color.istan_outline))
        statusCard.background = statusBg

        statusIndicator = ImageView(activity)
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(color(R.color.istan_olive))
        statusIndicator!!.setImageDrawable(dot)
        statusCard.addView(statusIndicator, LinearLayout.LayoutParams(dp(16), dp(16)))

        status = text(WAITING_TEXT, 16, R.color.istan_olive, false)
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
        scParams.setMargins(0, dp(16), 0, dp(24))
        scParams.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(statusCard, scParams)

        val versionName = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            e.printStackTrace()
            ""
        }
        val footerText = text(versionName, 16, R.color.istan_text_muted, false)
        val ftParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        ftParams.gravity = Gravity.CENTER_HORIZONTAL
        ftParams.setMargins(0, 0, 0, dp(4))
        root.addView(footerText, ftParams)

        return mainContainer
    }

    private fun dashboardCard(title: String, iconResId: Int, action: () -> Unit): View {
        val card = MaterialCardView(activity)
        card.setCardBackgroundColor(color(R.color.istan_surface))
        card.radius = dp(16).toFloat()
        card.strokeWidth = dp(1)
        card.strokeColor = color(R.color.istan_outline)
        card.cardElevation = 0f
        card.useCompatPadding = false

        val row = LinearLayout(activity)
        row.gravity = Gravity.CENTER_VERTICAL
        row.isBaselineAligned = false
        row.setPadding(dp(10), dp(20), dp(4), dp(20))
        row.orientation = LinearLayout.HORIZONTAL
        card.addView(row)

        if (iconResId != 0) {
            val icon = ImageView(activity)
            icon.setImageResource(iconResId)
            icon.setColorFilter(color(R.color.istan_olive_dark))
            val iconParams = LinearLayout.LayoutParams(dp(26), dp(26))
            iconParams.setMargins(0, 0, dp(6), 0)
            row.addView(icon, iconParams)
        }

        val label = text(title, 15, R.color.istan_text, false)
        label.maxLines = 1
        label.isSingleLine = true
        label.ellipsize = TextUtils.TruncateAt.END
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val chevron = ImageView(activity)
        chevron.setImageResource(R.drawable.chevron_right_24px)
        chevron.setColorFilter(color(R.color.istan_text_muted))
        val chevronParams = LinearLayout.LayoutParams(dp(18), dp(18))
        chevronParams.gravity = Gravity.CENTER_VERTICAL
        chevronParams.setMargins(dp(2), 0, dp(2), 0)
        row.addView(chevron, chevronParams)

        card.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        params.setMargins(dp(8), dp(8), dp(8), dp(8))
        card.layoutParams = params
        return card
    }

    private fun kofiCard(title: String, iconResId: Int, action: () -> Unit): View {
        val card = MaterialCardView(activity)
        card.setCardBackgroundColor(color(R.color.istan_surface))
        card.radius = dp(16).toFloat()
        card.strokeWidth = dp(1)
        card.strokeColor = color(R.color.istan_outline)
        card.cardElevation = 0f
        card.useCompatPadding = false

        val row = LinearLayout(activity)
        row.gravity = Gravity.CENTER_VERTICAL
        row.isBaselineAligned = false
        row.setPadding(dp(12), dp(20), dp(16), dp(20))
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
        params.setMargins(dp(8), dp(8), dp(8), dp(16))
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

    private fun createSectionHeader(title: String): TextView {
        val header = text(title, 12, R.color.istan_text_muted, true)
        header.isAllCaps = true
        header.letterSpacing = 0.1f
        header.setPadding(dp(8), dp(32), 0, dp(8))
        return header
    }

    private fun text(value: String, sp: Int, colorRes: Int, bold: Boolean): TextView {
        val textView = TextView(activity)
        textView.text = value
        textView.textSize = sp.toFloat()
        textView.setTextColor(color(colorRes))
        textView.typeface = if (bold) boldFont else regularFont
        textView.includeFontPadding = false
        return textView
    }

    private fun color(colorRes: Int): Int = ThemePrefs.resolveColor(activity, colorRes)

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)
}
