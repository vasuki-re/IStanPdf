package vasuki.istanpdf;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public final class ThemePrefs {
    public static final String PREFS_NAME = "istan_theme";
    private static final String KEY_ACCENT_INDEX = "accent_index";
    private static final String KEY_AMOLED = "amoled";
    private static final String KEY_THEME_MODE = "theme_mode";

    public static final int THEME_AUTO = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final Accent[] ACCENTS = new Accent[]{
            new Accent("Olive", Color.parseColor("#728241"), Color.parseColor("#5C6B32")),
            new Accent("Rose", Color.parseColor("#DF9D99"), Color.parseColor("#D7827E")),
            new Accent("Terracotta", Color.parseColor("#B5684A"), Color.parseColor("#85432F")),
            new Accent("Sand", Color.parseColor("#DBBC7F"), Color.parseColor("#80602F")),
            new Accent("Aqua", Color.parseColor("#83C092"), Color.parseColor("#3F7355")),
            new Accent("Gold", Color.parseColor("#F6C177"), Color.parseColor("#846022"))
        };

    private ThemePrefs() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static final String[] LAUNCHER_ALIASES = new String[]{
            "vasuki.istanpdf.LauncherOlive",
            "vasuki.istanpdf.LauncherRose",
            "vasuki.istanpdf.LauncherTerracotta",
            "vasuki.istanpdf.LauncherSand",
            "vasuki.istanpdf.LauncherAqua",
            "vasuki.istanpdf.LauncherGold"
    };

    public static int accentIndex(Context context) {
        int index = prefs(context).getInt(KEY_ACCENT_INDEX, 0);
        if (index < 0 || index >= ACCENTS.length) return 0;
        return index;
    }

    public static void setAccentIndex(Context context, int index) {
        if (index < 0 || index >= ACCENTS.length) return;
        prefs(context).edit().putInt(KEY_ACCENT_INDEX, index).apply();
    }

    public static void applyLauncherIconAndRestart(android.app.Activity activity) {
        int targetIndex = accentIndex(activity);
        boolean changed = false;
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            for (int i = 0; i < LAUNCHER_ALIASES.length; i++) {
                int desiredState = (i == targetIndex)
                        ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                
                android.content.ComponentName component = new android.content.ComponentName(activity, LAUNCHER_ALIASES[i]);
                if (pm.getComponentEnabledSetting(component) != desiredState) {
                    pm.setComponentEnabledSetting(component, desiredState, android.content.pm.PackageManager.DONT_KILL_APP);
                    changed = true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (changed) {
            android.content.Intent intent = new android.content.Intent(activity, MainActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            Runtime.getRuntime().exit(0);
        }
    }

    public static Accent accent(Context context) {
        return ACCENTS[accentIndex(context)];
    }

    public static int themeMode(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.contains(KEY_THEME_MODE)) {
            int mode = preferences.getInt(KEY_THEME_MODE, THEME_AUTO);
            if (mode >= THEME_AUTO && mode <= THEME_DARK) return mode;
        }
        if (preferences.contains(KEY_AMOLED)) {
            return preferences.getBoolean(KEY_AMOLED, false) ? THEME_DARK : THEME_LIGHT;
        }
        return THEME_AUTO;
    }

    public static void setThemeMode(Context context, int mode) {
        if (mode < THEME_AUTO || mode > THEME_DARK) return;
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).remove(KEY_AMOLED).apply();
    }

    public static boolean isAmoled(Context context) {
        int mode = themeMode(context);
        if (mode == THEME_DARK) return true;
        if (mode == THEME_LIGHT) return false;
        int uiMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static String token(Context context) {
        return accentIndex(context) + ":" + themeMode(context) + ":" + isAmoled(context);
    }

    public static int resolveColor(Context context, int colorRes) {
        Accent accent = accent(context);
        boolean amoled = isAmoled(context);
        if (colorRes == R.color.istan_olive || colorRes == R.color.istan_olive_dark) {
            return accentForeground(accent, amoled);
        }
        if (colorRes == R.color.istan_background) return amoled ? Color.BLACK : Color.parseColor("#FAFAFA");
        if (colorRes == R.color.istan_surface) return amoled ? Color.parseColor("#101010") : tint(accent.base, Color.WHITE, 0.90f);
        if (colorRes == R.color.istan_surface_high) return amoled ? Color.parseColor("#1A1A1A") : tint(accent.base, Color.WHITE, 0.80f);
        if (colorRes == R.color.istan_text) return amoled ? Color.WHITE : Color.parseColor("#1C1C1C");
        if (colorRes == R.color.istan_text_muted) return amoled ? Color.parseColor("#B9B9B9") : Color.parseColor("#505548");
        if (colorRes == R.color.istan_outline) return amoled ? Color.parseColor("#333333") : tint(accent.dark, Color.WHITE, 0.62f);
        return context.getColor(colorRes);
    }

    public static int accentForeground(Accent accent, boolean amoled) {
        return amoled ? accent.base : accent.dark;
    }

    public static int contrastText(int background) {
        double luminance = relativeLuminance(background);
        double whiteContrast = (1.05d) / (luminance + 0.05d);
        double blackContrast = (luminance + 0.05d) / 0.05d;
        return whiteContrast >= blackContrast ? Color.WHITE : Color.BLACK;
    }

    private static double relativeLuminance(int color) {
        return 0.2126d * linear(Color.red(color))
                + 0.7152d * linear(Color.green(color))
                + 0.0722d * linear(Color.blue(color));
    }

    private static double linear(int component) {
        double value = component / 255d;
        return value <= 0.04045d ? value / 12.92d : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }

    public static int[] quadrantColors(Accent accent, boolean amoled) {
        if (amoled) {
            return new int[]{
                    accent.dark,
                    accent.base,
                    tint(accent.base, Color.WHITE, 0.32f),
                    tint(accent.dark, Color.BLACK, 0.48f)
            };
        }
        return new int[]{
                tint(accent.base, Color.WHITE, 0.14f),
                accent.base,
                accent.dark,
                tint(accent.dark, Color.WHITE, 0.42f)
        };
    }

    public static int tint(int from, int to, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * amount);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.argb(a, r, g, b);
    }

    public static final class Accent {
        public final String name;
        public final int base;
        public final int dark;

        Accent(String name, int base, int dark) {
            this.name = name;
            this.base = base;
            this.dark = dark;
        }
    }
}
