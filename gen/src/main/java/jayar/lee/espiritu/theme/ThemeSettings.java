package jayar.lee.espiritu.theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Dark / Light mode setting for the standalone Generator (admin app) —
 * the gen-side copy of the client's ThemeSettings (:gen cannot use :app
 * classes). Same contract: "cc_theme" prefs, default "system", pushed
 * into AppCompatDelegate.setDefaultNightMode() at startup
 * (GenApplication) and the moment the admin changes it in the
 * Appearance card. AppTheme here is the same DayNight theme and the
 * cc_ tokens carry values + values-night variants, so the whole
 * generator UI follows the chosen mode.
 */
public final class ThemeSettings {

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_DARK = "dark";
    public static final String MODE_LIGHT = "light";

    private static final String PREFS = "cc_theme";
    private static final String KEY_MODE = "mode";

    private ThemeSettings() {}

    /** Saved mode; anything unrecognized (incl. first run) = "system". */
    public static String getMode(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return normalize(sp.getString(KEY_MODE, MODE_SYSTEM));
    }

    /** Persist a mode; unrecognized values normalize to "system". */
    public static void setMode(Context ctx, String mode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, normalize(mode)).apply();
    }

    private static String normalize(String mode) {
        if (MODE_DARK.equals(mode) || MODE_LIGHT.equals(mode)) return mode;
        return MODE_SYSTEM;
    }

    /** Map a saved mode onto the AppCompatDelegate night constant. */
    public static int toNightMode(String mode) {
        if (MODE_DARK.equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        if (MODE_LIGHT.equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /** Apply the saved mode process-wide (startup + on change). */
    public static void applySaved(Context ctx) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(getMode(ctx)));
    }
}
