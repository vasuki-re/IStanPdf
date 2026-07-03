package vasuki.istanpdf;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SettingsActivity extends AppCompatActivity {
    private Typeface regularFont;
    private Typeface boldFont;
    private final android.os.Handler iconHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable iconRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        
        regularFont = Typeface.createFromAsset(getAssets(), "vasuki.ttf");
        boldFont = Typeface.createFromAsset(getAssets(), "vasuki_bold.ttf");
        
        buildSettings(0);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (ThemePrefs.themeMode(this) == ThemePrefs.THEME_AUTO) {
            applySystemBarTheme();
            buildSettings(0);
        }
    }

    private void buildSettings(int scrollY) {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(color(R.color.istan_background));

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(outer, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        outer.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(12), dp(8), dp(22), dp(8));
        page.addView(titleRow);

        TextView backArrow = text("←", 28, R.color.istan_text, true);
        backArrow.setContentDescription("Navigate up");
        backArrow.setGravity(Gravity.CENTER);
        backArrow.setOnClickListener(v -> finish());
        titleRow.addView(backArrow, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("Settings", 22, R.color.istan_text, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        View separator = new View(this);
        separator.setBackgroundColor(ThemePrefs.isAmoled(this) ? 0xFF333333 : 0xFFB4B8AA);
        page.addView(separator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, 0, 0, dp(208));
        page.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(22));
        scrollView.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(settingsContent(scrollView));

        View footer = developerFooter();
        FrameLayout.LayoutParams footerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        footerLp.setMargins(dp(22), 0, dp(22), dp(40));
        outer.addView(footer, footerLp);
        setContentView(outer);
        androidx.core.view.ViewCompat.requestApplyInsets(outer);
        scrollView.post(() -> scrollView.scrollTo(0, scrollY));
    }

    private View settingsContent(ScrollView scrollView) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        int selected = ThemePrefs.accentIndex(this);
        boolean amoled = ThemePrefs.isAmoled(this);

        LinearLayout accentHeader = new LinearLayout(this);
        accentHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams accentHeaderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        body.addView(accentHeader, accentHeaderLp);

        TextView accentTitle = text("Accent", 18, R.color.istan_text, true);
        accentHeader.addView(accentTitle);

        LinearLayout circles = new LinearLayout(this);
        circles.setOrientation(LinearLayout.HORIZONTAL);
        circles.setGravity(Gravity.CENTER);
        circles.setPadding(0, dp(12), 0, 0);
        body.addView(circles, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < ThemePrefs.ACCENTS.length; i++) {
            final int index = i;
            QuadrantCircleView circle = new QuadrantCircleView(this);
            circle.bind(ThemePrefs.ACCENTS[i], selected == i, amoled);
            circle.setOnClickListener(v -> {
                if (ThemePrefs.accentIndex(this) == index) return;
                
                ThemePrefs.setAccentIndex(this, index);
                buildSettings(scrollView.getScrollY());
                if (iconRunnable != null) iconHandler.removeCallbacks(iconRunnable);
                iconRunnable = () -> {
                    android.widget.Toast.makeText(this, "Applying accent & restarting...", android.widget.Toast.LENGTH_SHORT).show();
                    ThemePrefs.applyLauncherIconAndRestart(this);
                };
                iconHandler.postDelayed(iconRunnable, 500);
            });
            circle.setOnLongClickListener(v -> {
                showAccentPill(circle, ThemePrefs.ACCENTS[index].name);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f);
            circles.addView(circle, lp);
        }

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeRow.setPadding(0, 0, 0, 0);

        TextView themeTitle = text("Theme", 18, R.color.istan_text, true);
        themeRow.addView(themeTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FrameLayout modes = new FrameLayout(this);
        modes.setBackground(roundedBackground(color(R.color.istan_surface_high), dp(22), 0));
        themeRow.addView(modes, new LinearLayout.LayoutParams(dp(204), dp(44)));

        int mode = ThemePrefs.themeMode(this);
        MaterialButtonToggleGroup modeGroup = new MaterialButtonToggleGroup(this);
        modeGroup.setSingleSelection(true);
        modeGroup.setSelectionRequired(true);
        modes.addView(modeGroup, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        MaterialButton auto = addThemeModeButton(modeGroup, "Auto", ThemePrefs.THEME_AUTO, mode);
        MaterialButton light = addThemeModeButton(modeGroup, "Light", ThemePrefs.THEME_LIGHT, mode);
        MaterialButton dark = addThemeModeButton(modeGroup, "Dark", ThemePrefs.THEME_DARK, mode);
        modeGroup.check(mode == ThemePrefs.THEME_AUTO ? auto.getId()
                : mode == ThemePrefs.THEME_LIGHT ? light.getId() : dark.getId());
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int option = (int) group.findViewById(checkedId).getTag();
            if (ThemePrefs.themeMode(this) == option) return;
            ThemePrefs.setThemeMode(this, option);
            applySystemBarTheme();
            buildSettings(scrollView.getScrollY());
        });
        addThemeDivider(modes, 1, mode);
        addThemeDivider(modes, 2, mode);

        LinearLayout.LayoutParams themeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        themeRowLp.setMargins(0, dp(40), 0, 0);
        body.addView(themeRow, themeRowLp);

        LinearLayout updateRow = new LinearLayout(this);
        updateRow.setGravity(Gravity.CENTER_VERTICAL);
        updateRow.setPadding(0, 0, 0, 0);

        TextView updateTitle = text("Check for Updates", 18, R.color.istan_text, true);
        updateRow.addView(updateTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        com.google.android.material.materialswitch.MaterialSwitch updateSwitch = new com.google.android.material.materialswitch.MaterialSwitch(this);
        updateSwitch.setChecked(prefs.getBoolean("check_updates", true));
        updateSwitch.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(R.color.istan_olive), color(R.color.istan_text_muted)}));
        updateSwitch.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ThemePrefs.tint(color(R.color.istan_olive), color(R.color.istan_background), 0.5f), color(R.color.istan_outline)}));
        updateSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("check_updates", isChecked).apply();
        });
        updateRow.addView(updateSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams updateRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        updateRowLp.setMargins(0, dp(40), 0, 0);
        body.addView(updateRow, updateRowLp);

        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.setMargins(0, 0, 0, dp(18));
        body.setLayoutParams(bodyLp);
        return body;
    }

    private void showAccentPill(View anchor, String name) {
        TextView label = text(name, 12, R.color.istan_text, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(12), dp(6), dp(12), dp(6));

        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(color(R.color.istan_surface_high));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), color(R.color.istan_outline));
        label.setBackground(background);

        label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        PopupWindow pill = new PopupWindow(label, label.getMeasuredWidth(), label.getMeasuredHeight(), false);
        pill.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        pill.setOutsideTouchable(false);
        pill.setElevation(dp(4));

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int x = location[0] + (anchor.getWidth() - label.getMeasuredWidth()) / 2;
        int y = location[1] - label.getMeasuredHeight() - dp(8);
        pill.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
        anchor.postDelayed(pill::dismiss, 1500);
    }

    private void addThemeDivider(FrameLayout modes, int boundary, int selected) {
        if (selected == boundary - 1 || selected == boundary) return;
        View divider = new View(this);
        divider.setBackgroundColor(ThemePrefs.tint(color(R.color.istan_outline),
                color(R.color.istan_surface_high), 0.82f));
        FrameLayout.LayoutParams dividerLp = new FrameLayout.LayoutParams(dp(1), dp(20));
        dividerLp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        dividerLp.leftMargin = dp(68 * boundary);
        modes.addView(divider, dividerLp);
    }

    private MaterialButton addThemeModeButton(MaterialButtonToggleGroup group, String label, int option,
                                              int selected) {
        boolean isSelected = option == selected;
        MaterialButton button = new MaterialButton(this);
        button.setId(View.generateViewId());
        button.setTag(option);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(boldFont);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setCheckable(true);
        button.setContentDescription(label + " theme" + (isSelected ? ", selected" : ""));
        button.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ThemePrefs.contrastText(color(R.color.istan_olive)), color(R.color.istan_text_muted)}));
        button.setBackgroundTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{color(R.color.istan_olive), Color.TRANSPARENT}));
        button.setStrokeWidth(0);
        button.setInsetTop(dp(4));
        button.setInsetBottom(dp(4));
        button.setCornerRadius(dp(18));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        group.addView(button, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return button;
    }

    private View developerFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);

        TextView credit = new TextView(this);
        credit.setText("Dev By\nRamakanth");
        credit.setTextSize(38);
        credit.setTextColor(ThemePrefs.isAmoled(this) ? 0xFFCAC4D0 : 0xFF1C1B1F);
        credit.setTypeface(boldFont);
        credit.setGravity(Gravity.START);
        credit.setIncludeFontPadding(false);
        credit.setLineSpacing(0f, 0.88f);
        footer.addView(credit);

        String crafted = "Crafted with ";
        String heart = "❤️";
        String location = " in Bengaluru, India";
        SpannableString attribution = new SpannableString(crafted + heart + location);
        int locationStart = crafted.length() + heart.length();
        attribution.setSpan(new AssetTypefaceSpan(regularFont), 0, crafted.length() + heart.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        attribution.setSpan(new ForegroundColorSpan(ThemePrefs.tint(color(R.color.istan_text_muted),
                color(R.color.istan_background), 0.26f)), 0, crafted.length() + heart.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        attribution.setSpan(new ForegroundColorSpan(ThemePrefs.isAmoled(this) ? 0xFFCAC4D0 : 0xFF1C1B1F), locationStart,
                attribution.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        attribution.setSpan(new AssetTypefaceSpan(boldFont), locationStart, attribution.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView attributionView = new TextView(this);
        attributionView.setText(attribution);
        attributionView.setTextSize(15);
        attributionView.setTextColor(color(R.color.istan_text_muted));
        attributionView.setTypeface(regularFont);
        attributionView.setGravity(Gravity.START);
        attributionView.setIncludeFontPadding(false);
        attributionView.setSingleLine(true);
        LinearLayout.LayoutParams attributionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        attributionLp.topMargin = dp(16);
        footer.addView(attributionView, attributionLp);
        return footer;
    }

    private static final class AssetTypefaceSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        AssetTypefaceSpan(Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setTypeface(typeface);
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            paint.setTypeface(typeface);
        }
    }

    private android.graphics.drawable.GradientDrawable roundedBackground(int color, int radius, int strokeWidth) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (strokeWidth > 0) background.setStroke(strokeWidth, color(R.color.istan_outline));
        return background;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(colorRes));
        textView.setTypeface(bold ? boldFont : regularFont);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private int color(int colorRes) {
        return ThemePrefs.resolveColor(this, colorRes);
    }

    private void applySystemBarTheme() {
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean lightBars = !ThemePrefs.isAmoled(this);
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
