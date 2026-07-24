package vasuki.istanpdf.presentation;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import vasuki.istanpdf.R;
import vasuki.istanpdf.ThemePrefs;

public class HomeViewBuilder {

    public interface HomeActions {
        void onMergePdf();
        void onModifyPdf();
        void onImageToPdf();
        void onPdfToImage();
        void onDocxRemovePages();
        void onDocxReorderPages();
        void onSupportDeveloper();
        void onOpenSettings();
    }

    private static final String WAITING_TEXT = "Ready";

    private final Activity activity;
    private final Typeface regularFont;
    private final Typeface boldFont;

    
    private TextView status;
    private ImageView statusIndicator;

    public HomeViewBuilder(Activity activity, Typeface regularFont, Typeface boldFont) {
        this.activity = activity;
        this.regularFont = regularFont;
        this.boldFont = boldFont;
    }

    public View build(HomeActions actions) {
        LinearLayout mainContainer = new LinearLayout(activity);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(color(R.color.istan_background));

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(mainContainer, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        mainContainer.addView(scrollView, scrollParams);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(16));

        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout heroRow = new LinearLayout(activity);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heroRow);

        TextView title = text("", 40, R.color.istan_text, true);
        android.text.SpannableString ss = new android.text.SpannableString("IStanPdf");
        ss.setSpan(new android.text.style.ForegroundColorSpan(color(R.color.istan_text)), 0, 5, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new android.text.style.ForegroundColorSpan(color(R.color.istan_olive)), 5, 8, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(ss);
        heroRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialCardView settingsButton = new MaterialCardView(activity);
        settingsButton.setCardBackgroundColor(color(R.color.istan_surface));
        settingsButton.setRadius(dp(100));
        settingsButton.setStrokeWidth(dp(1));
        settingsButton.setStrokeColor(color(R.color.istan_outline));
        settingsButton.setCardElevation(0);
        settingsButton.setUseCompatPadding(false);
        settingsButton.setOnClickListener(v -> actions.onOpenSettings());

        ImageView menuIcon = new ImageView(activity);
        menuIcon.setImageResource(R.drawable.menu);
        menuIcon.setColorFilter(color(R.color.istan_olive_dark));
        menuIcon.setPadding(dp(10), dp(10), dp(10), dp(10));
        settingsButton.addView(menuIcon, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        settingsLp.setMargins(dp(12), 0, 0, 0);
        heroRow.addView(settingsButton, settingsLp);

        TextView subtitle = text("Offline app for PDF and DOCX operations", 16, R.color.istan_text_muted, false);
        subtitle.setPadding(0, dp(4), 0, dp(40));
        root.addView(subtitle);

        root.addView(createSectionHeader("PDF TOOLS"));
        LinearLayout pdfRow1 = new LinearLayout(activity);
        pdfRow1.setOrientation(LinearLayout.HORIZONTAL);
        pdfRow1.addView(dashboardCard("Merge PDF", R.drawable.merge_24px, actions::onMergePdf));
        pdfRow1.addView(dashboardCard("Modify PDF", R.drawable.modify_pdf_24px, actions::onModifyPdf));
        root.addView(pdfRow1);

        root.addView(createSectionHeader("CONVERSIONS"));
        LinearLayout convRow1 = new LinearLayout(activity);
        convRow1.setOrientation(LinearLayout.HORIZONTAL);
        convRow1.addView(dashboardCard("Image to PDF", R.drawable.img2pdf_24px, actions::onImageToPdf));
        convRow1.addView(dashboardCard("PDF to Image", R.drawable.pdf2img_24px, actions::onPdfToImage));
        root.addView(convRow1);

        root.addView(createSectionHeader("DOCX TOOLS"));
        LinearLayout docxRow1 = new LinearLayout(activity);
        docxRow1.setOrientation(LinearLayout.HORIZONTAL);
        docxRow1.addView(dashboardCard("Remove Pages", R.drawable.remove_page_docx_24px, actions::onDocxRemovePages));
        docxRow1.addView(dashboardCard("Reorder Pages", R.drawable.reorder_docx_24px, actions::onDocxReorderPages));
        root.addView(docxRow1);

        root.addView(kofiCard("Support the Developer", R.drawable.ic_kofi, actions::onSupportDeveloper));

        View topSpacer = new View(activity);
        root.addView(topSpacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout statusCard = new LinearLayout(activity);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER);
        statusCard.setBaselineAligned(false);
        statusCard.setPadding(dp(16), dp(10), dp(20), dp(10));
        android.graphics.drawable.GradientDrawable statusBg = new android.graphics.drawable.GradientDrawable();
        statusBg.setColor(color(R.color.istan_surface));
        statusBg.setCornerRadius(dp(28));
        statusBg.setStroke(dp(1), color(R.color.istan_outline));
        statusCard.setBackground(statusBg);

        statusIndicator = new ImageView(activity);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color(R.color.istan_olive));
        statusIndicator.setImageDrawable(dot);
        statusCard.addView(statusIndicator, new LinearLayout.LayoutParams(dp(12), dp(12)));

        status = text(WAITING_TEXT, 15, R.color.istan_olive, false);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(8), 0, 0, 0);
        statusCard.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scParams.setMargins(0, 0, 0, dp(24));
        scParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(statusCard, scParams);

        String versionName = "";
        try {
            versionName = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        TextView footerText = text(versionName, 15, R.color.istan_text_muted, false);
        LinearLayout.LayoutParams ftParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ftParams.gravity = Gravity.CENTER_HORIZONTAL;
        ftParams.setMargins(0, 0, 0, dp(4));
        root.addView(footerText, ftParams);

        return mainContainer;
    }

    public TextView getStatus() { return status; }
    public ImageView getStatusIndicator() { return statusIndicator; }

    private View dashboardCard(String title, int iconResId, Runnable action) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(color(R.color.istan_surface));
        card.setRadius(dp(16));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(color(R.color.istan_outline));
        card.setCardElevation(0);
        card.setUseCompatPadding(false);

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        row.setPadding(dp(12), dp(20), dp(8), dp(20));
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        if (iconResId != 0) {
            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconResId);
            icon.setColorFilter(color(R.color.istan_olive_dark));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
            iconParams.setMargins(0, 0, dp(10), 0);
            row.addView(icon, iconParams);
        }

        TextView label = text(title, 15, R.color.istan_text, false);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView chevron = text(">", 20, R.color.istan_text_muted, false);
        chevron.setGravity(Gravity.CENTER);
        chevron.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(chevron, chevronParams);

        card.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        params.setMargins(dp(8), dp(8), dp(8), dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private View kofiCard(String title, int iconResId, Runnable action) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(color(R.color.istan_surface));
        card.setRadius(dp(16));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(color(R.color.istan_outline));
        card.setCardElevation(0);
        card.setUseCompatPadding(false);

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        row.setPadding(dp(12), dp(20), dp(12), dp(20));
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);

        if (iconResId != 0) {
            FrameLayout icon = new FrameLayout(activity);
            addKofiIconLayer(icon, R.drawable.ic_kofi_background, R.color.istan_surface);
            addKofiIconLayer(icon, R.drawable.ic_kofi_body, R.color.istan_olive_dark);
            addKofiIconLayer(icon, R.drawable.ic_kofi_cutout, R.color.istan_surface);
            addKofiIconLayer(icon, R.drawable.ic_kofi_handle, R.color.istan_olive_dark);
            addKofiIconLayer(icon, R.drawable.ic_kofi_heart, R.color.istan_olive);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(26));
            iconParams.setMargins(0, 0, dp(10), 0);
            row.addView(icon, iconParams);
        }

        TextView label = text(title, 15, R.color.istan_text, false);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        card.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), dp(8), dp(8), dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private void addKofiIconLayer(FrameLayout icon, int drawableRes, int colorRes) {
        ImageView layer = new ImageView(activity);
        layer.setImageResource(drawableRes);
        layer.setColorFilter(color(colorRes));
        layer.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView createSectionHeader(String title) {
        TextView header = text(title, 12, R.color.istan_text_muted, true);
        header.setAllCaps(true);
        header.setLetterSpacing(0.1f);
        header.setPadding(dp(6), dp(32), 0, dp(8));
        return header;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold) {
        TextView textView = new TextView(activity);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(colorRes));
        textView.setTypeface(bold ? boldFont : regularFont);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private int color(int colorRes) {
        return ThemePrefs.resolveColor(activity, colorRes);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
