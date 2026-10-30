package jayar.lee.espiritu.thread;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import jayar.lee.espiritu.HarliesApplication;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.config.SettingsConstants;
import jayar.lee.espiritu.core.vpnutils.CustomNativeLoader;
import jayar.lee.espiritu.core.vpnutils.StreamGobbler;
import jayar.lee.espiritu.core.vpnutils.VpnUtils;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.service.InjectorService;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UDPThread extends Thread implements SettingsConstants {
    private final ConfigUtil mConfig;
    private File filehysteria;
    private Process hysteriaProcess;
    private final InjectorService service;
    private static final String UDP_BIN = "libudp";
    private static final String UDP_CLI = "{\n  \"server\": \"%s\",\n  \"obfs\": \"%s\",\n  \"auth_str\": \"%s\",\n  \"up_mbps\": %s,\n  \"down_mbps\": %s,\n  \"retry\": %s,\n  \"retry_interval\": %s,\n  \"socks5\": {\n    \"listen\": \"%s\"\n  },\n  \"http\": {\n    \"listen\": \"%s\"\n  },\n  \"insecure\": %s,\n  \"lazy_start\": true,\n  \"ca\": \"%s\",\n  \"recv_window_conn\": %s,\n \"recv_window\": %s\n}";
    private SharedPreferences mPref;

    public UDPThread(InjectorService _service) {
        this.service = _service;
        this.mConfig = ConfigUtil.getInstance(service);
        this.mPref = HarliesApplication.getPrivateSharedPreferences();
    }

    @Override
    public void run() {
        try {
            hLogStatus.updateStateString(hLogStatus.VPN_CONNECTING, service.getString(R.string.state_connecting));
            addLog("<b>UDP Client: </b>" + service.getString(R.string.state_connecting));

            StringBuilder cmd = new StringBuilder();

            filehysteria = CustomNativeLoader.loadNativeBinary(service.getBaseContext(), UDP_BIN, new File(service.getBaseContext().getFilesDir(), UDP_BIN));

            if (filehysteria == null) {
                interrupt();
                throw new IOException("Bin UDP was not found");
            }

            if (this.mConfig.getSecureString(SERVER_KEY).isEmpty()) {
                interrupt();
                hLogStatus.updateStateString(hLogStatus.VPN_AUTH_FAILED, service.getString(R.string.state_auth_failed));
                service.startService(new Intent(service, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
                throw new IOException("Invalid UDP Server");
            }
            hLogStatus.updateStateString(hLogStatus.VPN_GET_CONFIG, service.getString(R.string.state_get_config));
            addLog("<b>UDP Client: </b>" + service.getString(R.string.state_get_config));
            File s = makeUdpConf(service.getBaseContext().getFilesDir());

            cmd.append(filehysteria.getCanonicalPath());
            cmd.append(" client -c ");
            cmd.append(s.getAbsoluteFile());

            hysteriaProcess = Runtime.getRuntime().exec(cmd.toString());

            StreamGobbler.OnLineListener onLineListener = log -> {
                Log.d(UDP_BIN, "<b>UDP Client: </b>" + log);
                if (log.toLowerCase().contains("connected")) {
                    addLog("<b>UDP Client: </b>" + log.replace(excludeIps(),"UDP HYSTERIA"));
                    addLog("<font color='#006633'><strong>" + "Connected" + "</b>");
                    addLog("<font color='#FFA500'><strong>" + "Git Fable VPN ready to use!" + "</b>");
                    service.vpn_handler(true);
                } else if (log.toLowerCase().contains("auth error")) {
                    addLog("<font color = #d50000>Failed to authenticate, username or password expired");
                    hLogStatus.updateStateString(hLogStatus.VPN_AUTH_FAILED, service.getString(R.string.state_auth_failed));
                    service.startService(new Intent(service, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
                } else if (log.toLowerCase().contains("out of retries")) {
                    addLog("<b>UDP Client: </b>out of retries");
                    interrupt();
                    service.network_reconnect();
                } else if (log.contains("[ERRO]")) {
                    addLog("<b>UDP Client: </b>" + log);
                    interrupt();
                    service.network_reconnect();
                }
            };

            StreamGobbler stdoutGobbler = new StreamGobbler(hysteriaProcess.getInputStream(), onLineListener);
            StreamGobbler stderrGobbler = new StreamGobbler(hysteriaProcess.getErrorStream(), onLineListener);

            stdoutGobbler.start();
            stderrGobbler.start();

            hysteriaProcess.waitFor();
        } catch (IOException | InterruptedException e) {
            interrupt();
            addLog("UDP Client: " + e.getMessage());
        }
    }

    @Override
    public void interrupt() {
        if (hysteriaProcess != null)
            hysteriaProcess.destroy();
        try {
            if (filehysteria != null)
                VpnUtils.killProcess(filehysteria);
        } catch (Exception e) {
        }
        hysteriaProcess = null;
        filehysteria = null;
        super.interrupt();
    }

    public String getUDPConfig(){
        try {
            JSONObject js = new JSONObject(mConfig.getSecureString(DIRECT_UDP_CONFIG_KEY));
            String mObfs = js.getString("obfs");
            String mUser = mConfig.getSecureString(USERNAME_KEY);
            int up = js.getInt("up_mbps");
            int dw = js.getInt("down_mbps");
            int rt = js.getInt("retry");
            int rt_in = js.getInt("retry_interval");
            String mSocks5 =  js.getJSONObject("socks5").getString("listen");
            String mHttp =  js.getJSONObject("http").getString("listen");
            boolean mInsecure =  js.getBoolean("insecure");
            String mCa =  js.getString("ca");
            int mRecv_window_conn =  js.getInt("recv_window_conn");
            int mRecv_window =  js.getInt("recv_window");
            return String.format(UDP_CLI, excludeIps() + ":20000-50000", mObfs, mUser, up, dw, rt, rt_in, mSocks5, mHttp, mInsecure, mCa, mRecv_window_conn, mRecv_window);
        } catch (JSONException e) {
            return mConfig.getSecureString(DIRECT_UDP_CONFIG_KEY);
        }
    }

    private File makeUdpConf(File fileDir) throws IOException {
        System.out.println(excludeIps());
        String conf = getUDPConfig();
        File f = new File(fileDir, "config.json");
        if (f.exists()) {
            f.delete();
        }
        saveTextFile(f, conf);

        File cache = new File(fileDir, "config.json");
        if (!cache.exists()) {
            try {
                cache.createNewFile();
            } catch (Exception e) {
            }
        }
        return f;
    }

    public static boolean saveTextFile(File file, String contents) {
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter writer = new FileWriter(file, false);
            writer.write(contents);
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String excludeIps() {
        try {
            InetAddress addr = InetAddress.getByName(mConfig.getSecureString(SERVER_KEY));
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            interrupt();
            return null;
        }
    }

    public static void addLog(String msg) {
        hLogStatus.logInfo(msg);
    }

}