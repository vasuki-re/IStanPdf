package vasuki.istanpdf;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class QuadrantCircleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF circleBounds = new RectF();
    private ThemePrefs.Accent accent;
    private boolean selected;
    private boolean amoled;

    public QuadrantCircleView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void bind(ThemePrefs.Accent accent, boolean selected, boolean amoled) {
        this.accent = accent;
        this.selected = selected;
        this.amoled = amoled;
        setSelected(selected);
        setFocusable(true);
        setContentDescription(accent.name + " accent" + (selected ? ", selected" : ""));
        invalidate();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.RadioButton");
        info.setCheckable(true);
        info.setChecked(selected);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (accent == null) return;

        float inset = dp(selected ? 3 : 4);
        float size = Math.min(getWidth(), getHeight()) - (2 * inset);
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        circleBounds.set(left, top, left + size, top + size);
        int[] colors = ThemePrefs.quadrantColors(accent, amoled);
        for (int i = 0; i < colors.length; i++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colors[i]);
            canvas.drawArc(circleBounds, -90 + (i * 90), 90, true, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(selected ? 2 : 1));
        paint.setColor(selected ? ThemePrefs.accentForeground(accent, amoled)
                : ThemePrefs.tint(accent.dark, amoled ? 0xFF000000 : 0xFFFFFFFF, amoled ? 0.2f : 0.58f));
        canvas.drawOval(circleBounds, paint);

    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
