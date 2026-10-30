package jayar.lee.espiritu.logger;

import android.content.Intent;
import android.os.Build;

import com.jepongdevxyz.fablevpn.R;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.Vector;

public class hLogStatus
{
    private static final LinkedList<LogItem> logbuffer;
    private static Vector<LogListener> logListener;
    private static Vector<StateListener> stateListener;
    private static ConnectionStatus mLastLevel = ConnectionStatus.LEVEL_NOTCONNECTED;
    private static String mLaststatemsg = "";
    private static String mLaststate = "Disconnected";
    private static int mLastStateresid = R.string.state_disconnected;
    private static int mProgress = 0;
    static final int MAXLOGENTRIES = 1000;
    public static TrafficHistory trafficHistory;
    private static Vector<ByteCountListener> byteCountListener;

    public static boolean isTunnelActive() {
        return mLastLevel != ConnectionStatus.LEVEL_AUTH_FAILED && !(mLastLevel == ConnectionStatus.LEVEL_NOTCONNECTED);
    }

    public static String getLastState() {
        return mLaststate;
    }


    public static enum LogLevel {

        INFO(2),
        ERROR(-2),
        WARNING(1),
        VERBOSE(3),
        DEBUG(4);

        protected int mValue;

        LogLevel(int value) {
            mValue = value;
        }

        public int getInt() {
            return mValue;
        }

        public static LogLevel getEnumByValue(int value) {
            switch (value) {
                case 2:
                    return INFO;
                case -2:
                    return ERROR;
                case 1:
                    return WARNING;
                case 3:
                    return VERBOSE;
                case 4:
                    return DEBUG;

                default:
                    return null;
            }
        }
    }

    // keytool -printcert -jarfile de.blinkt.openvpn_85.apk
    // tudo ok, certificado da Playstore
    static final byte[] oficialkey = {93, -72, 88, 103, -128, 115, -1, -47, 120, 113, 98, -56, 12, -56, 52, -62, 95, -2, -114, 95};
    // já atualizado, slipk certificado
    static final byte[] oficialdebugkey = {-41, 73, 58, 102, -81, -27, -120, 45, -56, -3, 53, -49, 119, -97, -20, -80, 65, 68, -72, -22};

    static {
        logbuffer = new LinkedList<>();
        logListener = new Vector<>();
        stateListener = new Vector<>();
        byteCountListener = new Vector<>();
        trafficHistory = new TrafficHistory();
        logInformation();
    }

    public synchronized static String CopyLogs() {
        return logbuffer.toString().replace("<b>","").replace("</b>","");
    }

    public synchronized static void clearLog() {
        logbuffer.clear();
        for (LogListener li : logListener) {
            li.onClear();
        }
    }

    private static void logInformation() {
        logInfo(R.string.mobile_info, Build.MODEL, Build.BOARD, Build.BRAND, Build.VERSION.SDK_INT,Build.VERSION.RELEASE);
        logInfo(R.string.app_mobile_info, "", "");
    }

    public synchronized static LogItem[] getlogbuffer() {
        // The stoned way of java to return an array from a vector
        // brought to you by eclipse auto complete
        return logbuffer.toArray(new LogItem[logbuffer.size()]);
    }

    public static void setTrafficHistory(TrafficHistory _trafficHistory) {
        trafficHistory = _trafficHistory;
    }

    public interface ByteCountListener {
        void updateByteCount(long in, long out, long diffIn, long diffOut);
    }

    public static synchronized void updateByteCount(long in, long out) {
        TrafficHistory.LastDiff diff = trafficHistory.add(in, out);
        for (ByteCountListener bcl : byteCountListener) {
            bcl.updateByteCount(in, out, diff.getDiffIn(), diff.getDiffOut());
        }
    }

    public synchronized static void addByteCountListener(ByteCountListener bcl) {
        TrafficHistory.LastDiff diff = trafficHistory.getLastDiff(null);
        bcl.updateByteCount(diff.getIn(), diff.getOut(), diff.getDiffIn(),diff.getDiffOut());
        byteCountListener.add(bcl);
    }

    public synchronized static void removeByteCountListener(ByteCountListener bcl) {
        byteCountListener.remove(bcl);
    }

    public interface LogListener {
        void newLog(LogItem logItem);
        void onClear();
    }

    public interface StateListener {
        void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level,int progress);
    }

    public synchronized static void addLogListener(LogListener ll) {
        if (!logListener.contains(ll)) {
            logListener.add(ll);
        }
    }

    public synchronized static void removeLogListener(LogListener ll) {
        if (logListener.contains(ll)) {
            logListener.remove(ll);
        }
    }

    public synchronized static void addStateListener(StateListener sl) {
        if (!stateListener.contains(sl)) {
            stateListener.add(sl);
            if (mLaststate != null)
                sl.updateState(mLaststate, mLaststatemsg, mLastStateresid, mLastLevel, mProgress);
        }
    }

    public synchronized static void removeStateListener(StateListener sl) {
        if (stateListener.contains(sl)) {
            stateListener.remove(sl);
        }
    }
    /**
     * State
     */

    public static final String
            VPN_AUTH_FAILED = "Authentication Failed",
            VPN_CONNECTING = "Connecting",
            VPN_WAITING = "Waiting for OVPN Server Response",
            VPN_NO_NETWORK = "Waiting for network",
            VPN_AUTHENTICATING = "Authenticating",
            VPN_CONNECTED = "Connected",
            VPN_DISCONNECTED = "Disconnected",
            VPN_RECONNECTING = "Reconnecting",
            VPN_GET_CONFIG = "Getting Client Configuration",
            VPN_ASSIGN_IP = "Assigning IP Addresses",
            VPN_ADD_ROUTES = "Adding Routes",
            VPN_RESOLVE = "Resolving Host Names",
            VPN_AUTH_PENDING = "Waiting for Autentication",
            VPN_PAUSE = "Pausing (waiting for network)",
            VPN_RESUME = "Resuming after pause",
            VPN_STARTING = "Getting Started",
            VPN_STOPPING = "Stopping";

    public static int getLocalizedState(String state) {
        switch (state) {
            case VPN_STARTING:
                return R.string.state_starting;
            case VPN_STOPPING:
                return R.string.state_stopping;
            case VPN_PAUSE:
                return R.string.state_pause;
            case VPN_RESUME:
                return R.string.state_resume;
            case VPN_CONNECTING:
                return R.string.state_connecting;
            case VPN_WAITING:
                return R.string.state_wait;
            case VPN_NO_NETWORK:
                return R.string.state_nonetwork;
            case VPN_AUTHENTICATING:
                return R.string.state_auth;
            case VPN_GET_CONFIG:
                return R.string.state_get_config;
            case VPN_ASSIGN_IP:
                return R.string.state_assign_ip;
            case VPN_ADD_ROUTES:
                return R.string.state_add_routes;
            case VPN_CONNECTED:
                return R.string.state_connected;
            case VPN_DISCONNECTED:
                return R.string.state_disconnected;
            case VPN_RECONNECTING:
                return R.string.state_reconnecting;
            case VPN_RESOLVE:
                return R.string.state_resolve;
            case VPN_AUTH_PENDING:
                return R.string.state_auth_pending;
            case VPN_AUTH_FAILED:
                return R.string.state_auth_failed;
            default:
                return R.string.state_unknown;
        }
    }

    public static int getProgressState(String state) {
        switch (state) {
            case VPN_CONNECTING:
                return 40;
            case VPN_WAITING:
                return 20;
            case VPN_NO_NETWORK:
                return 30;
            case VPN_AUTHENTICATING:
                return 85;
            case VPN_GET_CONFIG:
                return 70;
            case VPN_ASSIGN_IP:
                return 75;
            case VPN_ADD_ROUTES:
                return 80;
            case VPN_CONNECTED:
                return 100;
            case VPN_DISCONNECTED:
                return 1;
            case VPN_RECONNECTING:
                return 50;
            case VPN_STARTING:
                return 10;
            case VPN_STOPPING:
                return 1;
            case VPN_RESOLVE:
                return 60;
            case VPN_PAUSE:
                return 65;
            case VPN_RESUME:
                return 80;
            case VPN_AUTH_PENDING:
                return 70;
            case VPN_AUTH_FAILED:
                return 1;
            default:
                return 1;
        }
    }

    private static ConnectionStatus getLevel(String state) {
        String[] noreplyet = {VPN_STARTING, VPN_CONNECTING, VPN_WAITING ,VPN_NO_NETWORK, VPN_RECONNECTING, VPN_RESOLVE,VPN_PAUSE};
        String[] reply = {VPN_AUTHENTICATING, VPN_GET_CONFIG, VPN_ASSIGN_IP, VPN_ADD_ROUTES, VPN_AUTH_PENDING,VPN_RESUME};
        String[] connected = {VPN_CONNECTED};
        String[] notconnected = {VPN_DISCONNECTED,VPN_AUTH_FAILED,VPN_STOPPING};

        for (String x : noreplyet)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET;

        for (String x : reply)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED;

        for (String x : connected)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_CONNECTED;

        for (String x : notconnected)
            if (state.equals(x))
                return ConnectionStatus.LEVEL_NOTCONNECTED;

        return ConnectionStatus.UNKNOWN_LEVEL;
    }

    public static void updateStateString(final String state, final String msg) {
        int rid = getLocalizedState(state);
        ConnectionStatus level = getLevel(state);
        int prog = getProgressState(state);
        updateStateString(state, msg, rid, level, prog);
    }

    public synchronized static void updateStateString(String state, String msg, int resid, ConnectionStatus level, int progress) {
        // Workound for OpenVPN doing AUTH and wait and being connected
        // Simply ignore these state
        if (mLastLevel == ConnectionStatus.LEVEL_CONNECTED &&
                (state.equals(VPN_AUTHENTICATING))) {
            newLogItem(new LogItem((LogLevel.DEBUG), String.format("Ignoring 7Tunnel Status in CONNECTED state (%s->%s): %s", state, level.toString(), msg)));
            return;
        }

        mLaststate = state;
        mLaststatemsg = msg;
        mLastStateresid = resid;
        mLastLevel = level;
        mProgress = progress;

        for (StateListener sl : stateListener) {
            sl.updateState(state, msg, resid, level, progress);
        }

        //newLogItem(new LogItem((LogLevel.DEBUG), String.format("SocksHttp Novo Status (%s->%s): %s",state,level.toString(),msg)));
    }


    /**
     * NewLog
     */

    static void newLogItem(LogItem logItem) {
        newLogItem(logItem, false);
    }

    synchronized static void newLogItem(LogItem logItem, boolean cachedLine) {
        if (cachedLine) {
            logbuffer.addFirst(logItem);
        } else {
            logbuffer.addLast(logItem);
        }

        if (logbuffer.size() > MAXLOGENTRIES + MAXLOGENTRIES / 2) {
            while (logbuffer.size() > MAXLOGENTRIES)
                logbuffer.removeFirst();
        }

        for (LogListener ll : logListener) {
            ll.newLog(logItem);
        }
    }


    /**
     * Logger static methods
     */

    public static void logException(String context, Exception e) {
        logException(LogLevel.ERROR, context, e);
    }

    public static void logException(LogLevel ll, String context, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));

        LogItem li;

        if (context != null)
            li = new LogItem(ll, String.format("%s: %s, %s", context, e.getMessage(), sw.toString()));
        else
            li = new LogItem(ll, String.format("Error: %s, %s", e.getMessage(), sw.toString()));

        newLogItem(li);
    }

    public static void logException(Exception e) {
        logException(LogLevel.ERROR, null, e);
    }

    public static void logInfo(final String message) {
        newLogItem(new LogItem(LogLevel.INFO, message));
    }

    public static void logDebug(String message) {
        newLogItem(new LogItem(LogLevel.DEBUG, message));
    }

    public static void logInfo(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.INFO, resourceId, args));
    }

    public static void logDebug(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.DEBUG, resourceId, args));
    }

    public static void logError(String msg) {
        newLogItem(new LogItem(LogLevel.ERROR, msg));
    }

    public static void logWarning(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.WARNING, resourceId, args));
    }

    public static void logWarning(String msg) {
        newLogItem(new LogItem(LogLevel.WARNING, msg));
    }

    public static void logError(int resourceId) {
        newLogItem(new LogItem(LogLevel.ERROR, resourceId));
    }

    public static void logError(int resourceId, Object... args) {
        newLogItem(new LogItem(LogLevel.ERROR, resourceId, args));
    }

}

