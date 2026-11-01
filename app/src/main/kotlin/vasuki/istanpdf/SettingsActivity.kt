package vasuki.istanpdf

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

        val (cameraRow, cameraSubtitle) = settingsRow("Camera app",
            resolveCameraSubtitle(prefs), clickable = true)
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
                    val customApps = detectCustomCameraApps()
                    if (customApps.isEmpty()) {
                        prefs.edit().putString("camera_pkg", "").apply()
                        cameraSubtitle.text = "Default"
                        Toast.makeText(this, "No custom camera apps available", Toast.LENGTH_SHORT).show()
                    } else {
                        showCustomCameraDialog(customApps, prefs, cameraSubtitle)
                    }
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

    private fun queryActivities(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun detectCustomCameraApps(): List<CameraAppInfo> {
        val defaultIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val defaultResolve = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                defaultIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(defaultIntent, 0)
        }
        val defaultPkg = defaultResolve?.activityInfo?.packageName?.takeIf {
            it != "android" && it != packageName
        }

        val candidatePackages = mutableSetOf<String>()

        val stillIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        for (info in queryActivities(stillIntent)) {
            info.activityInfo?.packageName?.let { candidatePackages.add(it) }
        }

        val secureStillIntent = Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE")
        for (info in queryActivities(secureStillIntent)) {
            info.activityInfo?.packageName?.let { candidatePackages.add(it) }
        }

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        for (info in queryActivities(captureIntent)) {
            info.activityInfo?.packageName?.let { candidatePackages.add(it) }
        }

        val secureCaptureIntent = Intent("android.media.action.IMAGE_CAPTURE_SECURE")
        for (info in queryActivities(secureCaptureIntent)) {
            info.activityInfo?.packageName?.let { candidatePackages.add(it) }
        }

        return candidatePackages
            .asSequence()
            .filter { it != packageName && it != "android" && it != defaultPkg }
            .filter { pkg ->
                try {
                    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(
                            pkg,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                    }
                    val hasCamera = packageInfo.requestedPermissions?.contains(android.Manifest.permission.CAMERA) == true ||
                        packageInfo.reqFeatures?.any { it.name == "android.hardware.camera" || it.name == "android.hardware.camera.any" } == true
                    val canCapture = queryActivities(Intent(MediaStore.ACTION_IMAGE_CAPTURE).setPackage(pkg)).isNotEmpty()
                    hasCamera && canCapture
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            }
            .mapNotNull { pkg ->
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val label = packageManager.getApplicationLabel(appInfo)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    val icon = packageManager.getApplicationIcon(appInfo)
                    CameraAppInfo(pkg, label, icon)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun resolveCameraSubtitle(prefs: android.content.SharedPreferences): String {
        val currentCameraPkg = prefs.getString("camera_pkg", "") ?: ""
        if (currentCameraPkg.isEmpty()) return "Default"
        return try {
            val appInfo = packageManager.getApplicationInfo(currentCameraPkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            prefs.edit().putString("camera_pkg", "").apply()
            "Default"
        }
    }

    private fun showCustomCameraDialog(
        apps: List<CameraAppInfo>,
        prefs: android.content.SharedPreferences,
        subtitle: TextView
    ) {
        val currentPkg = prefs.getString("camera_pkg", "") ?: ""
        val dialog = android.app.Dialog(this)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ShadcnDrawables.card(this@SettingsActivity, ShadcnDrawables.DIALOG_RADIUS)
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        val titleView = text("Select Camera App", 20, R.color.istan_text, true).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }
        root.addView(titleView)

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (app in apps) {
            val isSelected = app.packageName == currentPkg
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                setBackgroundResource(typedValue.resourceId)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.edit()
                        .putString("camera_pkg", app.packageName)
                        .apply()
                    subtitle.text = app.label
                    dialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Camera app set to ${app.label}", Toast.LENGTH_SHORT).show()
                }
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(iconView, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(14)
            })

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val appLabel = text(app.label, 15, R.color.istan_text, true)
            val pkgLabel = text(app.packageName, 12, R.color.istan_text_muted, false).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            textColumn.addView(appLabel)
            textColumn.addView(pkgLabel)
            row.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (isSelected) {
                val checkIcon = ImageView(this).apply {
                    setImageResource(R.drawable.check_24px)
                    setColorFilter(color(R.color.istan_olive))
                }
                row.addView(checkIcon, LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    marginStart = dp(8)
                })
            }

            listContainer.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        scroll.addView(listContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scrollLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (apps.size > 4) (resources.displayMetrics.heightPixels * 0.45).toInt()
            else ViewGroup.LayoutParams.WRAP_CONTENT
        )
        root.addView(scroll, scrollLp)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(16), 0, 0)
        }
        val cancelBtn = text("Cancel", 14, R.color.istan_text_muted, true).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(cancelBtn)
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private data class CameraAppInfo(
        val packageName: String,
        val label: String,
        val icon: Drawable
    )

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


    private companion object {
        val THEME_LABELS = listOf("System default", "Light", "Dark")
    }
}
