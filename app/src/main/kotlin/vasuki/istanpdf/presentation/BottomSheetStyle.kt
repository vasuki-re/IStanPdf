package vasuki.istanpdf.presentation

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import vasuki.istanpdf.R
import vasuki.istanpdf.ThemePrefs

object BottomSheetStyle {
    fun dialog(activity: Activity): BottomSheetDialog {
        val theme = if (ThemePrefs.isDark(activity)) {
            R.style.Theme_IStan_BottomSheetDialog_Dark
        } else {
            R.style.Theme_IStan_BottomSheetDialog_Light
        }
        return BottomSheetDialog(activity, theme)
    }

    fun apply(activity: Activity, dialog: BottomSheetDialog, sheet: View) {
        val sheetColor = ThemePrefs.resolveColor(activity, R.color.istan_sheet)
        sheet.backgroundTintList = android.content.res.ColorStateList.valueOf(sheetColor)
        val window = dialog.window ?: return
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = sheetColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val controller = WindowCompat.getInsetsController(window, sheet)
        controller.isAppearanceLightNavigationBars = !ThemePrefs.isDark(activity)
    }
}
