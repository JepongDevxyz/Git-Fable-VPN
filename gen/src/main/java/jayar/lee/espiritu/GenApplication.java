package jayar.lee.espiritu;

import android.app.Application;
import android.content.SharedPreferences;

/**
 * Application class for the standalone Git Fable Generator (admin tool).
 *
 * Provides the same private-SharedPreferences accessor the copied
 * ProfilePrefs expects from the client's HarliesApplication
 * (getPrivateSharedPreferences()), backed by THIS app's context — the
 * generator's profile store ("cc_profiles_v1") lives in the generator
 * app's own private prefs, fully separate from the user client.
 */
public class GenApplication extends Application {
    public static final String PREFS_GERAL = "GitFableGenerator";
    private static SharedPreferences privatePrefs;
    private static GenApplication mainApp;

    @Override public void onCreate() {
        super.onCreate();
        mainApp = GenApplication.this;
        privatePrefs = getSharedPreferences(PREFS_GERAL, MODE_PRIVATE);
    }

    public static GenApplication getApp() {
        return mainApp;
    }

    public static SharedPreferences getPrivateSharedPreferences() {
        return privatePrefs;
    }
}
