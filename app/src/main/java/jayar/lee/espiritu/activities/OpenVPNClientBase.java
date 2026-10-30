package jayar.lee.espiritu.activities;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.security.KeyChainAliasCallback;
import android.util.Log;
import jayar.lee.espiritu.HarliesApplication;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.service.InjectorService;
import jayar.lee.espiritu.service.OpenVPNService;
import jayar.lee.espiritu.service.OpenVPNService.*;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import net.openvpn.openvpn.*;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.view.StatisticGraphData;

import java.net.InetAddress;
import java.net.UnknownHostException;

public abstract class OpenVPNClientBase extends AppCompatActivity implements InjectorService.InjectorListener,EventReceiver{
    private static final String TAG = "OpenVPNClientBase";
    private OpenVPNService mBoundService = null;
    public SharedPreferences mPref;
    public SharedPreferences.Editor mEditor;
    protected InjectorService mInjector;
    public ConfigUtil mConfig;
    public StatisticGraphData.DataTransferStats _upDateBytes;
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            OpenVPNClientBase.this.mBoundService = ((LocalBinder) service).getService();
            Log.d(OpenVPNClientBase.TAG, "CLIBASE: onServiceConnected: " + OpenVPNClientBase.this.mBoundService.toString());
            OpenVPNClientBase.this.mBoundService.client_attach(OpenVPNClientBase.this);
            OpenVPNClientBase.this.post_bind();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(OpenVPNClientBase.TAG, "CLIBASE: onServiceDisconnected");
            OpenVPNClientBase.this.mBoundService = null;
        }
    };

    private final ServiceConnection mInjectorConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName p1, IBinder p2)
        {
            mInjector = ((InjectorService.MyBinder)p2).getService();
            mInjector.setInjectorListener(OpenVPNClientBase.this);
        }
        @Override
        public void onServiceDisconnected(ComponentName p1)
        {
            mInjector = null;
        }
    };

    protected interface EpkiPost {
        void post_dispatch(String str);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        mPref = HarliesApplication.getPrivateSharedPreferences();
        mEditor = mPref.edit();
        mConfig = ConfigUtil.getInstance(OpenVPNClientBase.this);
        _upDateBytes = StatisticGraphData.getStatisticData().getDataTransferStats();
    }
    public void addlogInfo(String msg){
        if(!msg.contains("EVENT"))hLogStatus.logInfo(msg);
    }

    @SuppressLint("WrongConstant")
    protected void doBindService() {
        bindService(new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_BIND), this.mConnection, 65);
        bindService(new Intent(this, InjectorService.class), mInjectorConnection, 1);
    }

    protected void doUnbindService() {
        Log.d(TAG, "CLIBASE: doUnbindService");
        if (this.mBoundService != null) {
            unbindService(this.mConnection);
            this.mBoundService = null;
        }
        if (mInjector != null) {
            unbindService(mInjectorConnection);
            mInjector = null;
        }
    }

    @Override
    public void startOpenVPN(){
        // TODO: Implement this method
    }

    protected ConnectionStats get_connection_stats() {
        if (this.mBoundService != null) {
            return this.mBoundService.get_connection_stats();
        }
        return null;
    }

    public String get_gui_version(String name) {
        String versionName = "0.0";
        int versionCode = 0;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pi.versionName;
            versionCode = pi.versionCode;
        } catch (Exception e) {
            Log.e(TAG, "cannot obtain version info", e);
        }
        return String.format("%s %s-%d", new Object[]{name, versionName, Integer.valueOf(versionCode)});
    }
    protected void submitConnectIntent(String profile_name, String server, String vpn_proto, String conn_timeout, String username, String password, boolean cache_password, String pk_password, String response, String epki_alias, String compression_mode, String proxy_name, String proxy_username, String proxy_password, boolean proxy_allow_creds_dialog, String gui_version) {
        String prefix = OpenVPNService.INTENT_PREFIX;
        Intent intent = new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_CONNECT).putExtra(prefix + ".PROFILE", profile_name).putExtra(prefix + ".GUI_VERSION", gui_version).putExtra(prefix + ".PROXY_NAME", proxy_name).putExtra(prefix + ".PROXY_USERNAME", proxy_username).putExtra(prefix + ".PROXY_PASSWORD", proxy_password).putExtra(prefix + ".PROXY_ALLOW_CREDS_DIALOG", proxy_allow_creds_dialog).putExtra(prefix + ".SERVER", server).putExtra(prefix + ".PROTO", vpn_proto).putExtra(prefix + ".CONN_TIMEOUT", conn_timeout).putExtra(prefix + ".USERNAME", username).putExtra(prefix + ".PASSWORD", password).putExtra(prefix + ".CACHE_PASSWORD", cache_password).putExtra(prefix + ".PK_PASSWORD", pk_password).putExtra(prefix + ".RESPONSE", response).putExtra(prefix + ".EPKI_ALIAS", epki_alias).putExtra(prefix + ".COMPRESSION_MODE", compression_mode);
        if (this.mBoundService != null) {
            this.mBoundService.client_attach(this);
        }
        ComponentName serv = startService(intent);
        Log.d(TAG, "CLI: submitConnectIntent: " + profile_name);
    }

    protected void submitDisconnectIntent(int stop_service) {
        switch(stop_service){
            case 0:
                startService(new Intent(OpenVPNClientBase.this, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
                break;
            case 1:
                startService(new Intent(OpenVPNClientBase.this, OpenVPNService.class).setAction(OpenVPNService.ACTION_DISCONNECT).putExtra(OpenVPNService.INTENT_PREFIX + ".STOP", true));
                break;
        }
    }

    public void resolveExternalPkiAlias(final EpkiPost next_action) {
        final Handler handler = new Handler();
        final OpenVPNClientBase topthis = this;
        final KeyChainAliasCallback response = alias -> {
            if (alias != null) {
                handler.post(() -> next_action.post_dispatch(alias));
            }
        };
        next_action.post_dispatch("DISABLE_CLIENT_CERT");
    }

    protected void init_default_preferences(PrefUtil prefs) {
        prefs.set_string("compression_mode", "yes");
        prefs.set_boolean("google_dns_fallback", true);
        prefs.set_string("vpn_proto", "TCP");
        prefs.set_string("conn_timeout", "60");
        prefs.set_string("ipv6", "default");
        prefs.set_string("tls_version_min_override", "default");
        prefs.set_boolean("auto_keyboard", false);
        prefs.set_boolean("autostart_finish_on_connect", true);
    }

    protected void post_bind() {
    }

    public void event(EventMsg ev) {
    }

    public void log(LogMsg lm) {
    }
    protected String resString(int res_id) {
        return getResources().getString(res_id);
    }

}
