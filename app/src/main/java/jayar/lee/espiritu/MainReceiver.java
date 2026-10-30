package jayar.lee.espiritu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.jepongdevxyz.fablevpn.R;

import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.service.InjectorService;
import jayar.lee.espiritu.service.OpenVPNService;

public class MainReceiver extends BroadcastReceiver
{
    public static final String TAG = "MainReceiver";
    private final int ANTI_FLAP_PERIOD;
    public boolean conn_on;
    public boolean conn_on_defined;
    private boolean initialized;
    private long last_action_time;
    private boolean last_ok;
    public boolean screen_on;
    public boolean screen_on_defined;
    
    public MainReceiver() {
        this.ANTI_FLAP_PERIOD = 10000;
        this.screen_on_defined = false;
        this.screen_on = false;
        this.conn_on_defined = false;
        this.conn_on = false;
        this.last_ok = true;
        this.initialized = false;
    }

    public void onReceive(Context context, Intent intent) {
        ConfigUtil mConfig = ConfigUtil.getInstance(context);
        int type = mConfig.getNetworkType().equals("UDP")?0:1;
        boolean screen_on_mod = false;
        boolean conn_on_mod = false;
        boolean failover = false;
        String act = intent.getAction();
        boolean pvbs = false;
        if ("android.intent.action.SCREEN_ON".equals(act)) {
            this.screen_on = true;
            screen_on_mod = true;
            this.screen_on_defined = true;
        } else if ("android.intent.action.SCREEN_OFF".equals(act)) {
            this.screen_on = false;
            screen_on_mod = true;
            this.screen_on_defined = true;
        } else if ("android.net.conn.CONNECTIVITY_CHANGE".equals(act)) {
            boolean z;
            if (intent.getBooleanExtra("noConnectivity", false)) {
                z = false;
            } else {
                z = true;
            }
            this.conn_on = z;
            failover = intent.getBooleanExtra("isFailover", false);
            conn_on_mod = true;
            this.conn_on_defined = true;
        } 
        if (screen_on_mod || conn_on_mod) {
            boolean ok;
            if (!(pvbs && this.screen_on_defined && !this.screen_on) && (!this.conn_on_defined || this.conn_on)) {
                ok = true;
            } else {
                ok = false;
            }
            if (hLogStatus.isTunnelActive()) {
                if (ok != this.last_ok) {
                    if (ok) {
                        if(type==1){
                            context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_RESUME));
                        }
                        else if(type==0){
                            context.startService(new Intent(context, InjectorService.class).setAction(InjectorService.RECONNECT_SERVICE));
                        }
                        this.last_ok = ok;
                    } else {
                        if(type==2){
                            context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_PAUSE));
                        }
                        else if(type==0){
                            hLogStatus.updateStateString(hLogStatus.VPN_PAUSE, context.getString(R.string.state_pause));
                            hLogStatus.logInfo(context.getString(R.string.state_pause));
                            if(OpenVPNService.isSSHRunning)context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.STOP_SSH_SERVICE));
                        }
                        this.last_ok = ok;
                    }
                } else if (screen_on_mod && this.screen_on && ok && pvbs) {
                    if(type==2){
                        context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_RECONNECT));
                    }
                    else if(type==1){
                        context.startService(new Intent(context, InjectorService.class).setAction(InjectorService.RECONNECT_SERVICE));
                    }
                } else if (conn_on_mod && failover && ok && this.initialized && time_since_last_action() > 10000) {
                    if(type==2){
                        context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_RECONNECT));
                    }
                    else if(type==1){
                        context.startService(new Intent(context, InjectorService.class).setAction(InjectorService.RECONNECT_SERVICE));
                    }
                }
            }
            if (conn_on_mod) {
                this.initialized = true;
                update_last_action_time();
            }
        }
    }
    
    private void update_last_action_time() {
        this.last_action_time = SystemClock.elapsedRealtime();
    }

    private long time_since_last_action() {
        return SystemClock.elapsedRealtime() - this.last_action_time;
    }

}
