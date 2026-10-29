package vasuki.istanpdf

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import vasuki.istanpdf.libreoffice.LibreOfficeManager
import vasuki.istanpdf.presentation.RoundedDropdown
import vasuki.istanpdf.presentation.ShadcnDrawables

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        buildSettings(0)
        applySystemBarTheme()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ThemePrefs.themeMode(this) == ThemePrefs.THEME_AUTO) {
            applySystemBarTheme()
            buildSettings(0)
        }
    }

    private fun buildSettings(scrollY: Int) {
        val outer = FrameLayout(this)
        outer.setBackgroundColor(color(R.color.istan_background))

        ViewCompat.setOnApplyWindowInsetsListener(outer) { v, windowInsets ->
            val insets: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, 0)
            WindowInsetsCompat.CONSUMED
        }

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(page, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(24), dp(20), dp(8))
        }
        page.addView(titleRow)

        val backArrow = ImageView(this).apply {
            setImageResource(R.drawable.arrow_back_24px)
            setColorFilter(color(R.color.istan_text))
            contentDescription = "Navigate up"
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { finish() }
        }
        titleRow.addView(backArrow, LinearLayout.LayoutParams(dp(48), dp(48)))

        val title = text("Settings", 28, R.color.istan_text, true).apply {
            includeFontPadding = false
            typeface = AppFont.medium
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        page.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        scrollView.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(settingsContent(scrollView))
        content.addView(View(this), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(developerFooter(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(outer)
        ViewCompat.requestApplyInsets(outer)
        scrollView.post { scrollView.scrollTo(0, scrollY) }
    }

    private fun settingsContent(scrollView: ScrollView): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val mode = ThemePrefs.themeMode(this)

        body.addView(categoryHeader("APPEARANCE"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
            bottomMargin = dp(8)
        })

        val appearanceGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ShadcnDrawables.card(this@SettingsActivity, ShadcnDrawables.CARD_RADIUS)
            clipToOutline = true
        }

        val (themeRow, _) = settingsRow("Theme", THEME_LABELS[mode], clickable = true)
        val themeChevron = ImageView(this).apply {
            setImageResource(R.drawable.chevron_right_24px)
            setColorFilter(color(R.color.istan_text_muted))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        themeRow.addView(themeChevron, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginStart = dp(8)
        })
        themeRow.setOnClickListener {
            RoundedDropdown(this, themeRow, THEME_LABELS, ThemePrefs.themeMode(this)) { index ->
                if (ThemePrefs.themeMode(this) != index) {
                    ThemePrefs.setThemeMode(this, index)
                    applySystemBarTheme()
                    buildSettings(scrollView.scrollY)
                }
            }.show()
        }
        appearanceGroup.addView(themeRow)

        if (ThemePrefs.isDark(this)) {
            val (hcmRow, _) = settingsRow("HCM", "High Contrast Mode")
            hcmRow.addView(styledSwitch(
                checked = ThemePrefs.isHighContrast(this),
                onToggle = { isChecked ->
                    ThemePrefs.setHighContrast(this, isChecked)
                    applySystemBarTheme()
                    buildSettings(scrollView.scrollY)
                }
            ))
            appearanceGroup.addView(hcmRow)
        }

        body.addView(appearanceGroup, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(32)
        })

        body.addView(categoryHeader("GENERAL"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
            bottomMargin = dp(8)
        })

        val generalGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ShadcnDrawables.card(this@SettingsActivity, ShadcnDrawables.CARD_RADIUS)
            clipToOutline = true
        }

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        val (updateRow, _) = settingsRow("Check for updates", "Notify when a new version is available")
        updateRow.addView(styledSwitch(
            checked = prefs.getBoolean("check_updates", true),
            onToggle = { isChecked -> prefs.edit().putBoolean("check_updates", isChecked).apply() }
        ))
        generalGroup.addView(updateRow)

        if (LibreOfficeManager.isEngineInstalled(this)) {
            val (perfRow, _) = settingsRow("LibreOffice on startup", "Pre-load engine for faster DOCX conversion")
            perfRow.addView(styledSwitch(
                checked = prefs.getBoolean("improve_docx_perf", false),
                onToggle = { isChecked -> prefs.edit().putBoolean("improve_docx_perf", isChecked).apply() }
            ))
            generalGroup.addView(perfRow)
        }

        val currentCameraPkg = prefs.getString("camera_pkg", "") ?: ""
        val (cameraRow, cameraSubtitle) = settingsRow("Camera app",
            if (currentCameraPkg.isEmpty()) "Default" else "Custom", clickable = true)
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.chevron_right_24px)
            setColorFilter(color(R.color.istan_text_muted))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        cameraRow.addView(chevron, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginStart = dp(8)
        })
        cameraRow.setOnClickListener {
            val options = listOf("Default", "Custom")
            val selected = if ((prefs.getString("camera_pkg", "") ?: "").isEmpty()) 0 else 1
            RoundedDropdown(this, cameraRow, options, selected) { index ->
                if (index == 0) {
                    prefs.edit().putString("camera_pkg", "").apply()
                    cameraSubtitle.text = "Default"
                } else {
                    showCameraPackageDialog(prefs, cameraSubtitle)
                }
            }.show()
        }
        generalGroup.addView(cameraRow)

        body.addView(generalGroup, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(32)
        })

        body.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return body
    }

    private fun settingsRow(title: String, subtitle: String, clickable: Boolean = false): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (clickable) {
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                setBackgroundResource(typedValue.resourceId)
            }
        }

        val leftColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftColumn.addView(text(title, 16, R.color.istan_text, true))

        val subtitleView = text(subtitle, 14, R.color.istan_text_muted, false).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
        }
        leftColumn.addView(subtitleView)

        row.addView(leftColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        return Pair(row, subtitleView)
    }

    private fun styledSwitch(checked: Boolean, onToggle: (Boolean) -> Unit): MaterialSwitch =
        MaterialSwitch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ShadcnDrawables.primaryForeground(this@SettingsActivity), color(R.color.istan_text_muted))
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(color(R.color.istan_olive), color(R.color.istan_outline))
            )
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }

    private fun categoryHeader(label: String): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            typeface = AppFont.semiBold
            setTextColor(color(R.color.istan_text_muted))
            letterSpacing = 0.05f
            isAllCaps = true
            includeFontPadding = false
        }

    private fun showCameraPackageDialog(prefs: android.content.SharedPreferences, subtitle: TextView) {
        val current = prefs.getString("camera_pkg", "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString("camera_pkg_last", "") ?: "")

        val editText = EditText(this).apply {
            hint = "e.g. com.google.android.GoogleCamera"
            setText(current)
            textSize = 14f
            typeface = AppFont.regular
            setTextColor(color(R.color.istan_text))
            setHintTextColor(color(R.color.istan_text_muted))
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            isSingleLine = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = ShadcnDrawables.input(this@SettingsActivity, ShadcnDrawables.BUTTON_RADIUS)
            setSelection(current.length)
        }
        tintEditText(editText, color(R.color.istan_olive))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
            addView(editText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val dialog = android.app.Dialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ShadcnDrawables.card(this@SettingsActivity, ShadcnDrawables.DIALOG_RADIUS)
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        val titleView = text("Custom Camera App", 20, R.color.istan_text, true)
        root.addView(titleView)
        root.addView(content)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(20), 0, 0)
        }
        val cancelBtn = text("Cancel", 14, R.color.istan_text_muted, true).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { dialog.dismiss() }
        }
        val saveBtn = text("Save", 14, R.color.istan_olive, true).apply {
            setPadding(dp(12), dp(12), 0, dp(12))
            setOnClickListener {
                dialog.dismiss()
                validateAndSaveCameraPkg(editText.text.toString(), prefs, subtitle)
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(saveBtn)
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
        editText.requestFocus()
    }

    private fun validateAndSaveCameraPkg(pkg: String, prefs: android.content.SharedPreferences, subtitle: TextView) {
        val trimmed = pkg.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().putString("camera_pkg", "").apply()
            subtitle.text = "Default"
            return
        }
        val probe = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { setPackage(trimmed) }
        val canCapture = packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) != null
        if (!canCapture) {
            val installed = try { packageManager.getApplicationInfo(trimmed, 0); true }
                            catch (e: PackageManager.NameNotFoundException) { false }
            val msg = if (installed) "\"$trimmed\" is not a camera app"
                      else "App not installed: $trimmed"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }
        val defaultPkg = packageManager
            .resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (trimmed == defaultPkg) {
            prefs.edit()
                .putString("camera_pkg", "")
                .remove("camera_pkg_last").apply()
            subtitle.text = "Default"
            Toast.makeText(this, "That is already your default camera. Set to Default.", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit()
            .putString("camera_pkg", trimmed)
            .putString("camera_pkg_last", trimmed).apply()
        subtitle.text = "Custom"
        Toast.makeText(this, "Camera set to $trimmed", Toast.LENGTH_SHORT).show()
    }

    private fun developerFooter(): View {
        val footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val credit = TextView(this).apply {
            text = "Dev By\nRamakanth"
            textSize = 38f
            setTextColor(color(R.color.istan_text))
            typeface = AppFont.semiBold
            gravity = Gravity.START
            includeFontPadding = false
            setLineSpacing(0f, 0.88f)
        }
        footer.addView(credit)

        val crafted = "Crafted with "
        val heart = "\u2764\uFE0F"
        val location = " in Bengaluru, India"
        val attribution = SpannableString(crafted + heart + location)
        val locationStart = crafted.length + heart.length
        attribution.setSpan(AssetTypefaceSpan(AppFont.regular), 0, locationStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        attribution.setSpan(ForegroundColorSpan(color(R.color.istan_text_muted)), 0, locationStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        attribution.setSpan(ForegroundColorSpan(color(R.color.istan_text)),
            locationStart, attribution.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        attribution.setSpan(AssetTypefaceSpan(AppFont.semiBold), locationStart, attribution.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val attributionView = TextView(this).apply {
            setText(attribution)
            textSize = 15f
            setTextColor(color(R.color.istan_text_muted))
            typeface = AppFont.regular
            gravity = Gravity.START
            includeFontPadding = false
            isSingleLine = true
        }
        val attributionLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        }
        footer.addView(attributionView, attributionLp)
        return footer
    }

    private class AssetTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) { paint.typeface = typeface }
        override fun updateMeasureState(paint: TextPaint) { paint.typeface = typeface }
    }

    private fun text(value: String, sp: Int, colorRes: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(color(colorRes))
            typeface = if (bold) AppFont.semiBold else AppFont.regular
            includeFontPadding = true
        }

    private fun color(colorRes: Int): Int = ThemePrefs.resolveColor(this, colorRes)

    private fun applySystemBarTheme() {
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val lightBars = !ThemePrefs.isDark(this)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun tintEditText(editText: EditText, accentColor: Int) {
        val tint = ColorStateList.valueOf(accentColor)
        editText.highlightColor = Color.argb(76, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.textCursorDrawable?.mutate()?.also {
                it.setTint(accentColor)
                editText.setTextCursorDrawable(it)
            }
            editText.textSelectHandle?.mutate()?.also {
                it.setTintList(tint)
                editText.setTextSelectHandle(it)
            }
            editText.textSelectHandleLeft?.mutate()?.also {
                it.setTintList(tint)
                editText.setTextSelectHandleLeft(it)
            }
            editText.textSelectHandleRight?.mutate()?.also {
                it.setTintList(tint)
                editText.setTextSelectHandleRight(it)
            }
        } else {
            try {
                val editorField = android.widget.TextView::class.java.getDeclaredField("mEditor")
                editorField.isAccessible = true
                val editor = editorField.get(editText)
                val editorClass = editor.javaClass
                for (handleField in arrayOf("mSelectHandleLeft", "mSelectHandleRight", "mSelectHandleCenter")) {
                    try {
                        val f = editorClass.getDeclaredField(handleField)
                        f.isAccessible = true
                        (f.get(editor) as? Drawable)?.mutate()?.setTintList(tint)
                    } catch (_: Exception) {}
                }
                val cursorField = android.widget.TextView::class.java.getDeclaredField("mCursorDrawableRes")
                cursorField.isAccessible = true
                val drawableField = editorClass.getDeclaredField("mCursorDrawable")
                drawableField.isAccessible = true
                val drawables = drawableField.get(editor)
                if (drawables is Array<*>) {
                    drawables.filterIsInstance<Drawable>().forEach { it.mutate().setTint(accentColor) }
                }
            } catch (_: Exception) {}
        }
    }

    private companion object {
        val THEME_LABELS = listOf("System default", "Light", "Dark")
    }
}
