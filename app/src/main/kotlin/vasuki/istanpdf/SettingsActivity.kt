package vasuki.istanpdf

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import vasuki.istanpdf.libreoffice.LibreOfficeManager

class SettingsActivity : AppCompatActivity() {

    private val accentCloseHandler = Handler(Looper.getMainLooper())
    private var accentCloseRunnable: Runnable? = null
    private var activeAccentPill: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
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

    override fun onDestroy() {
        accentCloseRunnable?.let { accentCloseHandler.removeCallbacks(it) }
        accentCloseRunnable = null
        activeAccentPill?.dismiss()
        activeAccentPill = null
        super.onDestroy()
    }

    private fun applyAccentAndScheduleClose(index: Int, scrollView: ScrollView) {
        accentCloseRunnable?.let { accentCloseHandler.removeCallbacks(it) }
        ThemePrefs.setAccentIndex(this, index)
        buildSettings(scrollView.scrollY)
        if (ThemePrefs.isDynamicIcon(this)) {
            val toast = Toast.makeText(this, "Applying new accent. App will close to refresh the icon", Toast.LENGTH_SHORT)
            toast.show()
            accentCloseRunnable = Runnable {
                toast.cancel()
                ThemePrefs.applyLauncherIconAndKill(applicationContext)
            }
            accentCloseHandler.postDelayed(accentCloseRunnable!!, 1500)
        }
    }

    private fun buildSettings(scrollY: Int) {
        val outer = FrameLayout(this)
        outer.setBackgroundColor(color(R.color.istan_background))

        ViewCompat.setOnApplyWindowInsetsListener(outer) { v, windowInsets ->
            val insets: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
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
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(16))
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

        val footer = developerFooter()
        footer.setPadding(0, dp(24), 0, dp(24))
        content.addView(footer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(outer)
        ViewCompat.requestApplyInsets(outer)
        scrollView.post { scrollView.scrollTo(0, scrollY) }
    }

    private fun settingsContent(scrollView: ScrollView): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val selected = ThemePrefs.accentIndex(this)
        val amoled = ThemePrefs.isAmoled(this)

        body.addView(categoryHeader("APPEARANCE"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
            bottomMargin = dp(8)
        })

        val appearanceGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(color(R.color.istan_surface), dp(16), dp(1))
            clipToOutline = true
            setPadding(0, dp(8), 0, dp(8))
        }

        val accentRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        accentRow.addView(text("Accent color", 16, R.color.istan_text, true))

        val circles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        for (i in ThemePrefs.ACCENTS.indices) {
            val circle = QuadrantCircleView(this).apply {
                bind(ThemePrefs.ACCENTS[i], selected == i, amoled)
                setOnClickListener {
                    if (ThemePrefs.accentIndex(this@SettingsActivity) == i) return@setOnClickListener
                    applyAccentAndScheduleClose(i, scrollView)
                }
                setOnLongClickListener {
                    showAccentPill(this, ThemePrefs.ACCENTS[i].name)
                    true
                }
            }
            circles.addView(circle, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        accentRow.addView(circles, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        appearanceGroup.addView(accentRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        val themeRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val themeTitle = text("Theme", 16, R.color.istan_text, true)
        themeRow.addView(themeTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val modes = FrameLayout(this).apply {
            background = roundedBackground(color(R.color.istan_surface_high), dp(22), 0)
        }
        themeRow.addView(modes, LinearLayout.LayoutParams(dp(204), dp(44)))

        val mode = ThemePrefs.themeMode(this)
        val modeGroup = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        modes.addView(modeGroup, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val auto = addThemeModeButton(modeGroup, "Auto", ThemePrefs.THEME_AUTO, mode)
        val light = addThemeModeButton(modeGroup, "Light", ThemePrefs.THEME_LIGHT, mode)
        val dark = addThemeModeButton(modeGroup, "Dark", ThemePrefs.THEME_DARK, mode)

        modeGroup.check(when (mode) {
            ThemePrefs.THEME_AUTO -> auto.id
            ThemePrefs.THEME_LIGHT -> light.id
            else -> dark.id
        })
        modeGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val option = group.findViewById<View>(checkedId).tag as Int
            if (ThemePrefs.themeMode(this) == option) return@addOnButtonCheckedListener
            ThemePrefs.setThemeMode(this, option)
            applySystemBarTheme()
            buildSettings(scrollView.scrollY)
        }
        addThemeDivider(modes, 1, mode)
        addThemeDivider(modes, 2, mode)
        appearanceGroup.addView(themeRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        val (dynamicIconRow, _) = settingsRow("Dynamic icon", "Match launcher icon to accent")
        dynamicIconRow.addView(styledSwitch(
            checked = ThemePrefs.isDynamicIcon(this@SettingsActivity),
            onToggle = { isChecked ->
                ThemePrefs.setDynamicIcon(this@SettingsActivity, isChecked)
                if (ThemePrefs.accentIndex(this@SettingsActivity) != 0) {
                    accentCloseRunnable?.let { accentCloseHandler.removeCallbacks(it) }
                    val msg = if (isChecked) "Applying accent icon. App will close to refresh the icon"
                              else "Reverting to default icon. App will close to refresh the icon"
                    val toast = Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_SHORT)
                    toast.show()
                    accentCloseRunnable = Runnable {
                        toast.cancel()
                        if (isChecked) {
                            ThemePrefs.applyLauncherIconAndKill(applicationContext)
                        } else {
                            ThemePrefs.resetLauncherIconToDefault(applicationContext, killApp = true)
                        }
                    }
                    accentCloseHandler.postDelayed(accentCloseRunnable!!, 1500)
                }
            }
        ))
        appearanceGroup.addView(dynamicIconRow)

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
            background = roundedBackground(color(R.color.istan_surface), dp(16), dp(1))
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
        cameraRow.setOnClickListener { showCameraDropdown(cameraRow, cameraSubtitle, prefs) }
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
                intArrayOf(color(R.color.istan_olive), color(R.color.istan_text_muted))
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ThemePrefs.tint(color(R.color.istan_olive), color(R.color.istan_background), 0.5f), color(R.color.istan_outline))
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

    private fun showCameraDropdown(anchor: View, subtitle: TextView, prefs: android.content.SharedPreferences) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(color(R.color.istan_surface))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), color(R.color.istan_outline))
            }
            elevation = dp(4).toFloat()
            setPadding(0, dp(4), 0, dp(4))
        }

        fun option(label: String, action: () -> Unit): TextView = text(label, 14, R.color.istan_text, false).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { action() }
        }

        val popupRef = arrayOfNulls<PopupWindow>(1)

        container.addView(option("Default") {
            popupRef[0]?.dismiss()
            prefs.edit().putString("camera_pkg", "").apply()
            subtitle.text = "Default"
        })
        container.addView(View(this).apply {
            setBackgroundColor(color(R.color.istan_outline))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(dp(12), 0, dp(12), 0)
        })
        container.addView(option("Custom") {
            popupRef[0]?.dismiss()
            showCameraPackageDialog(prefs, subtitle)
        })

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popup = PopupWindow(container, container.measuredWidth.coerceAtLeast(dp(140)), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(4).toFloat()
        }
        popupRef[0] = popup

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY,
            location[0] + anchor.width - container.measuredWidth.coerceAtLeast(dp(140)),
            location[1] + anchor.height + dp(4))
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
            background = roundedBackground(color(R.color.istan_surface_high), dp(12), dp(1))
            setSelection(current.length)
        }

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
            background = GradientDrawable().apply {
                setColor(color(R.color.istan_surface))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), color(R.color.istan_outline))
            }
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
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { dialog.dismiss() }
        }
        val saveBtn = text("Save", 14, R.color.istan_olive, true).apply {
            setPadding(dp(12), dp(8), 0, dp(8))
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

    private fun showAccentPill(anchor: View, name: String) {
        activeAccentPill?.dismiss()
        activeAccentPill = null
        val label = text(name, 12, R.color.istan_text, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(color(R.color.istan_surface_high))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), color(R.color.istan_outline))
            }
        }

        label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val pill = PopupWindow(label, label.measuredWidth, label.measuredHeight, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = false
            elevation = dp(4).toFloat()
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val x = location[0] + (anchor.width - label.measuredWidth) / 2
        val y = location[1] - label.measuredHeight - dp(8)
        activeAccentPill = pill
        pill.showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
        anchor.postDelayed({
            pill.dismiss()
            if (activeAccentPill == pill) activeAccentPill = null
        }, 1500)
    }

    private fun addThemeDivider(modes: FrameLayout, boundary: Int, selected: Int) {
        if (selected == boundary - 1 || selected == boundary) return
        val divider = View(this).apply {
            setBackgroundColor(ThemePrefs.tint(color(R.color.istan_outline),
                color(R.color.istan_surface_high), 0.82f))
        }
        val dividerLp = FrameLayout.LayoutParams(dp(1), dp(20)).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            leftMargin = dp(68 * boundary)
        }
        modes.addView(divider, dividerLp)
    }

    private fun addThemeModeButton(
        group: MaterialButtonToggleGroup,
        label: String,
        option: Int,
        selected: Int
    ): MaterialButton {
        val isSelected = option == selected
        return MaterialButton(this).apply {
            id = View.generateViewId()
            tag = option
            text = label
            textSize = 15f
            typeface = AppFont.semiBold
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            gravity = Gravity.CENTER
            isAllCaps = false
            isCheckable = true
            contentDescription = label + " theme" + if (isSelected) ", selected" else ""
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(if (ThemePrefs.isAmoled(this@SettingsActivity)) Color.BLACK else Color.WHITE, color(R.color.istan_text_muted))))
            backgroundTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(color(R.color.istan_olive), Color.TRANSPARENT))
            strokeWidth = 0
            setInsetTop(dp(4))
            setInsetBottom(dp(4))
            cornerRadius = dp(18)
            minWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            group.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun developerFooter(): View {
        val footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val credit = TextView(this).apply {
            text = "Dev By\nRamakanth"
            textSize = 38f
            setTextColor(if (ThemePrefs.isAmoled(this@SettingsActivity)) 0xFFCAC4D0.toInt() else 0xFF1C1B1F.toInt())
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
        attribution.setSpan(ForegroundColorSpan(ThemePrefs.tint(color(R.color.istan_text_muted),
            color(R.color.istan_background), 0.26f)), 0, locationStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        attribution.setSpan(ForegroundColorSpan(if (ThemePrefs.isAmoled(this)) 0xFFCAC4D0.toInt() else 0xFF1C1B1F.toInt()),
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

    private fun roundedBackground(color: Int, radius: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, color(R.color.istan_outline))
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
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val lightBars = !ThemePrefs.isAmoled(this)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
