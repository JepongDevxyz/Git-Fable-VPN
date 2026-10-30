package jayar.lee.espiritu;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import androidx.annotation.RequiresApi;

import jayar.lee.espiritu.activities.OpenVPNClient;
import jayar.lee.espiritu.service.OpenVPNService;
import jayar.lee.espiritu.thread.TopExceptionHandler;
import jayar.lee.espiritu.utils.SecurePreferences;
import jayar.lee.espiritu.utils.util;

public class HarliesApplication extends Application
{
    public static final String PREFS_GERAL = "HarlieApplication";
    private static SecurePreferences sharedPreferences;
    private static SharedPreferences _defaultSharedPreferences;
    private static HarliesApplication MainApp;

    @Override
    public void onCreate()
    {
        super.onCreate();
        MainApp = HarliesApplication.this;
        TopExceptionHandler.init(this);
        _defaultSharedPreferences = getSharedPreferences(PREFS_GERAL, MODE_PRIVATE);
        sharedPreferences = new SecurePreferences(this);
        OpenVPNService.setNotificationActivityClass(OpenVPNClient.class);
        _defaultSharedPreferences.edit().putString(PREFS_GERAL,new util(MainApp).a()).apply();
    }

    public static HarliesApplication getApp() {
        return MainApp;
    }

    public static String resString(int res_id) {
        return MainApp.getString(res_id);
    }

    public static SecurePreferences getSecurePreferences() {
        return sharedPreferences;
    }

    public static SharedPreferences getPrivateSharedPreferences() {
        return _defaultSharedPreferences;
    }
}
