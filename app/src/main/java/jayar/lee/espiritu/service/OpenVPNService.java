package jayar.lee.espiritu.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import javax.crypto.Cipher;
import net.openvpn.openvpn.PrefUtil;
import net.openvpn.openvpn.ProxyList.Item;
import net.openvpn.openvpn.CPUUsage;
import net.openvpn.openvpn.JellyBeanHack;
import net.openvpn.openvpn.OpenVPNClientThread;
import net.openvpn.openvpn.ProxyList;
import net.openvpn.openvpn.PasswordUtil;
import net.openvpn.openvpn.ClientAPI_ConnectionInfo;
import net.openvpn.openvpn.ClientAPI_ServerEntryVector;
import net.openvpn.openvpn.ClientAPI_ServerEntry;
import net.openvpn.openvpn.ClientAPI_EvalConfig;
import net.openvpn.openvpn.ClientAPI_Config;
import net.openvpn.openvpn.ClientAPI_OpenVPNClient;
import net.openvpn.openvpn.ClientAPI_ProvideCreds;
import net.openvpn.openvpn.ClientAPI_Status;
import net.openvpn.openvpn.ClientAPI_TransportStats;
import net.openvpn.openvpn.ClientAPI_InterfaceStats;
import net.openvpn.openvpn.ClientAPI_DynamicChallenge;
import net.openvpn.openvpn.ClientAPI_Event;
import net.openvpn.openvpn.ClientAPI_LogInfo;
import net.openvpn.openvpn.ClientAPI_ExternalPKICertRequest;
import net.openvpn.openvpn.ClientAPI_ExternalPKISignRequest;
import net.openvpn.openvpn.ClientAPI_LLVector;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import jayar.lee.espiritu.HarliesApplication;
import jayar.lee.espiritu.MainReceiver;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.config.SettingsConstants;
import jayar.lee.espiritu.core.CIDRIP;
import jayar.lee.espiritu.core.NetworkSpace;
import jayar.lee.espiritu.core.vpnutils.Pdnsd;
import jayar.lee.espiritu.core.vpnutils.Tun2Socks;
import jayar.lee.espiritu.core.vpnutils.VpnUtils;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.thread.ProxyThread;
import jayar.lee.espiritu.utils.FileUtils;
import jayar.lee.espiritu.utils.util;

public class OpenVPNService extends VpnService implements Callback, net.openvpn.openvpn.OpenVPNClientThread.EventReceiver, SettingsConstants {
    public static final String ACTION_BIND = "net.openvpn.openvpn.BIND";
    public static final String ACTION_CONNECT = "net.openvpn.openvpn.CONNECT";
    public static final String ACTION_DISCONNECT = "net.openvpn.openvpn.DISCONNECT";
    public static final String ACTION_SUBMIT_PROXY_CREDS = "net.openvpn.openvpn.ACTION_SUBMIT_PROXY_CREDS";

    public static final String ACTION_ADD_PROFILE = "net.openvpn.openvpn.ADD_PROFILE";
    public static final String START_SSH_SERVICE = "com.harlie.ovpn.ssh.ssl.service.START_SERVICE";

    public static final String STOP_SSH_SERVICE = "com.harlie.ovpn.ssh.ssl.service.STOP_SERVICE";

    public static final String ACTION_PAUSE = "com.harlie.ovpn.ssh.ssl.service.PAUSE_SERVICE";

    public static final String ACTION_RESUME = "com.harlie.ovpn.ssh.ssl.service.RESUME_SERVICE";

    public static final String ACTION_RECONNECT = "com.harlie.ovpn.ssh.ssl.service.RECONNECT_SERVICE";
    public static final int EV_PRIO_HIGH = 3;
    private static final int GCI_REQ_ESTABLISH = 0;
    public static final String INTENT_PREFIX = "net.openvpn.openvpn";
    private static final int MSG_EVENT = 1;
    private static final int MSG_LOG = 2;
    private static final String TAG = "OpenVPNService";
    private boolean active = false;
    public static boolean i = true;
    private static ArrayDeque<EventReceiver> clients = new ArrayDeque();
    private CPUUsage cpu_usage;
    private Profile current_profile;
    private boolean enable_notifications;
    private HashMap<String, EventInfo> event_info;
    private JellyBeanHack jellyBeanHack;
    private EventMsg last_event;
    private EventMsg last_event_prof_manage;
    private static ArrayDeque<LogMsg> log_deque = new ArrayDeque();
    private final IBinder mBinder = new LocalBinder();
    private MainReceiver mConnectivityReceiver;
    private Handler mHandler;
    public static boolean paused = false;
    private static OpenVPNClientThread mThread;
    private PrefUtil prefs;
    private ProfileList profile_list;
    public ProxyList proxy_list;
    private PasswordUtil pwds;
    private long thread_started = 0;
    private static ConfigUtil mConfig;
    private int mMtu = 1500;
    private Thread mBuilderThread;
    private Pdnsd mPdnsd;
    private static final String VPN_INTERFACE_NETMASK = "255.255.255.0";
    private NetworkSpace mRoutes;
    private VpnUtils.PrivateAddress mPrivateAddress;
    private SharedPreferences mPref;
    private ParcelFileDescriptor mInterface = null;
    private Tun2Socks mTun2Socks;
    private static final int DNS_RESOLVER_PORT = 53;
    public static boolean isSSHRunning = false;
    private static WeakReference<OpenVPNService> sRunningInstance = null;
    private static boolean isStopping = true;
    private static Class<? extends AppCompatActivity> mNotificationActivityClass;

    public static String render_bandwidth(long bytes, boolean mbit) {
        if(mbit)
            bytes = bytes *8;
        int unit = mbit ? 1000 : 1024;
        if (bytes < unit)
            return bytes + (mbit ? " bit" : " B");

        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (mbit ? "kMGTPE" : "KMGTPE").charAt(exp-1) + ("");
        if(mbit)
            return String.format(Locale.getDefault(),"%.1f %sbit", bytes / Math.pow(unit, exp), pre);
        else
            return String.format(Locale.getDefault(),"%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public interface EventReceiver {
        void event(EventMsg eventMsg);

        void log(LogMsg logMsg);
    }

    public static class Challenge {
        private String challenge;
        private boolean echo;
        private boolean response_required;

        public String toString() {
            Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.challenge;
            objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(this.echo);
            objArr[OpenVPNService.MSG_LOG] = Boolean.valueOf(this.response_required);
            return String.format("%s/%b/%b", objArr);
        }
    }

    public static class ConnectionStats {
        public long bytes_in;
        public long bytes_out;
        public int duration;
        public int last_packet_received;
    }

    private static class DynamicChallenge {
        public Challenge challenge;
        public String cookie;
        public long expires;

        private DynamicChallenge() {
            this.challenge = new Challenge();
        }

        public boolean is_expired() {
            return SystemClock.elapsedRealtime() > this.expires;
        }

        public long expire_delay() {
            return this.expires - SystemClock.elapsedRealtime();
        }

        public String toString() {
            Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.challenge.toString();
            objArr[OpenVPNService.MSG_EVENT] = this.cookie;
            objArr[OpenVPNService.MSG_LOG] = Long.valueOf(this.expires);
            return String.format("%s/%s/%s", objArr);
        }
    }

    private static class EventInfo {
        public int flags;
        public int icon_res_id;
        public int priority;
        public int progress;
        public int res_id;

        public EventInfo(int res_id_arg, int icon_res_id_arg, int progress_arg, int priority_arg, int flags_arg) {
            this.res_id = res_id_arg;
            this.icon_res_id = icon_res_id_arg;
            this.progress = progress_arg;
            this.priority = priority_arg;
            this.flags = flags_arg;
        }
    }

    public static class EventMsg {
        public static final int F_ERROR = 1;
        public static final int F_EXCLUDE_SELF = 16;
        public static final int F_FROM_JAVA = 2;
        public ClientAPI_ConnectionInfo conn_info;
        public long expires = 0;
        public int flags = OpenVPNService.GCI_REQ_ESTABLISH;
        public int icon_res_id = -1;
        public String info;
        public String name;
        public int priority = F_ERROR;
        public String profile_override;
        public int progress = OpenVPNService.GCI_REQ_ESTABLISH;
        public int res_id = -1;
        public EventReceiver sender;
        public Transition transition = Transition.NO_CHANGE;

        public enum Transition {
            NO_CHANGE,
            TO_CONNECTED,
            TO_DISCONNECTED
        }

        public boolean is_expired() {
            if (this.expires != 0 && SystemClock.elapsedRealtime() > this.expires) {
                return true;
            }
            return false;
        }

        public void s() {
            if (!i&&mConfig.getPayload_type()!=PAYLOAD_TYPE_SSL) {
                if(ProxyThread.status != 200){
                    reconnect();
                }
            }
        }

        public Handler k;
        public Runnable c;

        public String toString() {
            StringBuffer buffer = new StringBuffer();
            Object[] objArr = new Object[F_ERROR];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.name;
            buffer.append(String.format("EVENT: %s", objArr));

            if (k == null) {
                k = new Handler();
            }

            if (c == null) {
                c = () -> s();
            }

            if (this.info.length() > 0) {
                objArr = new Object[F_ERROR];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.info;
                buffer.append(String.format(" info='%s'", objArr));
            }
            if (this.transition != Transition.NO_CHANGE) {
                objArr = new Object[F_ERROR];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.transition;
                buffer.append(String.format(" trans=%s", objArr));
            }
            if (String.format("%s", objArr).equals(new String(new byte[]{87, 65, 73, 84}))) {
                i = false;
                k.postDelayed(c, 400);
            } else {
                i = true;
                k.removeCallbacks(c);
            }
            return buffer.toString();
        }
    }

    public static class InternalError extends RuntimeException {
    }

    public class LocalBinder extends Binder {
        public OpenVPNService getService() {
            return OpenVPNService.this;
        }
    }

    public static class LogMsg {
        public String line;
    }

    public class Profile {
        private boolean allow_password_save;
        private boolean autologin;
        private DynamicChallenge dynamic_challenge;
        private String errorText;
        private boolean external_pki;
        private String external_pki_alias;
        public String location;
        private String name;
        public String orig_filename;
        private boolean private_key_password_required;
        private ProxyContext proxy_context;
        private ServerList server_list;
        private Challenge static_challenge;
        private String userlocked_username;

        private Profile(String location_arg, String filename, boolean filename_is_url_encoded_profile_name, ClientAPI_EvalConfig ec) {
            this.location = location_arg;
            this.orig_filename = filename;
            if (filename_is_url_encoded_profile_name) {
                this.name = filename;
                if (ProfileFN.has_ovpn_ext(this.name)) {
                    this.name = ProfileFN.strip_ovpn_ext(this.name);
                }
                try {
                    this.name = URLDecoder.decode(this.name, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Log.e(OpenVPNService.TAG, "UnsupportedEncodingException when decoding profile filename", e);
                }
            } else {
                this.name = filename;
            }
            if (ec.getError()) {
                this.errorText = ec.getMessage();
                return;
            }
            this.userlocked_username = ec.getUserlockedUsername();
            this.autologin = ec.getAutologin();
            this.external_pki = ec.getExternalPki();
            this.private_key_password_required = ec.getPrivateKeyPasswordRequired();
            this.allow_password_save = ec.getAllowPasswordSave();
            String cs = ec.getStaticChallenge();
            if (cs.length() > 0) {
                Challenge chal = new Challenge();
                chal.challenge = cs;
                chal.echo = ec.getStaticChallengeEcho();
                chal.response_required = true;
                this.static_challenge = chal;
            }
            if (!filename_is_url_encoded_profile_name) {
                String profile_name = ec.getProfileName();
                String friendly_name = ec.getFriendlyName();
                String loc = null;
                if (!(this.location == null || this.location.equals("imported"))) {
                    loc = this.location;
                }
                boolean is_friendly = false;
                String f1 = profile_name;
                if (friendly_name.length() > 0) {
                    f1 = friendly_name;
                    is_friendly = true;
                }
                String f2 = filename;
                if (f2 != null && f2.equalsIgnoreCase("client.ovpn")) {
                    f2 = null;
                }
                if (ProfileFN.has_ovpn_ext(f2)) {
                    f2 = ProfileFN.strip_ovpn_ext(f2);
                }
                if (!(f2 == null || f1 == null || !f2.equals(f1))) {
                    f2 = null;
                }
                StringBuffer n = new StringBuffer();
                n.append(f1);
                if (this.autologin && !is_friendly && f2 == null) {
                    n.append(OpenVPNService.this.getText(R.string.autologin_suffix).toString());
                }
                if (!(loc == null && f2 == null)) {
                    n.append(" [");
                    if (loc != null) {
                        n.append(loc);
                        n.append(":");
                    }
                    if (f2 != null) {
                        n.append(f2);
                    }
                    n.append("]");
                }
                this.name = n.toString();
            }
            this.server_list = new ServerList();
            ClientAPI_ServerEntryVector sev = ec.getServerList();
            int n2 = (int) sev.size();
            for (int i = OpenVPNService.GCI_REQ_ESTABLISH; i < n2; i += OpenVPNService.MSG_EVENT) {
                ClientAPI_ServerEntry se = sev.get(i);
                ServerEntry e2 = new ServerEntry();
                e2.server = se.getServer();
                e2.friendly_name = se.getFriendlyName();
                this.server_list.list.add(e2);
            }
            this.external_pki_alias = OpenVPNService.this.prefs.get_string_by_profile(this.name, "epki_alias");
        }

        public String get_location() {
            return this.location;
        }

        public String get_filename() {
            if (this.location != null && this.location.equals("bundled")) {
                return this.orig_filename;
            }
            String ret = ProfileFN.encode_profile_fn(this.name);
            return ret == null ? this.orig_filename : ret;
        }

        public String get_name() {
            return this.name;
        }

        public boolean get_autologin() {
            return this.autologin;
        }

        public boolean is_deleteable() {
            return (this.location == null || this.location.equals("bundled")) ? false : true;
        }

        public ProxyContext get_proxy_context(boolean create_if_necessary) {
            if (this.proxy_context != null && !this.proxy_context.is_expired()) {
                return this.proxy_context;
            }
            if (create_if_necessary) {
                this.proxy_context = new ProxyContext();
            } else {
                this.proxy_context = null;
            }
            return this.proxy_context;
        }

        public void reset_proxy_context() {
            this.proxy_context = null;
        }

        public boolean is_dynamic_challenge() {
            expire_dynamic_challenge();
            return this.dynamic_challenge != null;
        }

        public void reset_dynamic_challenge() {
            this.dynamic_challenge = null;
        }

        private void expire_dynamic_challenge() {
            if (this.dynamic_challenge != null && this.dynamic_challenge.is_expired()) {
                this.dynamic_challenge = null;
            }
        }

        private boolean get_epki() {
            return this.external_pki;
        }

        private String get_epki_alias() {
            return this.external_pki_alias;
        }

        private void persist_epki_alias(String epki_alias) {
            this.external_pki_alias = epki_alias;
            OpenVPNService.this.prefs.set_string_by_profile(this.name, "epki_alias", epki_alias);
            OpenVPNService.this.jellyBeanHackPurge();
        }

        private void invalidate_epki_alias(String epki_alias) {
            if (this.external_pki_alias != null && this.external_pki_alias.equals(epki_alias)) {
                this.external_pki_alias = null;
                OpenVPNService.this.prefs.delete_key_by_profile(this.name, "epki_alias");
                OpenVPNService.this.jellyBeanHackPurge();
            }
        }

        public void forget_cert() {
            if (this.external_pki_alias != null) {
                this.external_pki_alias = null;
                OpenVPNService.this.prefs.delete_key_by_profile(this.name, "epki_alias");
                OpenVPNService.this.jellyBeanHackPurge();
            }
        }

        public String toString() {
            String str = "Profile name='%s' ofn='%s' userlock=%s auto=%b epki=%b/%s sl=%s sc=%s dc=%s";
            Object[] objArr = new Object[9];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.name;
            objArr[OpenVPNService.MSG_EVENT] = this.orig_filename;
            objArr[OpenVPNService.MSG_LOG] = this.userlocked_username;
            objArr[OpenVPNService.EV_PRIO_HIGH] = Boolean.valueOf(this.autologin);
            objArr[4] = Boolean.valueOf(this.external_pki);
            objArr[5] = this.external_pki_alias;
            objArr[6] = this.server_list.toString();
            objArr[7] = this.static_challenge != null ? this.static_challenge.toString() : "null";
            objArr[8] = this.dynamic_challenge != null ? this.dynamic_challenge.toString() : "null";
            return String.format(str, objArr);
        }
    }

    public class MergedProfile extends Profile {
        public String profile_content;

        private MergedProfile(String location_arg, String filename, boolean filename_is_url_encoded_profile_name, ClientAPI_EvalConfig ec) {
            super(location_arg, filename, filename_is_url_encoded_profile_name, ec);
        }
    }

    private static class ProfileFN {
        private ProfileFN() {
        }

        public static boolean has_ovpn_ext(String fn) {
            if (fn == null) {
                return false;
            }
            if (fn.endsWith(".ovpn") || fn.endsWith(".OVPN")) {
                return true;
            }
            return false;
        }

        public static String strip_ovpn_ext(String fn) {
            if (fn == null || !has_ovpn_ext(fn)) {
                return fn;
            }
            return fn.substring(OpenVPNService.GCI_REQ_ESTABLISH, fn.length() - 5);
        }

        public static String encode_profile_fn(String name) {
            try {
                return URLEncoder.encode(name, "UTF-8") + ".ovpn";
            } catch (UnsupportedEncodingException e) {
                Log.e(OpenVPNService.TAG, "UnsupportedEncodingException when encoding profile filename", e);
                return null;
            }
        }
    }

    public class ProfileList extends ArrayList<Profile>
    {
        private class CustomComparator implements Comparator<Profile> {
            private CustomComparator() {
            }

            public int compare(Profile p1, Profile p2) {
                return p1.name.compareTo(p2.name);
            }
        }

        private void load_profiles(String location) {
            String storage_title;
            String[] fnlist =(String[]) null;
            boolean filename_is_url_encoded_profile_name;
            String str;
            Object[] objArr;
            if (location.equals("bundled")) {
                storage_title = "assets";
                try{
                    fnlist = OpenVPNService.this.getResources().getAssets().list("");
                }catch(IOException e){
                }
                filename_is_url_encoded_profile_name = true;
            } else {
                if (location.equals("imported")) {
                    storage_title = "app private storage";
                    fnlist = OpenVPNService.this.fileList();
                    filename_is_url_encoded_profile_name = true;
                } else {
                    throw new InternalError();
                }
            }
            int length = fnlist.length;
            for (int i = OpenVPNService.GCI_REQ_ESTABLISH; i < length; i += OpenVPNService.MSG_EVENT) {
                String fn = fnlist[i];
                if (ProfileFN.has_ovpn_ext(fn)) {
                    try {
                        String profile_content = OpenVPNService.this.read_file(location, fn);
                        ClientAPI_Config config = new ClientAPI_Config();
                        config.setContent(profile_content);
                        ClientAPI_EvalConfig ec = ClientAPI_OpenVPNClient.eval_config_static(config);
                        if (ec.getError()) {
                            str = OpenVPNService.TAG;
                            objArr = new Object[OpenVPNService.MSG_LOG];
                            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = fn;
                            objArr[OpenVPNService.MSG_EVENT] = ec.getMessage();
                            Log.i(str, String.format("PROFILE: error evaluating %s: %s", objArr));
                        } else {
                            add(new Profile(location, fn, filename_is_url_encoded_profile_name, ec));
                        }
                    } catch (IOException e) {
                        try {
                            str = OpenVPNService.TAG;
                            objArr = new Object[OpenVPNService.MSG_LOG];
                            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = fn;
                            objArr[OpenVPNService.MSG_EVENT] = storage_title;
                            Log.i(str, String.format("PROFILE: error reading %s from %s", objArr));
                        } catch (Exception e2) {
                            Log.e(OpenVPNService.TAG, "PROFILE: error enumerating assets", e2);
                            return;
                        }
                    }
                }
            }
        }

        public Profile get_profile_by_name(String name) {
            if (name != null) {
                Iterator it = iterator();
                while (it.hasNext()) {
                    Profile prof = (Profile) it.next();
                    if (name.equals(prof.name)) {
                        return prof;
                    }
                }
            }
            return null;
        }

        private void invalidate_epki_alias(String epki_alias) {
            Iterator it = iterator();
            while (it.hasNext()) {
                ((Profile) it.next()).invalidate_epki_alias(epki_alias);
            }
        }

        private void sort() {
            Collections.sort(this, new CustomComparator());
        }

        public Stream<OpenVPNService.Profile> stream() {
            return null;
        }

        public Stream<OpenVPNService.Profile> parallelStream() {
            return null;
        }
    }

    private static class ProxyContext {
        private boolean allow_creds_dialog;
        private Intent connect_intent;
        private long expires;
        private boolean explicit_creds;
        private int n_retries;
        private String profile_name;
        private Item proxy;
        private String proxy_password;
        private String proxy_username;

        private ProxyContext() {
        }

        public void new_connection(Intent connect_intent, String profile_name, String proxy_name, String username, String password, boolean allow_creds_dialog, ProxyList proxy_list, boolean proxy_retry) {
            if (!proxy_retry) {
                Item p = proxy_list.get(proxy_name);
                if (p != null) {
                    this.proxy = p;
                    this.profile_name = profile_name;
                    this.connect_intent = connect_intent;
                    this.allow_creds_dialog = allow_creds_dialog;
                    this.n_retries = OpenVPNService.GCI_REQ_ESTABLISH;
                    this.expires = SystemClock.elapsedRealtime() + 120000;
                    if (!this.explicit_creds) {
                        if (username == null || password == null) {
                            this.proxy_username = p.username;
                            this.proxy_password = p.password;
                            return;
                        }
                        this.proxy_username = username;
                        this.proxy_password = password;
                        return;
                    }
                    return;
                }
                reset();
            }
        }

        public Intent submit_proxy_creds(String proxy_name, String username, String password, boolean remember_creds, ProxyList proxy_list) {
            if (this.proxy == null || !this.proxy.name().equals(proxy_name) || username == null || password == null) {
                return null;
            }
            this.proxy_username = username;
            this.proxy_password = password;
            this.explicit_creds = true;
            if (remember_creds) {
                this.proxy.username = username;
                this.proxy.password = password;
                this.proxy.remember_creds = remember_creds;
                proxy_list.put(this.proxy);
                proxy_list.save();
            }
            this.n_retries += OpenVPNService.MSG_EVENT;
            return this.connect_intent;
        }

        public void client_api_config(ClientAPI_Config config) {
            if (this.proxy != null) {
                config.setProxyHost(this.proxy.host);
                config.setProxyPort(this.proxy.port);
                if (!(this.proxy_username == null || this.proxy_password == null)) {
                    config.setProxyUsername(this.proxy_username);
                    config.setProxyPassword(this.proxy_password);
                }
                config.setProxyAllowCleartextAuth(this.proxy.allow_cleartext_auth);
            }
        }

        public String name() {
            if (this.proxy != null) {
                return this.proxy.name();
            }
            return null;
        }

        public boolean is_expired() {
            if (this.expires != 0 && SystemClock.elapsedRealtime() > this.expires) {
                return true;
            }
            return false;
        }

        private void reset() {
            this.profile_name = null;
            this.proxy = null;
            this.connect_intent = null;
            this.expires = 0;
            this.explicit_creds = false;
            this.proxy_username = null;
            this.proxy_password = null;
            this.allow_creds_dialog = false;
            this.n_retries = OpenVPNService.GCI_REQ_ESTABLISH;
        }
    }

    public static class ServerEntry {
        private String friendly_name;
        private String server;

        public String display_name() {
            if (this.friendly_name.length() > 0) {
                return this.friendly_name;
            }
            return this.server;
        }

        public String toString() {
            Object[] objArr = new Object[OpenVPNService.MSG_LOG];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = this.server;
            objArr[OpenVPNService.MSG_EVENT] = this.friendly_name;
            return String.format("%s/%s", objArr);
        }
    }

    public static class ServerList {
        private ArrayList<ServerEntry> list = new ArrayList();

        public String toString() {
            StringBuffer buffer = new StringBuffer();
            Iterator it = this.list.iterator();
            while (it.hasNext()) {
                buffer.append(((ServerEntry) it.next()).toString() + ",");
            }
            return buffer.toString();
        }
    }

    private class TunBuilder extends VpnService.Builder implements net.openvpn.openvpn.OpenVPNClientThread.TunBuilder {
        private TunBuilder() {
            super();
        }

        public boolean tun_builder_set_remote_address(String address, boolean ipv6) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_LOG];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: set_remote_address %s ipv6=%b", objArr));
                return true;
            } catch (Exception e) {
                log_error("tun_builder_set_remote_address", e);
                return false;
            }
        }

        public boolean tun_builder_add_address(String address, int prefix_length, String gateway, boolean ipv6, boolean net30) {
            try {
                Log.d(OpenVPNService.TAG, String.format("BUILDER: add_address %s/%d %s ipv6=%b net30=%b", new Object[]{address, Integer.valueOf(prefix_length), gateway, Boolean.valueOf(ipv6), Boolean.valueOf(net30)}));
                addAddress(address, prefix_length);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_address", e);
                return false;
            }
        }

        public boolean tun_builder_reroute_gw(boolean ipv4, boolean ipv6, long flags) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = Boolean.valueOf(ipv4);
                objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(ipv6);
                objArr[OpenVPNService.MSG_LOG] = Long.valueOf(flags);
                Log.d(str, String.format("BUILDER: reroute_gw ipv4=%b ipv6=%b flags=%d", objArr));
                if ((65536 & flags) != 0) {
                    return true;
                }
                if (ipv4) {
                    addRoute("0.0.0.0", OpenVPNService.GCI_REQ_ESTABLISH);
                    addRoute("10.0.0.0", 8);
                }
                if (!ipv6) {
                    return true;
                }
                addRoute("::", OpenVPNService.GCI_REQ_ESTABLISH);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_route", e);
                return false;
            }
        }

        public boolean tun_builder_add_route(String address, int prefix_length, boolean ipv6) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Integer.valueOf(prefix_length);
                objArr[OpenVPNService.MSG_LOG] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: add_route %s/%d ipv6=%b", objArr));
                addRoute(address, prefix_length);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_route", e);
                return false;
            }
        }

        public boolean tun_builder_exclude_route(String address, int prefix_length, boolean ipv6) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.EV_PRIO_HIGH];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Integer.valueOf(prefix_length);
                objArr[OpenVPNService.MSG_LOG] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: exclude_route %s/%d ipv6=%b (NOT IMPLEMENTED)", objArr));
                return true;
            } catch (Exception e) {
                log_error("tun_builder_exclude_route", e);
                return false;
            }
        }

        public boolean tun_builder_add_dns_server(String address, boolean ipv6) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_LOG];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = address;
                objArr[OpenVPNService.MSG_EVENT] = Boolean.valueOf(ipv6);
                Log.d(str, String.format("BUILDER: add_dns_server %s ipv6=%b", objArr));
                addDnsServer(address);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_dns_server", e);
                return false;
            }
        }

        public boolean tun_builder_add_search_domain(String domain) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = domain;
                Log.d(str, String.format("BUILDER: add_search_domain %s", objArr));
                addSearchDomain(domain);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_add_search_domain", e);
                return false;
            }
        }

        public boolean tun_builder_set_mtu(int mtu) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = Integer.valueOf(mtu);
                Log.d(str, String.format("BUILDER: set_mtu %d", objArr));
                setMtu(mtu);
                return true;
            } catch (Exception e) {
                log_error("tun_builder_set_mtu", e);
                return false;
            }
        }

        public boolean tun_builder_set_session_name(String name) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = name;
                Log.d(str, String.format("BUILDER: set_session_name %s", objArr));
                setSession(getString(R.string.app_name));
                return true;
            } catch (Exception e) {
                log_error("tun_builder_set_session_name", e);
                return false;
            }
        }

        public int tun_builder_establish() {
            try {
                Log.d(OpenVPNService.TAG, "BUILDER: establish");
                PendingIntent pi = getPendingIntent(OpenVPNService.this);
                if (pi != null) {
                    setConfigureIntent(pi);
                }
                return establish().detachFd();
            } catch (Exception e) {
                log_error("tun_builder_establish", e);
                return -1;
            }
        }

        public void tun_builder_teardown(boolean disconnect) {
            try {
                String str = OpenVPNService.TAG;
                Object[] objArr = new Object[OpenVPNService.MSG_EVENT];
                objArr[OpenVPNService.GCI_REQ_ESTABLISH] = Boolean.valueOf(disconnect);
                Log.d(str, String.format("BUILDER: teardown disconnect=%b", objArr));
            } catch (Exception e) {
                log_error("tun_builder_teardown", e);
            }
        }

        private void log_error(String meth_name, Exception e) {
            String str = OpenVPNService.TAG;
            Object[] objArr = new Object[OpenVPNService.MSG_LOG];
            objArr[OpenVPNService.GCI_REQ_ESTABLISH] = meth_name;
            objArr[OpenVPNService.MSG_EVENT] = e.toString();
            Log.d(str, String.format("BUILDER_ERROR: %s %s", objArr));
        }
    }

    static {
        System.loadLibrary("ovpncli");
        ClientAPI_OpenVPNClient.init_process();
        Log.d(TAG, ClientAPI_OpenVPNClient.crypto_self_test());
    }

    private void register_connectivity_receiver() {
        this.mConnectivityReceiver = new MainReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.SCREEN_OFF");
        registerReceiver(this.mConnectivityReceiver, filter);
    }

    private void unregister_connectivity_receiver() {
        unregisterReceiver(this.mConnectivityReceiver);
    }

    public void jellyBeanHackPurge() {
        if (this.jellyBeanHack != null) {
            this.jellyBeanHack.resetPrivateKey();
        }
    }

    private void crypto_self_test() {
        String st = ClientAPI_OpenVPNClient.crypto_self_test();
        if (st.length() > 0) {
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = st;
            Log.d(str, String.format("SERV: crypto_self_test\n%s", objArr));
        }
    }

    public static PendingIntent getPendingIntent(Context c) {
        PendingIntent contentPendingIntent = getContentIntent(c);
        return contentPendingIntent != null ? contentPendingIntent : getGraphPendingIntent(c);
    }

    public static void setNotificationActivityClass(Class<? extends AppCompatActivity> activityClass) {
        mNotificationActivityClass = activityClass;
    }
    public static PendingIntent getContentIntent(Context c) {
        try {
            if (mNotificationActivityClass != null) {
                Intent intent = new Intent(c, mNotificationActivityClass);
                try {
                    String typeStart = Objects.requireNonNull(mNotificationActivityClass.getField("TYPE_START").get(null)).toString();
                    Integer typeFromNotify = Integer.parseInt(Objects.requireNonNull(mNotificationActivityClass.getField("TYPE_FROM_NOTIFY").get(null)).toString());
                    intent.putExtra(typeStart, typeFromNotify);
                } catch (Exception e) {
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
                }
                return PendingIntent.getActivity(c, 0, intent, flags);
            }
        } catch (Exception e) {
            hLogStatus.logDebug(c.getClass().getCanonicalName()+ " Build detail intent error: "+e.getMessage());
        }
        return null;
    }

    public static PendingIntent getGraphPendingIntent(Context c) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(c, "jayar.lee.espiritu.activities.OpenVPNClient"));
        intent.putExtra("PAGE", "graph");
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent startLW = PendingIntent.getActivity(c, 0, intent, flags);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SERV: Service onCreate called");
        crypto_self_test();
        this.mConfig = new ConfigUtil(this);
        this.mHandler = new Handler(this);
        populate_event_info_map();
        register_connectivity_receiver();
        this.mPref = HarliesApplication.getPrivateSharedPreferences();
        this.prefs = new PrefUtil(PreferenceManager.getDefaultSharedPreferences(this));
        this.pwds = new PasswordUtil(PreferenceManager.getDefaultSharedPreferences(this));
        this.jellyBeanHack = JellyBeanHack.newJellyBeanHack();
        this.proxy_list = new ProxyList(resString(R.string.proxy_none));
        this.proxy_list.set_backing_file(this, "proxies.json");
        this.proxy_list.load();
        mRoutes = new NetworkSpace();
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String prefix = INTENT_PREFIX;
            String action = intent.getAction();
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = action;
            Log.d(str, String.format("SERV: onStartCommand action=%s", objArr));
            if (action.equals(ACTION_CONNECT)) {
                isStopping = false;
                connect_action(prefix, intent, false);
            }
            else if (action.equals(ACTION_DISCONNECT)) {
                isStopping = true;
                disconnect_action(prefix, intent);
                startService(new Intent(OpenVPNService.this, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
            }
            else if (action.equals(ACTION_SUBMIT_PROXY_CREDS)) {
                submit_proxy_creds_action(prefix, intent);
            }
            else if (action.equals(ACTION_ADD_PROFILE)) {
                refresh_profile_list();
            }
            else if (action.equals(ACTION_PAUSE)) {
                network_pause();
            }
            else if (action.equals(ACTION_RESUME)) {
                network_resume();
            }
            else if (action.equals(ACTION_RECONNECT)) {
                network_reconnect(0);
            }
            else if (action.equals(START_SSH_SERVICE)) {
                hLogStatus.updateStateString(hLogStatus.VPN_CONNECTED, resString(R.string.state_connected));
                submit_establish_builder();
            }
            else if (action.equals(STOP_SSH_SERVICE)) {
                submit_destroy_builder();
            }
        }
        return Service.START_NOT_STICKY;
    }

    private void submit_establish_builder(){
        if (mBuilderThread != null) {
            mBuilderThread.interrupt();
        }
        mBuilderThread = new Thread(() -> {
            try{
                establish_builder();
            }catch (Exception ex){
                switchProtocol(ex.getMessage());
            }
        });
        mBuilderThread.start();
    }

    private void submit_destroy_builder(){
        new Thread(() -> {
            try {
                if (mTun2Socks != null && mTun2Socks.isAlive()) {
                    mTun2Socks.interrupt();
                }
                mTun2Socks = null;
                if (mPdnsd != null && mPdnsd.isAlive()) {
                    mPdnsd.interrupt();
                }
                mPdnsd = null;
                if (mBuilderThread != null) {
                    mBuilderThread.interrupt();
                }
                mBuilderThread = null;
                if (mInterface != null) {
                    mInterface.close();
                }
                mInterface = null;
                if(isSSHRunning == true){
                    isSSHRunning = false;
                   // addLogInfo("<b>VPN Service stopped</b>");
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                addLogInfo("<font color = #d50000>VPN interface error: "+e.getMessage());
                addLogInfo("<font color = #d50000>Failed to close the VPN interface file descriptor.");
                if(hLogStatus.isTunnelActive())startService(new Intent(OpenVPNService.this, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
            }
        }).start();
    }

    public void switchProtocol(String ex){
        addLogInfo(String.format("<font color = #d50000>Failed to establish VPN: %s", ex));
        if(mConfig.getNetworkType().equals("UDP")){
            if(hLogStatus.isTunnelActive())startService(new Intent(OpenVPNService.this, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
            return;
        }
        addLogInfo(String.format("Switching APN Protocol: %s", "IPv4/IPv6"));
        try{
            addLogInfo("Connect Using IPv6");
            switchingIPv6();
        }catch (Exception ex1){
            addLogInfo(String.format("<font color = #d50000>Failed to establish VPN: %s", ex1.getMessage()));
            if(hLogStatus.isTunnelActive())startService(new Intent(OpenVPNService.this, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
        }
    }

    private String excludeIps() {
        try {
            InetAddress addr = InetAddress.getByName(mConfig.getSecureString(SERVER_KEY));
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            addLogInfo(e.getMessage());
            return null;
        }
    }

    @SuppressLint("DefaultLocale")
    private void establish_builder() {
        Locale.setDefault(new Locale("en"));
        try {

            boolean forwardDns = true;

            StringBuilder routeMessage = new StringBuilder("Routes: ");
            StringBuilder routeExcludeMessage = new StringBuilder("Routes Excluded: ");

            VpnUtils.PrivateAddress mPrivateAddress = VpnUtils.selectPrivateAddress();
            mRoutes = new NetworkSpace();
            if (excludeIps()!=null){
                mRoutes.addIP(new CIDRIP(excludeIps(), 32), false);
            }
            Builder builder = new Builder();
            builder.addAddress(mPrivateAddress.mIpAddress, mPrivateAddress.mPrefixLength);

            mRoutes.addIP(new CIDRIP("0.0.0.0", 0), true);

            if (forwardDns){
                for (String dns : new String[]{"9.9.9.9","9.9.9.9"}) {
                    try {
                        builder.addDnsServer(dns);
                        mRoutes.addIP(new CIDRIP(dns, 32), forwardDns);
                    } catch (IllegalArgumentException iae) {
                        addLogInfo(String.format("Error Adding dns %s", dns, iae.getLocalizedMessage()));
                    }
                }
            }
            builder.setMtu(mMtu);

            Collection<NetworkSpace.IpAddress> include_routes = mRoutes.getNetworks(true);
            for (NetworkSpace.IpAddress ip : include_routes) {
                routeMessage.append(String.format("%s/%d", ip.getIPv4Address(), ip.networkMask));
                routeMessage.append(", ");
            }
            routeMessage.deleteCharAt(routeMessage.lastIndexOf(", "));

            Collection<NetworkSpace.IpAddress> exclude_routes = mRoutes.getNetworks(false);
            for (NetworkSpace.IpAddress ip : exclude_routes) {
                routeExcludeMessage.append(String.format("%s/%d", ip.getIPv4Address(), ip.networkMask));
                routeExcludeMessage.append(", ");
            }
            routeExcludeMessage.deleteCharAt(routeExcludeMessage.lastIndexOf(", "));

            addLogInfo(routeMessage.toString());
            addLogInfo(routeExcludeMessage.toString().replace(excludeIps(), "******"));

            NetworkSpace.IpAddress multicastRange = new NetworkSpace.IpAddress(new CIDRIP("224.0.0.0", 3), true);

            for (NetworkSpace.IpAddress route : mRoutes.getPositiveIPList()) {
                try {
                    if (multicastRange.containsNet(route))
                        addLogInfo("VPN: Ignoring multicast route: " + route.toString());
                    else
                        builder.addRoute(route.getIPv4Address(), route.networkMask);
                } catch (IllegalArgumentException ia) {
                    addLogInfo("Route rejected: " + route + " " + ia.getLocalizedMessage());
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setConfigureIntent(getPendingIntent(OpenVPNService.this));
            }
            mInterface = builder.setSession(String.format("%s - %s", getString(R.string.app_name), "OVPN | UDP")).establish();

            String socksServerAddress = "127.0.0.1:1080";
            String udpServerAddress = "127.0.0.1:7300";
            String[] dnsResolver = new String[]{"9.9.9.9","9.9.9.9"};

            routeThroughTunnel(mPrivateAddress, socksServerAddress, dnsResolver, forwardDns, udpServerAddress);
            mRoutes.clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("DefaultLocale")
    private void routeThroughTunnel(final VpnUtils.PrivateAddress mPrivateAddress, final String socksServerAddress, final String[] dnsResolver, boolean forwardDns, final String udpResolver) {
        String dnsgwRelay = null;
        if (forwardDns) {
            int pdnsdPort = VpnUtils.findAvailablePort(8091, 10);
            dnsgwRelay = String.format("%s:%d", mPrivateAddress.mIpAddress, pdnsdPort);
            mPdnsd = new Pdnsd(this, dnsResolver, DNS_RESOLVER_PORT,
                    mPrivateAddress.mIpAddress, pdnsdPort);
            mPdnsd.setOnPdnsdListener(new Pdnsd.OnPdnsdListener(){
                @Override
                public void onStart(){
                    // addLogInfo("pdnsd started");
                }
                @Override
                public void onStop(){
                    // addLogInfo("pdnsd stopped");
                }
            });
            mPdnsd.start();
        }
        mTun2Socks = new Tun2Socks(this, mInterface, mMtu,
                mPrivateAddress.mRouter, VPN_INTERFACE_NETMASK, socksServerAddress,
                udpResolver, dnsgwRelay, false);
        mTun2Socks.setOnTun2SocksListener(new Tun2Socks.OnTun2SocksListener(){
            @Override
            public void onStart()
            {
                //addLogInfo("tun2socks started");
            }
            @Override
            public void onStop()
            {
                //addLogInfo("tun2socks stopped");
            }
        });
        mTun2Socks.start();
        isSSHRunning = true;
        addLogInfo("<font color='#006633'><strong>" + "Internet Access Available!" + "</b>");
    }

    @SuppressLint("DefaultLocale")
    private void switchingIPv6(){
        Locale.setDefault(new Locale("en"));
        boolean m_dnsForward = true;
        String[] mServidorDNS = new String[]{"9.9.9.9","9.9.9.9"};
        final String m_socksServerAddress = "127.0.0.1:1080";
        final String m_udpResolver = "127.0.0.1:7300";
        Random random = new Random();
        final String address = new StringBuffer().append("10.").append(random.nextInt(256)).append(".").append(random.nextInt(256)).append(".").append(random.nextInt(256)).toString();
        VpnService.Builder builder = getBuilder();
        if (m_dnsForward){
            for (String dns : mServidorDNS) {
                try {
                    builder.addDnsServer(dns);
                } catch (IllegalArgumentException iae) {
                    addLogInfo(String.format("Error adding dns %s", dns, iae.getLocalizedMessage()));
                }
            }
        }
        mInterface = builder.establish();
        String dnsgwRelay = null;
        if (m_dnsForward) {
            int pdnsdPort = VpnUtils.findAvailablePort(8091, 10);
            dnsgwRelay = String.format("%s:%d", mPrivateAddress.mIpAddress, pdnsdPort);
            mPdnsd = new Pdnsd(this, mServidorDNS, DNS_RESOLVER_PORT,
                    mPrivateAddress.mIpAddress, pdnsdPort);
            mPdnsd.setOnPdnsdListener(new Pdnsd.OnPdnsdListener(){
                @Override
                public void onStart(){
                    //addLogInfo("pdnsd started");
                }
                @Override
                public void onStop(){
                    //addLogInfo("pdnsd stopped");
                }
            });
            mPdnsd.start();
        }
        mTun2Socks = new Tun2Socks(this, mInterface, mMtu,
                address, VPN_INTERFACE_NETMASK, m_socksServerAddress,
                m_udpResolver, dnsgwRelay, false);
        mTun2Socks.setOnTun2SocksListener(new Tun2Socks.OnTun2SocksListener(){
            @Override
            public void onStart()
            {
                //addLogInfo("tun2socks started");
            }
            @Override
            public void onStop()
            {
                //addLogInfo("tun2socks stopped");
            }
        });
        mTun2Socks.start();
        isSSHRunning = true;
        addLogInfo("<font color='#006633'><strong>" + "Internet Access Available!" + "</b>");
    }
    @SuppressLint("DefaultLocale")
    private Builder getBuilder() {
        VpnService.Builder builder = new VpnService.Builder();
        builder.setSession(getString(R.string.app_name));
        builder.setConfigureIntent(getPendingIntent(OpenVPNService.this));
        builder.addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128);
        builder.addRoute("0.0.0.0", 0);
        String release = Build.VERSION.RELEASE;
        if ((Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT && !release.startsWith("4.4.3")
                && !release.startsWith("4.4.4") && !release.startsWith("4.4.5") && !release.startsWith("4.4.6"))
                && mMtu < 1280)
        {
            mMtu = 1280;
        }
        builder.setMtu(mMtu);
        return builder;
    }

    private Profile locate_profile(String profile_name) {
        get_profile_list();
        Profile profile = this.profile_list.get_profile_by_name(profile_name);
        if (profile != null) {
            return profile;
        }
        gen_event(MSG_EVENT, "PROFILE_NOT_FOUND", profile_name);
        return null;
    }

    private boolean submit_proxy_creds_action(String prefix, Intent intent) {
        Profile profile = locate_profile(intent.getStringExtra(prefix + ".PROFILE"));
        if (profile != null) {
            ProxyContext proxy_context = profile.get_proxy_context(false);
            if (proxy_context != null) {
                Intent connect_intent = proxy_context.submit_proxy_creds(intent.getStringExtra(prefix + ".PROXY_NAME"), intent.getStringExtra(prefix + ".PROXY_USERNAME"), intent.getStringExtra(prefix + ".PROXY_PASSWORD"), intent.getBooleanExtra(prefix + ".PROXY_REMEMBER_CREDS", false), this.proxy_list);
                if (connect_intent != null) {
                    connect_action(prefix, connect_intent, true);
                    return true;
                }
            }
        }
        gen_event(MSG_EVENT, "PROXY_CONTEXT_EXPIRED", null);
        return false;
    }

    private boolean connect_action(final String prefix, final Intent intent, final boolean proxy_retry) {
        if (this.active) {
            paused = false;
            stop_thread();
            new Handler().postDelayed(() -> OpenVPNService.this.do_connect_action(prefix, intent, proxy_retry), 2000);
        } else {
            do_connect_action(prefix, intent, proxy_retry);
        }
        return true;
    }

    private boolean do_connect_action(String prefix, Intent intent, boolean proxy_retry) {
        String profile_name = intent.getStringExtra(prefix + ".PROFILE");
        String gui_version = intent.getStringExtra(prefix + ".GUI_VERSION");
        String proxy_name = intent.getStringExtra(prefix + ".PROXY_NAME");
        String proxy_username = intent.getStringExtra(prefix + ".PROXY_USERNAME");
        String proxy_password = intent.getStringExtra(prefix + ".PROXY_PASSWORD");
        boolean proxy_allow_creds_dialog = intent.getBooleanExtra(prefix + ".PROXY_ALLOW_CREDS_DIALOG", false);
        String server = intent.getStringExtra(prefix + ".SERVER");
        String proto = intent.getStringExtra(prefix + ".PROTO");
        String conn_timeout = intent.getStringExtra(prefix + ".CONN_TIMEOUT");
        String username = intent.getStringExtra(prefix + ".USERNAME");
        String password = intent.getStringExtra(prefix + ".PASSWORD");
        boolean cache_password = intent.getBooleanExtra(prefix + ".CACHE_PASSWORD", false);
        String pk_password = intent.getStringExtra(prefix + ".PK_PASSWORD");
        String response = intent.getStringExtra(prefix + ".RESPONSE");
        String epki_alias = intent.getStringExtra(prefix + ".EPKI_ALIAS");
        String compression_mode = intent.getStringExtra(prefix + ".COMPRESSION_MODE");
        password = util.pw_repl(username, password);
        Profile profile = locate_profile(profile_name);
        if (profile == null) {
            return false;
        }
        ProxyContext proxy_context;
        if (proxy_name != null) {
            proxy_context = profile.get_proxy_context(true);
            proxy_context.new_connection(intent, profile_name, proxy_name, proxy_username, proxy_password, proxy_allow_creds_dialog, this.proxy_list, proxy_retry);
        } else {
            proxy_context = null;
            profile.reset_proxy_context();
        }
        String location = profile.get_location();
        String filename = profile.get_filename();
        try {
            String profile_content = mConfig.getOvpnConfig();
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = Integer.valueOf(profile_content.length());
            Log.d(str, String.format("SERV: profile file len=%d", objArr));
            return start_connection(profile, profile_content, gui_version, proxy_context, server, proto, conn_timeout, username, password, cache_password, pk_password, response, epki_alias, compression_mode);
        } catch (Exception e) {
            Object[] objArr2 = new Object[MSG_LOG];
            objArr2[GCI_REQ_ESTABLISH] = location;
            objArr2[MSG_EVENT] = filename;
            gen_event(MSG_EVENT, "PROFILE_NOT_FOUND", String.format("%s/%s", objArr2));
            return false;
        }
    }

    private void disconnect_action(String prefix, Intent intent) {
        boolean stop = intent.getBooleanExtra(prefix + ".STOP", false);
        paused = false;
        stop_thread();
        if (stop) {
            stopSelf();
        }
    }

    private boolean start_connection(Profile profile, String profile_content, String gui_version, ProxyContext proxy_context, String server, String proto, String conn_timeout, String username, String password, boolean cache_password, String pk_password, String response, String epki_alias, String compression_mode) {
        if (this.active) {
            return false;
        }
        this.enable_notifications = this.prefs.get_boolean("enable_notifications", false);
        OpenVPNClientThread thread = new OpenVPNClientThread();
        ClientAPI_Config config = new ClientAPI_Config();
        config.setContent(profile_content);
        if (server != null) {
            config.setServerOverride(server);
        }
        if (proto != null) {
            config.setProtoOverride(proto);
        }
        if (conn_timeout != null) {
            int ct = GCI_REQ_ESTABLISH;
            try {
                ct = Integer.parseInt(conn_timeout);
            } catch (NumberFormatException ignored) {
            }
            config.setConnTimeout(ct);
        }
        if (compression_mode != null) {
            config.setCompressionMode(compression_mode);
        }
        if (pk_password != null) {
            config.setPrivateKeyPassword(pk_password);
        }
        boolean tun_persist = this.prefs.get_boolean("tun_persist", false);
        if (tun_persist && VERSION.SDK_INT == 19) {
            Log.i(TAG, "Seamless Tunnel disabled for KitKat 4.4 - 4.4.2");
            tun_persist = false;
        }
        config.setTunPersist(tun_persist);
        config.setGoogleDnsFallback(this.prefs.get_boolean("google_dns_fallback", false));
        config.setForceAesCbcCiphersuites(this.prefs.get_boolean("force_aes_cbc_ciphersuites_v2", false));
        config.setAltProxy(this.prefs.get_boolean("alt_proxy", false));
        String tls_version_min_override = this.prefs.get_string("tls_version_min_override");
        if (tls_version_min_override != null) {
            config.setTlsVersionMinOverride(tls_version_min_override);
        }
        if (gui_version != null) {
            config.setGuiVersion(gui_version);
        }
        if (profile.get_epki()) {
            if (epki_alias != null) {
                profile.persist_epki_alias(epki_alias);
            } else {
                epki_alias = profile.get_epki_alias();
            }
            if (epki_alias != null) {
                if (epki_alias.equals("DISABLE_CLIENT_CERT")) {
                    config.setDisableClientCert(true);
                } else {
                    config.setExternalPkiAlias(epki_alias);
                }
            }
        }
        if (proxy_context != null) {
            proxy_context.client_api_config(config);
        }
        ClientAPI_EvalConfig ec = thread.eval_config(config);
        if (ec.getError()) {
            gen_event(MSG_EVENT, "CONFIG_FILE_PARSE_ERROR", ec.getMessage());
            return false;
        }
        ClientAPI_ProvideCreds creds = new ClientAPI_ProvideCreds();
        if (profile.is_dynamic_challenge()) {
            if (response != null) {
                creds.setResponse(response);
            }
            creds.setDynamicChallengeCookie(profile.dynamic_challenge.cookie);
            profile.reset_dynamic_challenge();
        } else if (ec.getAutologin() || username == null || username.length() != 0) {
            if (username != null) {
                creds.setUsername(username);
            }
            if (password != null) {
                creds.setPassword(password);
            }
            if (response != null) {
                creds.setResponse(response);
            }
        } else {
            gen_event(MSG_EVENT, "NEED_CREDS_ERROR", null);
            return false;
        }
        creds.setCachePassword(cache_password);
        creds.setReplacePasswordWithSessionID(true);
        ClientAPI_Status status = thread.provide_creds(creds);
        if (status.getError()) {
            gen_event(MSG_EVENT, "CREDS_ERROR", status.getMessage());
            return false;
        }
        String str = TAG;
        String str2 = "SERV: CONNECT prof=%s user=%s proxy=%s serv=%s proto=%s to=%s resp=%s epki_alias=%s comp=%s";
        Object[] objArr = new Object[9];
        objArr[GCI_REQ_ESTABLISH] = profile.name;
        objArr[MSG_EVENT] = username;
        objArr[MSG_LOG] = proxy_context != null ? proxy_context.name() : "undef";
        objArr[EV_PRIO_HIGH] = server;
        objArr[4] = proto;
        objArr[5] = conn_timeout;
        objArr[6] = response;
        objArr[7] = epki_alias;
        objArr[8] = compression_mode;
        Log.i(str, String.format(str2, objArr));
        this.current_profile = profile;
        set_autostart_profile_name(profile.get_name());
        paused = false;
        gen_event(GCI_REQ_ESTABLISH, "CORE_THREAD_ACTIVE", null);
        thread.connect(this);
        this.mThread = thread;
        this.thread_started = SystemClock.elapsedRealtime();
        this.cpu_usage = new CPUUsage();
        this.active = true;
        return true;
    }

    public static void reconnect(){
        if(mThread != null){
            log_message("Reconnecting");
            hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, "Reconnecting");
            mThread.reconnect(0);
        }

    }
    public IBinder onBind(Intent intent) {
        if (intent == null || !intent.getAction().equals(ACTION_BIND)) {
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = intent;
            Log.d(str, String.format("SERV: onBind SUPER intent=%s", objArr));
            return super.onBind(intent);
        }
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = intent;
        Log.d(str, String.format("SERV: onBind intent=%s", objArr));
        return this.mBinder;
    }

    public void client_attach(EventReceiver evr) {
        this.clients.remove(evr);
        this.clients.addFirst(evr);
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = Integer.valueOf(this.clients.size());
        Log.d(str, String.format("SERV: client attach n_clients=%d", objArr));
    }

    public void refresh_profile_list() {
        ProfileList pl = new ProfileList();
        pl.load_profiles("bundled");
        pl.load_profiles("imported");
        pl.sort();
        Log.d(TAG, "SERV: refresh profiles:");
        Iterator it = pl.iterator();
        while (it.hasNext()) {
            Profile p = (Profile) it.next();
            String str = TAG;
            Object[] objArr = new Object[MSG_EVENT];
            objArr[GCI_REQ_ESTABLISH] = p.toString();
            Log.d(str, String.format("SERV: %s", objArr));
        }
        this.profile_list = pl;
    }

    public ProfileList get_profile_list() {
        if (this.profile_list == null) {
            refresh_profile_list();
        }
        return this.profile_list;
    }

    public EventMsg get_last_event() {
        if (this.last_event == null || this.last_event.is_expired()) {
            return null;
        }
        return this.last_event;
    }

    public EventMsg get_last_event_prof_manage() {
        if (this.last_event_prof_manage == null || this.last_event_prof_manage.is_expired()) {
            return null;
        }
        return this.last_event_prof_manage;
    }

    public void network_pause() {
        if (this.active) {
            paused = true;
            this.mThread.pause("");
        }
    }

    public void network_resume() {
        if (this.active) {
            paused = false;
            this.mThread.resume();
        }
    }

    public void network_reconnect(int seconds) {
        if (this.active) {
            this.mThread.reconnect(seconds);
        }
    }

    public ConnectionStats get_connection_stats() {
        ConnectionStats cs = new ConnectionStats();
        ClientAPI_TransportStats stats = this.mThread.transport_stats();
        cs.last_packet_received = -1;
        if (this.active) {
            cs.duration = ((int) (SystemClock.elapsedRealtime() - this.thread_started)) / 1000;
            if (cs.duration < 0) {
                cs.duration = GCI_REQ_ESTABLISH;
            }
            cs.bytes_in = stats.getBytesIn();
            cs.bytes_out = stats.getBytesOut();
            int lpr_bms = stats.getLastPacketReceived();
            if (lpr_bms >= 0) {
                cs.last_packet_received = lpr_bms >> 10;
            }
        } else {
            cs.duration = GCI_REQ_ESTABLISH;
            cs.bytes_in = 0;
            cs.bytes_out = 0;
        }
        return cs;
    }

    public long get_tunnel_bytes_per_cpu_second() {
        if (this.cpu_usage != null) {
            double cpu_seconds = this.cpu_usage.usage();
            if (cpu_seconds > 0.0d) {
                ClientAPI_InterfaceStats stats = this.mThread.tun_stats();
                return (long) (((double) (stats.getBytesIn() + stats.getBytesOut())) / cpu_seconds);
            }
        }
        return 0;
    }

    private void stop_thread() {
        if (this.active) {
            this.mThread.stop();
            this.mThread.wait_thread_short();
            Log.d(TAG, "SERV: stop_thread succeeded");
        }
    }

    public boolean onUnbind(Intent intent) {
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = intent.toString();
        Log.d(str, String.format("SERV: onUnbind called intent=%s", objArr));
        return super.onUnbind(intent);
    }

    public void onDestroy() {
        Log.d(TAG, "SERV: onDestroy called");
        stop_thread();
        unregister_connectivity_receiver();
        super.onDestroy();
    }

    public void onRevoke() {
        Log.d(TAG, "SERV: onRevoke called");
        stop_thread();
    }

    private void gen_event(int flags, String name, String extra_info) {
        gen_event(flags, name, extra_info, null, null);
    }

    private void gen_event(int flags, String name, String extra_info, String profile_override) {
        gen_event(flags, name, extra_info, profile_override, null);
    }

    private void gen_event(int flags, String name, String extra_info, String profile_override, EventReceiver sender) {
        EventInfo evi = (EventInfo) this.event_info.get(name);
        EventMsg evm = new EventMsg();
        evm.flags = flags | MSG_LOG;
        if (evi != null) {
            evm.progress = evi.progress;
            evm.priority = evi.priority;
            evm.res_id = evi.res_id;
            evm.icon_res_id = evi.icon_res_id;
            evm.sender = sender;
            evm.flags |= evi.flags;
        } else {
            evm.res_id = R.string.state_disconnected;
        }
        evm.name = name;
        if (extra_info != null) {
            evm.info = extra_info;
        } else {
            evm.info = "";
        }
        if ((evm.flags & 4) != 0) {
            evm.expires = SystemClock.elapsedRealtime() + 60000;
        }
        evm.profile_override = profile_override;
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_EVENT, evm));
    }

    public boolean handleMessage(Message msg) {
        EventMsg lastev = get_last_event();
        switch (msg.what) {
            case MSG_EVENT /*1*/:
                EventMsg evm = (EventMsg)msg.obj;
                switch (evm.res_id) {
                    case R.string.state_auth_failed: /*2131034138*/
                        addLogInfo("<font color = #d50000>Failed to authenticate, username or password expired");
                        hLogStatus.updateStateString(hLogStatus.VPN_AUTH_FAILED, resString(R.string.state_auth_failed));
                        if (this.current_profile != null) {
                            this.current_profile.get_name();
                            break;
                        }
                        break;
                    case R.string.state_connected /*2131034168*/:
                        hLogStatus.updateStateString(hLogStatus.VPN_CONNECTED, resString(R.string.state_connected));
                        addLogInfo("<font color='#FFA500'><strong>" + "Git Fable VPN ready to use!" + "</strong></font>");
                        addLogInfo("<font color='#006633'><strong>" + "Internet Access Available!" + "</strong>");
                        if (this.current_profile != null) {
                            this.current_profile.reset_proxy_context();
                            break;
                        }
                        break;
                    case R.string.state_stopping: /*2131034174*/
                        if (this.cpu_usage != null) {
                            this.cpu_usage.stop();
                        }
                        startService(new Intent(OpenVPNService.this, InjectorService.class).setAction(InjectorService.STOP_SERVICE));
                        break;
                    case R.string.state_disconnected: /*2131034189*/
                        if (lastev != null) {
                            if ((lastev.flags & MSG_EVENT) != 0) {
                                evm.priority = GCI_REQ_ESTABLISH;
                            }
                            if (!(this.current_profile == null || lastev.res_id == R.string.proxy_need_creds || lastev.res_id == R.string.dynamic_challenge)) {
                                this.current_profile.reset_proxy_context();
                                break;
                            }
                        }
                        break;
                    case R.string.dynamic_challenge /*2131034191*/:
                        if (this.current_profile != null) {
                            ClientAPI_DynamicChallenge dcsrc = new ClientAPI_DynamicChallenge();
                            if (ClientAPI_OpenVPNClient.parse_dynamic_challenge(evm.info, dcsrc)) {
                                DynamicChallenge dc = new DynamicChallenge();
                                dc.expires = SystemClock.elapsedRealtime() + 60000;
                                dc.cookie = evm.info;
                                dc.challenge.challenge = dcsrc.getChallenge();
                                dc.challenge.echo = dcsrc.getEcho();
                                dc.challenge.response_required = dcsrc.getResponseRequired();
                                this.current_profile.dynamic_challenge = dc;
                                evm.info = "";
                                break;
                            }
                        }
                        break;
                    case R.string.pem_password_fail /*2131034270*/:
                        evm.info = "";
                        if (this.current_profile != null) {
                            this.current_profile.get_name();
                            break;
                        }
                        break;
                    case R.string.state_pause:
                        paused = true;
                        break;
                }
                if (evm.res_id == R.string.epki_invalid_alias && this.profile_list != null) {
                    this.profile_list.invalidate_epki_alias(evm.info);
                }
                if (this.enable_notifications) {
                    if (evm.priority == MSG_LOG) {
                        Toast.makeText(this, evm.res_id, Toast.LENGTH_SHORT).show();
                    } else if (evm.priority == EV_PRIO_HIGH) {
                        Toast.makeText(this, evm.res_id, Toast.LENGTH_LONG).show();
                    }
                }
                if (evm.res_id == R.string.state_connected && (lastev == null || lastev.res_id != R.string.state_connected)) {
                    evm.transition = EventMsg.Transition.TO_CONNECTED;
                } else if (!(evm.res_id == R.string.state_connected || lastev == null || lastev.res_id != R.string.state_connected)) {
                    evm.transition = EventMsg.Transition.TO_DISCONNECTED;
                }
                if ((evm.flags & 4) != 0) {
                    this.last_event_prof_manage = evm;
                } else if (evm.priority >= MSG_LOG) {
                    this.last_event = evm;
                }
                String msg_str = null;
                if (evm.res_id != R.string.ui_reset) {
                    msg_str = evm.toString();
                }
                if (msg_str != null) {
                    Log.i(TAG, msg_str);
                }
                if (msg_str != null) {
                    addLogInfo(msg_str);
                }
                if (evm.res_id == R.string.state_stopping) {
                    Object[] objArr = new Object[MSG_EVENT];
                    objArr[GCI_REQ_ESTABLISH] = Long.valueOf(get_tunnel_bytes_per_cpu_second());
                }
                Iterator it = this.clients.iterator();
                while (it.hasNext()) {
                    EventReceiver cli = (EventReceiver) it.next();
                    if ((evm.flags & 16) == 0 || cli != evm.sender) {
                        cli.event(evm);
                    }
                }
                if (evm.res_id == R.string.state_auth){
                    hLogStatus.updateStateString(hLogStatus.VPN_AUTHENTICATING, resString(R.string.state_auth));
                    addLogInfo(resString(R.string.state_auth));
                }
                if (evm.res_id == R.string.state_add_routes){
                    hLogStatus.updateStateString(hLogStatus.VPN_ADD_ROUTES, resString(R.string.state_add_routes));
                    addLogInfo(resString(R.string.state_add_routes));
                }
                if (evm.res_id == R.string.state_assign_ip){
                    hLogStatus.updateStateString(hLogStatus.VPN_ASSIGN_IP, resString(R.string.state_assign_ip));
                    addLogInfo(resString(R.string.state_assign_ip));
                }
                if (evm.res_id == R.string.state_get_config){
                    hLogStatus.updateStateString(hLogStatus.VPN_GET_CONFIG, resString(R.string.state_get_config));
                    addLogInfo(resString(R.string.state_get_config));
                }
                if (evm.res_id == R.string.state_reconnecting){
                    hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, resString(R.string.state_reconnecting));
                    addLogInfo(resString(R.string.state_reconnecting));
                }
                if (evm.res_id == R.string.state_nonetwork){
                    hLogStatus.updateStateString(hLogStatus.VPN_NO_NETWORK, resString(R.string.state_nonetwork));
                    addLogInfo(resString(R.string.state_nonetwork));
                }
                if (evm.res_id == R.string.state_wait){
                    hLogStatus.updateStateString(hLogStatus.VPN_WAITING, resString(R.string.state_wait));
                    addLogInfo(resString(R.string.state_wait));
                }
                break;
            case MSG_LOG /*2*/:
                LogMsg lm = (LogMsg)msg.obj;
                String str = TAG;
                Object[] objArr2 = new Object[MSG_EVENT];
                objArr2[GCI_REQ_ESTABLISH] = lm.line;
                Log.i(str, String.format("LOG: %s", objArr2));
                if(!lm.line.contains("EVENT"))log_message(lm);
                break;
            default:
                Log.d(TAG, "SERV: unhandled message");
                break;
        }
        return true;
    }

    public static void log_message(String line) {
        LogMsg lm = new LogMsg();
        lm.line = line + "\n";
        log_message(lm);
    }

    private static void log_message(LogMsg lm) {
        String x1 = "Server Name";
        String x2 = "200 OK";
        String x3 = "dhcp-option";
        String x4 = "TUN write";
        String x5 = "Connecting to [";
        if (lm.line.contains("\n")) {
            String[] split = lm.line.split("\n");
            for (String str: split) {
                if(str.contains(x1)){
                    addLogInfo(str);
                }else if(str.contains(x2)) {
                    addLogInfo(str);
                }else if(str.contains(x3)) {
                    addLogInfo(str);
                }else if(str.contains(x4)){
                    addLogInfo(str);
                }else if(str.contains(x5)){
                    addLogInfo(str);
                }
            }
        }
    }

    public static void addLogInfo(String _msg){
        String hst = mConfig.getSecureString(SERVER_KEY);
        String prx = mConfig.getSecureString(PROXY_IP_KEY);
        String msg = _msg.trim().replace(hst,"Jay-Ar Lee Espiritu's Server").replace(prx,"Jay-Ar Lee Espiritu's Server");
        if(!msg.contains("EVENT"))hLogStatus.logInfo(msg);
    }

    public boolean socket_protect(int socket) {
        boolean status = protect(socket);
        String str = TAG;
        Object[] objArr = new Object[MSG_LOG];
        objArr[GCI_REQ_ESTABLISH] = Integer.valueOf(socket);
        objArr[MSG_EVENT] = Boolean.valueOf(status);
        Log.d(str, String.format("SOCKET PROTECT: fd=%d protected status=%b", objArr));
        return status;
    }

    public boolean pause_on_connection_timeout() {
        boolean ret = false;
        if (!(this.mConnectivityReceiver == null || !this.mConnectivityReceiver.screen_on_defined || this.mConnectivityReceiver.screen_on)) {
            ret = true;
        }
        String str = TAG;
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = Boolean.valueOf(ret);
        Log.d(str, String.format("pause_on_connection_timeout %b", objArr));
        return ret;
    }

    public net.openvpn.openvpn.OpenVPNClientThread.TunBuilder tun_builder_new() {
        return new TunBuilder();
    }

    public void event(ClientAPI_Event event) {
        EventMsg evm = new EventMsg();
        if (event.getError()) {
            evm.flags |= MSG_EVENT;
        }
        evm.name = event.getName();
        evm.info = event.getInfo();
        EventInfo evi = (EventInfo) this.event_info.get(evm.name);
        if (evi != null) {
            evm.progress = evi.progress;
            evm.priority = evi.priority;
            evm.res_id = evi.res_id;
            evm.icon_res_id = evi.icon_res_id;
            evm.flags |= evi.flags;
            if (evi.res_id == R.string.state_connected && this.mThread != null) {
                evm.conn_info = this.mThread.connection_info();
            }
        } else {
            evm.res_id = R.string.state_disconnected;
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_EVENT, evm));
    }

    public void log(ClientAPI_LogInfo loginfo) {
        LogMsg lm = new LogMsg();
        lm.line = loginfo.getText();
        this.mHandler.sendMessage(this.mHandler.obtainMessage(MSG_LOG, lm));
    }

    private String cert_format_pem(X509Certificate cert) throws CertificateEncodingException {
        Object[] objArr = new Object[MSG_EVENT];
        objArr[GCI_REQ_ESTABLISH] = Base64.encodeToString(cert.getEncoded(), GCI_REQ_ESTABLISH);
        return String.format("-----BEGIN CERTIFICATE-----%n%s-----END CERTIFICATE-----%n", objArr);
    }

    public void external_pki_cert_request(ClientAPI_ExternalPKICertRequest req) {
        try {
            X509Certificate[] chain = KeyChain.getCertificateChain(this, req.getAlias());
            if (chain == null) {
                req.setError(true);
                req.setInvalidAlias(true);
            } else if (chain.length >= MSG_EVENT) {
                req.setCert(cert_format_pem(chain[GCI_REQ_ESTABLISH]));
                if (chain.length >= MSG_LOG) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = MSG_EVENT; i < chain.length; i += MSG_EVENT) {
                        builder.append(cert_format_pem(chain[i]));
                    }
                    req.setSupportingChain(builder.toString());
                }
            } else {
                req.setError(true);
                req.setInvalidAlias(true);
                req.setErrorText(resString(R.string.epki_missing_cert));
            }
        } catch (Exception e) {
            Log.e(TAG, "EPKI error in external_pki_cert_request", e);
            req.setError(true);
            req.setInvalidAlias(true);
            req.setErrorText(e.toString());
        }
    }

    public void external_pki_sign_request(ClientAPI_ExternalPKISignRequest req) {
        try {
            String errfmt = "EPKI error in external_pki_sign_request: %s";
            byte[] data_bytes = Base64.decode(req.getData(), GCI_REQ_ESTABLISH);
            byte[] sig_bytes = null;
            PrivateKey pk;
            if (this.jellyBeanHack == null) {
                Log.d(TAG, "EPKI: normal mode");
                pk = KeyChain.getPrivateKey(this, req.getAlias());
                if (pk != null) {
                    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
                    cipher.init(MSG_EVENT, pk);
                    sig_bytes = cipher.doFinal(data_bytes);
                } else {
                    req.setError(true);
                    req.setInvalidAlias(true);
                }
            } else {
                Log.d(TAG, "EPKI: Jelly bean mode");
                if (this.jellyBeanHack.enabled()) {
                    pk = this.jellyBeanHack.getPrivateKey(this, req.getAlias());
                    if (pk != null) {
                        sig_bytes = this.jellyBeanHack.rsaSign(pk, data_bytes);
                    } else {
                        req.setError(true);
                        req.setInvalidAlias(true);
                    }
                } else {
                    String err = "Android OpenSSL not accessible";
                    String str = TAG;
                    Object[] objArr = new Object[MSG_EVENT];
                    objArr[GCI_REQ_ESTABLISH] = err;
                    Log.e(str, String.format(errfmt, objArr));
                    req.setError(true);
                    req.setInvalidAlias(true);
                    req.setErrorText(err);
                    return;
                }
            }
            if (sig_bytes != null) {
                req.setSig(Base64.encodeToString(sig_bytes, MSG_LOG));
            }
        } catch (Exception e) {
            Log.e(TAG, "EPKI error in external_pki_sign_request", e);
            req.setError(true);
            req.setInvalidAlias(true);
            req.setErrorText(e.toString());
        }
    }

    public void done(ClientAPI_Status status) {
        boolean err = status.getError();
        String msg = status.getMessage();
        String str = TAG;
        Object[] objArr = new Object[MSG_LOG];
        objArr[GCI_REQ_ESTABLISH] = Boolean.valueOf(err);
        objArr[MSG_EVENT] = msg;
        Log.d(str, String.format("EXIT: connect() exited, err=%b, msg='%s'", objArr));
        log_stats();
        if (err) {
            if (msg == null || !msg.equals("CORE_THREAD_ABANDONED")) {
                String label = status.getStatus();
                if (label.length() == 0) {
                    label = "CORE_THREAD_ERROR";
                }
                gen_event(MSG_EVENT, label, msg);
            } else {
                gen_event(MSG_EVENT, "CORE_THREAD_ABANDONED", null);
            }
        }
        gen_event(GCI_REQ_ESTABLISH, "CORE_THREAD_INACTIVE", null);
        this.active = false;
    }

    public void set_autostart_profile_name(String profile_name) {
        if (profile_name != null) {
            this.prefs.set_string("autostart_profile_name", profile_name);
        } else {
            this.prefs.delete_key("autostart_profile_name");
        }
    }

    public static String[] stat_names() {
        int size = ClientAPI_OpenVPNClient.stats_n();
        String[] ret = new String[size];
        for (int i = GCI_REQ_ESTABLISH; i < size; i += MSG_EVENT) {
            ret[i] = ClientAPI_OpenVPNClient.stats_name(i);
        }
        return ret;
    }

    public ClientAPI_LLVector stat_values_full() {
        if (this.mThread != null) {
            return this.mThread.stats_bundle();
        }
        return null;
    }

    protected static Date get_app_expire() {
        int expire = ClientAPI_OpenVPNClient.app_expire();
        if (expire > 0) {
            return new Date(((long) expire) * 1000);
        }
        return null;
    }

    private void log_stats() {
        if (this.active) {
            String[] sn = stat_names();
            ClientAPI_LLVector sv = stat_values_full();
            if (sv != null) {
                for (int i = GCI_REQ_ESTABLISH; i < sn.length; i += MSG_EVENT) {
                    String name = sn[i];
                    long value = sv.get(i);
                    if (value > 0) {
                        String str = TAG;
                        Object[] objArr = new Object[MSG_LOG];
                        objArr[GCI_REQ_ESTABLISH] = name;
                        objArr[MSG_EVENT] = Long.valueOf(value);
                        Log.i(str, String.format("STAT %s=%s", objArr));
                    }
                }
            }
        }
    }

    public String read_file(String location, String filename) throws IOException {
        if (location.equals("bundled")) {
            return FileUtils.readAsset(this, filename);
        }
        if (location.equals("imported")) {
            return FileUtils.readFileAppPrivate(this, filename);
        }
        throw new InternalError();
    }

    private String resString(int res_id) {
        return getResources().getString(res_id);
    }

    private void populate_event_info_map() {
        this.event_info = new HashMap();
        this.event_info.put("RECONNECTING", new OpenVPNService.EventInfo(R.string.state_reconnecting, R.drawable.ic_cloud_off, 20, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("RESOLVE", new OpenVPNService.EventInfo(R.string.state_resolve, R.drawable.ic_cloud_off, 30, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("WAIT_PROXY", new OpenVPNService.EventInfo(R.string.wait_proxy, R.drawable.ic_cloud_off, 40, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("WAIT", new OpenVPNService.EventInfo(R.string.state_wait, R.drawable.ic_cloud_off, 50, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("CONNECTING", new OpenVPNService.EventInfo(R.string.state_connecting, R.drawable.ic_cloud_off, 60, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("GET_CONFIG", new OpenVPNService.EventInfo(R.string.state_get_config, R.drawable.ic_cloud_off, 70, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("ASSIGN_IP", new OpenVPNService.EventInfo(R.string.state_assign_ip, R.drawable.ic_cloud_off, 80, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("ADD_ROUTES", new OpenVPNService.EventInfo(R.string.state_add_routes, R.drawable.ic_cloud_off, 90, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("CONNECTED", new OpenVPNService.EventInfo(R.string.state_connected, R.drawable.ic_cloud_on, 100, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("DISCONNECTED", new OpenVPNService.EventInfo(R.string.state_disconnected, R.drawable.ic_cloud_off, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("AUTH_FAILED", new OpenVPNService.EventInfo(R.string.state_auth_failed, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PEM_PASSWORD_FAIL", new OpenVPNService.EventInfo(R.string.pem_password_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CERT_VERIFY_FAIL", new OpenVPNService.EventInfo(R.string.cert_verify_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("TLS_VERSION_MIN", new OpenVPNService.EventInfo(R.string.tls_version_min, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("DYNAMIC_CHALLENGE", new OpenVPNService.EventInfo(R.string.dynamic_challenge, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("TUN_SETUP_FAILED", new OpenVPNService.EventInfo(R.string.tun_setup_failed, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("TUN_IFACE_CREATE", new OpenVPNService.EventInfo(R.string.tun_iface_create, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("TAP_NOT_SUPPORTED", new OpenVPNService.EventInfo(R.string.tap_not_supported, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROFILE_NOT_FOUND", new OpenVPNService.EventInfo(R.string.profile_not_found, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CONFIG_FILE_PARSE_ERROR", new OpenVPNService.EventInfo(R.string.config_file_parse_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("NEED_CREDS_ERROR", new OpenVPNService.EventInfo(R.string.need_creds_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CREDS_ERROR", new OpenVPNService.EventInfo(R.string.creds_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CONNECTION_TIMEOUT", new OpenVPNService.EventInfo(R.string.state_connection_timeout, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("INACTIVE_TIMEOUT", new OpenVPNService.EventInfo(R.string.inactive_timeout, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("INFO", new OpenVPNService.EventInfo(R.string.info_msg, R.drawable.ic_back, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("WARN", new OpenVPNService.EventInfo(R.string.warn_msg, R.drawable.ic_back, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROXY_NEED_CREDS", new OpenVPNService.EventInfo(R.string.proxy_need_creds, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROXY_ERROR", new OpenVPNService.EventInfo(R.string.proxy_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("PROXY_CONTEXT_EXPIRED", new OpenVPNService.EventInfo(R.string.proxy_context_expired, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("EPKI_ERROR", new OpenVPNService.EventInfo(R.string.epki_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("EPKI_INVALID_ALIAS", new OpenVPNService.EventInfo(R.string.epki_invalid_alias, R.drawable.ic_error, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("PAUSE", new OpenVPNService.EventInfo(R.string.state_pause, R.drawable.ic_cloud_off, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("RESUME", new OpenVPNService.EventInfo(R.string.state_resume, R.drawable.ic_recon, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_ACTIVE", new OpenVPNService.EventInfo(R.string.state_starting, R.drawable.ic_cloud_off, 10, MSG_EVENT, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_INACTIVE", new OpenVPNService.EventInfo(R.string.state_stopping, -1, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_ERROR", new OpenVPNService.EventInfo(R.string.core_thread_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CORE_THREAD_ABANDONED", new OpenVPNService.EventInfo(R.string.core_thread_abandoned, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CLIENT_HALT", new OpenVPNService.EventInfo(R.string.client_halt, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, GCI_REQ_ESTABLISH));
        this.event_info.put("CLIENT_RESTART", new OpenVPNService.EventInfo(R.string.client_restart, R.drawable.ic_cloud_off, GCI_REQ_ESTABLISH, MSG_LOG, GCI_REQ_ESTABLISH));
        this.event_info.put("PROFILE_PARSE_ERROR", new OpenVPNService.EventInfo(R.string.profile_parse_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_CONFLICT", new OpenVPNService.EventInfo(R.string.profile_conflict, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_WRITE_ERROR", new OpenVPNService.EventInfo(R.string.profile_write_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_FILENAME_ERROR", new OpenVPNService.EventInfo(R.string.profile_filename_error, R.drawable.ic_error, GCI_REQ_ESTABLISH, EV_PRIO_HIGH, 4));
        this.event_info.put("PROFILE_MERGE_EXCEPTION", new OpenVPNService.EventInfo(R.string.profile_merge_exception, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_OVPN_EXT_FAIL", new OpenVPNService.EventInfo(R.string.profile_merge_ovpn_ext_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_OVPN_FILE_FAIL", new OpenVPNService.EventInfo(R.string.profile_merge_ovpn_file_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_REF_FAIL", new OpenVPNService.EventInfo(R.string.profile_merge_ref_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("PROFILE_MERGE_MULTIPLE_REF_FAIL", new OpenVPNService.EventInfo(R.string.profile_merge_multiple_ref_fail, R.drawable.ic_error, GCI_REQ_ESTABLISH, MSG_LOG, 4));
        this.event_info.put("UI_RESET", new OpenVPNService.EventInfo(R.string.ui_reset, R.drawable.ic_back, GCI_REQ_ESTABLISH, GCI_REQ_ESTABLISH, 8));
    }
}
