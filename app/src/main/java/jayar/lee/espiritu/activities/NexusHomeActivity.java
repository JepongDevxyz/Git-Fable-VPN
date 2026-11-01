package jayar.lee.espiritu.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.jepongdevxyz.fablevpn.BuildConfig;
import com.jepongdevxyz.fablevpn.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.ArrayList;

import jayar.lee.espiritu.config.ConfigDataBase;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.config.SettingsConstants;
import jayar.lee.espiritu.core.ConfigParser;
import jayar.lee.espiritu.core.VpnProfile;
import jayar.lee.espiritu.logger.ConnectionStatus;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.protocols.ConnectionDisplay;
import jayar.lee.espiritu.protocols.FeedUpdater;
import jayar.lee.espiritu.protocols.LegacyEnginePlan;
import jayar.lee.espiritu.protocols.Protocol;
import jayar.lee.espiritu.protocols.ServerProfile;
import jayar.lee.espiritu.protocols.TunnelSettings;
import jayar.lee.espiritu.protocols.WireGuardConfParser;
import jayar.lee.espiritu.protocols.XrayConfigGenerator;
import jayar.lee.espiritu.service.CoreTunnelStatus;
import jayar.lee.espiritu.service.InjectorService;
import jayar.lee.espiritu.service.OpenVPNService;
import jayar.lee.espiritu.service.WgVpnService;
import jayar.lee.espiritu.service.XrayVpnService;
import jayar.lee.espiritu.utils.FileUtils;
import jayar.lee.espiritu.utils.util;
import jayar.lee.espiritu.view.GraphHelper;
import jayar.lee.espiritu.view.StatisticGraphData;
import net.openvpn.openvpn.PrefUtil;

/**
 * Nexus-style Home — THE connect screen (her order, 2026-11-01, with
 * video proof: "dapat di na babalik sa lumang home screen — may bago
 * na nga e"). Tapping the ring connects/disconnects IN PLACE here; this
 * screen never opens OpenVPNClient. It extends OpenVPNClientBase (the
 * same base the legacy client uses) and ports the minimal connect
 * machinery from OpenVPNClient, minus its UI.
 *
 * HOW A SELECTION REACHES THE ENGINE (investigated 2026-11-01):
 *  - The legacy screen's spinners only persist SERVER_POSITION /
 *    NETWORK_POSITION; reLoad_Configs() translates the chosen
 *    ServerData/NetworksData JSON rows into ConfigUtil (the singleton
 *    InjectorService + OpenVPNService read) via its public setters.
 *    Those setters ARE the sanctioned custom-config entry point, so a
 *    ProfileStore profile is applied through them — LegacyEnginePlan
 *    (pure, unit-tested) decides per protocol what can be applied:
 *    SSH injector plans and OpenVPN cert/remote/credentials apply;
 *    UDP Custom applies only with a full legacy UDP JSON; SlowDNS and
 *    the sing-box / system-VPN protocols cannot be applied by this
 *    engine and fall back to (1) a name match in the built-in server
 *    list, else (2) the last-used legacy selection, exactly what the
 *    old screen would have connected. Secure values are FileUtils._e2
 *    encoded and the .ovpn cert hideJson-encoded, exactly like the
 *    old screen encodes them (ConfigUtil decodes on read).
 *  - Credentials come from the profile itself when present, else the
 *    SAME private prefs keys (_screenUsername_key/_screenPassword_key)
 *    the Account screen writes and the old connect flow reads.
 *
 * UI FEEDS (nothing display-only):
 *  - Ring: hLogStatus state listener -> TAP TO CONNECT / CONNECTING… /
 *    CONNECTED, background cc_ring / cc_ring_connected.
 *  - Server IP: selected profile host (or legacy server host/name).
 *  - Location: selected profile name; legacy server name otherwise.
 *  - Ping: best-effort TCP connect timing to the active host:port,
 *    measured on connect; "—" when it cannot be measured.
 *  - Uptime: DataTransferStats elapsed while connected (1 s ticker).
 *  - Up/Down speeds: hLogStatus byte-count diffs via ConnectionDisplay.
 *  - Chart: GraphHelper bound to cc_chart, fed by hLogStatus traffic
 *    history exactly like the legacy client's chart.
 *  - Statistics bottom-nav -> NexusStatisticsActivity (own screen).
 *  - NO Server Generator here (admin-only separate app, her order).
 *
 * PHASE 2 (native cores): Xray-family profiles (VLESS/VMess/Trojan/
 * Shadowsocks/Hysteria2) no longer fall back to the legacy engine —
 * the ring generates an Xray config (XrayConfigGenerator) and starts
 * XrayVpnService (bundled libv2ray core); WireGuard profiles start
 * WgVpnService (official tunnel library). Core tunnels report through
 * CoreTunnelStatus; the ring, uptime, speeds and chart read it exactly
 * like the legacy feeds. SINGLE ACTIVE TUNNEL RULE: the ring stops
 * whichever tunnel is up (legacy or core) instead of starting a second
 * one, and the core start paths refuse while the legacy engine is up.
 */
public class NexusHomeActivity extends OpenVPNClientBase
        implements hLogStatus.StateListener, hLogStatus.ByteCountListener, SettingsConstants {

    private static final int START_BIND_CALLED = 1;
    private static final int START_CORE_CALLED = 2;

    /** Core service intent waiting on the VpnService.prepare grant. */
    private Intent pendingCoreIntent;

    private FrameLayout ring;
    private TextView ringText;
    private TextView protocolChip;
    private TextView currentServer;
    private TextView ipLine, locationLine, pingLine, uptimeLine;
    private TextView upSpeed, downSpeed;
    private LineChart chart;
    private GraphHelper graph;

    private ConfigDataBase serverData, networksData;
    private PrefUtil prefs;
    private Handler handler = new Handler();
    /** Core status events arrive off the main thread: always hop.
     *  Declared after handler — a field initializer may not forward-
     *  reference it. */
    private final CoreTunnelStatus.Listener coreListener = () -> handler.post(this::onCoreStatusChanged);

    private volatile boolean connected;
    private volatile String activeHost = "";
    private volatile int activePort = 0;
    private volatile long pingMs = -1;
    private long lastIn = -1, lastOut = -1, lastByteAt = 0;

    private final Handler statsHandler = new Handler();
    private final Runnable statsTask = new Runnable() {
        @Override public void run() {
            tickStats();
            statsHandler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nexus_home);

        ring = findViewById(R.id.cc_connect_ring);
        ringText = findViewById(R.id.cc_connect_text);
        protocolChip = findViewById(R.id.cc_protocol_chip);
        currentServer = findViewById(R.id.cc_current_server);
        ipLine = findViewById(R.id.cc_ip_line);
        locationLine = findViewById(R.id.cc_location_line);
        pingLine = findViewById(R.id.cc_ping_line);
        uptimeLine = findViewById(R.id.cc_uptime_line);
        upSpeed = findViewById(R.id.cc_up_speed);
        downSpeed = findViewById(R.id.cc_down_speed);
        chart = findViewById(R.id.cc_chart);
        if (chart != null) {
            chart.setNoDataText("");
            graph = GraphHelper.getHelper().with(this)
                .color(getResources().getColor(R.color.cc_graph_down)).chart(chart);
        }

        prefs = new PrefUtil(PreferenceManager.getDefaultSharedPreferences(this));
        init_default_preferences(prefs);
        serverData = new ConfigDataBase(this, "ServerData");
        networksData = new ConfigDataBase(this, "NetworksData");
        loadDefaultConfig();
        loadDefaultProfiles();
        doBindService();

        findViewById(R.id.cc_home_account).setOnClickListener(v ->
            startActivity(new Intent(this, AccountActivity.class)));

        View.OnClickListener toSettings = v ->
            startActivity(new Intent(this, NexusSettingsActivity.class));
        protocolChip.setOnClickListener(toSettings);
        findViewById(R.id.cc_nav_settings).setOnClickListener(toSettings);

        findViewById(R.id.cc_nav_stats).setOnClickListener(v ->
            startActivity(new Intent(this, NexusStatisticsActivity.class)));

        View.OnClickListener toServers = v ->
            startActivity(new Intent(this, ServersActivity.class));
        findViewById(R.id.cc_change_location).setOnClickListener(toServers);
        findViewById(R.id.cc_nav_locations).setOnClickListener(toServers);

        ring.setOnClickListener(v -> onRingTap());
    }

    @Override protected void onResume() {
        super.onResume();
        hLogStatus.addStateListener(this);
        hLogStatus.addByteCountListener(this);
        CoreTunnelStatus.addListener(coreListener);
        refreshHeader();
        refreshInfoCard();
        // Returning to Home with a tunnel already up: the state listener
        // may not replay, so take the connected flag from the stats core.
        if (hLogStatus.isTunnelActive() && _upDateBytes.isConnected()) connected = true;
        if (!hLogStatus.isTunnelActive()) connected = false;
        renderState(hLogStatus.isTunnelActive() || CoreTunnelStatus.isActive(),
            connected || CoreTunnelStatus.isConnected());
        if (CoreTunnelStatus.isConnected()) {
            ServerProfile sel = ProfilePrefs.selected();
            if (sel != null) { activeHost = sel.host; activePort = sel.port; }
        }
        if (hLogStatus.isTunnelActive() || CoreTunnelStatus.isConnected()) {
            if (graph != null) graph.start();
            startStatsTicker();
        }
        maybeAutoUpdateFeed();
    }

    @Override protected void onPause() {
        super.onPause();
        CoreTunnelStatus.removeListener(coreListener);
        statsHandler.removeCallbacks(statsTask);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        statsHandler.removeCallbacks(statsTask);
        hLogStatus.removeStateListener(this);
        hLogStatus.removeByteCountListener(this);
        doUnbindService();
    }

    @Override public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    // ---------------- header / info card ----------------

    private void refreshHeader() {
        SharedPreferences sp = getSharedPreferences(NexusSettingsActivity.PREFS, MODE_PRIVATE);
        String proto = sp.getString(TunnelSettings.KEY_DEFAULT_PROTOCOL, "AUTO");
        protocolChip.setText("Protocol: " + prettyProtocol(proto));
        if (currentServer != null) {
            ServerProfile sel = ProfilePrefs.selected();
            currentServer.setText(sel == null
                ? "Current server: none selected"
                : "Current server: " + sel.displayName());
        }
    }

    /** Honest disconnected state: selected server or "No server selected",
     *  never stale placeholder junk. */
    private void refreshInfoCard() {
        ServerProfile sel = ProfilePrefs.selected();
        if (sel != null) {
            ipLine.setText("Server IP: " + (sel.host == null || sel.host.isEmpty() ? "—" : sel.host));
            locationLine.setText("Location: " + sel.displayName());
        } else {
            String legacyName = legacyServerName();
            ipLine.setText("Server IP: " + (legacyName.isEmpty() ? "—" : legacyName));
            locationLine.setText("Location: " + (legacyName.isEmpty() ? "No server selected" : legacyName));
        }
        if (!connected && !CoreTunnelStatus.isConnected()) {
            pingLine.setText("Ping: —");
            uptimeLine.setText("Uptime: 00:00:00");
            upSpeed.setText(ConnectionDisplay.formatRate(0));
            downSpeed.setText(ConnectionDisplay.formatRate(0));
        }
    }

    private String legacyServerName() {
        try {
            JSONArray arr = serverArray();
            if (arr == null || arr.length() == 0) return "";
            JSONObject s = arr.getJSONObject(clampPos(mPref.getInt(SERVER_POSITION, 0), arr.length()));
            return s.optString("Name", "");
        } catch (Exception e) {
            return "";
        }
    }

    private void renderState(boolean tunnelActive, boolean isConnected) {
        if (isConnected) {
            ring.setBackgroundResource(R.drawable.cc_ring_connected);
            ringText.setText("CONNECTED");
            ringText.setTextColor(getResources().getColor(R.color.cc_connected));
        } else if (tunnelActive) {
            ring.setBackgroundResource(R.drawable.cc_ring);
            ringText.setText("CONNECTING…");
            ringText.setTextColor(getResources().getColor(R.color.cc_ink));
        } else {
            ring.setBackgroundResource(R.drawable.cc_ring);
            ringText.setText("TAP TO CONNECT");
            ringText.setTextColor(getResources().getColor(R.color.cc_ink));
        }
    }

    // ---------------- hLogStatus listeners ----------------

    @Override public void updateState(String state, String logMessage, int localizedResId,
                                      ConnectionStatus level, int progress) {
        final boolean nowConnected = hLogStatus.VPN_CONNECTED.equals(state)
            || state.equals(resString(R.string.state_connected));
        handler.post(() -> {
            connected = nowConnected;
            renderState(hLogStatus.isTunnelActive(), nowConnected);
            if (nowConnected) {
                startStatsTicker();
                if (graph != null) graph.start();
                measurePing();
            }
            if (hLogStatus.VPN_DISCONNECTED.equals(state) || hLogStatus.VPN_AUTH_FAILED.equals(state)) {
                connected = false;
                pingMs = -1;
                lastIn = -1; lastOut = -1;
                statsHandler.removeCallbacks(statsTask);
                refreshInfoCard();
            }
        });
    }

    @Override public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        long now = System.currentTimeMillis();
        long dt = lastByteAt == 0 ? 0 : now - lastByteAt;
        final long downRate = (lastIn >= 0 && dt > 0) ? Math.max(0, (in - lastIn) * 1000 / dt) : 0;
        final long upRate = (lastOut >= 0 && dt > 0) ? Math.max(0, (out - lastOut) * 1000 / dt) : 0;
        lastIn = in; lastOut = out; lastByteAt = now;
        handler.post(() -> {
            if (connected || CoreTunnelStatus.isConnected()) {
                downSpeed.setText(ConnectionDisplay.formatRate(downRate));
                upSpeed.setText(ConnectionDisplay.formatRate(upRate));
            }
        });
    }

    // ---------------- core tunnel status (Phase 2) ----------------

    /** Runs on the main thread (the listener hops via handler.post). */
    private void onCoreStatusChanged() {
        CoreTunnelStatus.State st = CoreTunnelStatus.getState();
        renderState(hLogStatus.isTunnelActive() || CoreTunnelStatus.isActive(),
            connected || CoreTunnelStatus.isConnected());
        if (CoreTunnelStatus.isConnected()) {
            ServerProfile sel = ProfilePrefs.selected();
            if (sel != null) { activeHost = sel.host; activePort = sel.port; }
            startStatsTicker();
            if (graph != null) graph.start();
            measurePing();
        } else if (st == CoreTunnelStatus.State.ERROR) {
            String msg = CoreTunnelStatus.getMessage();
            if (!msg.isEmpty()) brandedToast(msg);
            refreshInfoCard();
        } else if (st == CoreTunnelStatus.State.IDLE) {
            statsHandler.removeCallbacks(statsTask);
            refreshInfoCard();
        }
    }

    // ---------------- stats ticker / ping ----------------

    private void startStatsTicker() {
        statsHandler.removeCallbacks(statsTask);
        statsHandler.post(statsTask);
    }

    private void tickStats() {
        // Core tunnels (Xray / WireGuard) report through
        // CoreTunnelStatus; their services feed the byte-count fan-out
        // (speeds + chart) themselves, so the ticker only renders
        // uptime + ping here.
        if (CoreTunnelStatus.isActive()) {
            if (CoreTunnelStatus.isConnected() && CoreTunnelStatus.getStartedAt() > 0) {
                uptimeLine.setText("Uptime: " + ConnectionDisplay.formatUptime(
                    System.currentTimeMillis() - CoreTunnelStatus.getStartedAt()));
            }
            pingLine.setText("Ping: " + ConnectionDisplay.formatPing(
                CoreTunnelStatus.isConnected() ? pingMs : -1));
            return;
        }
        if (!hLogStatus.isTunnelActive()) return;
        // Mirror OpenVPNClient.show_stats(): feed the byte-count listeners
        // (and therefore the chart + speeds) from the live tunnel counters.
        try {
            if (!"UDP".equals(mConfig.getNetworkType())) {
                OpenVPNService.ConnectionStats stats = get_connection_stats();
                if (stats != null) hLogStatus.updateByteCount(stats.bytes_in, stats.bytes_out);
            } else {
                hLogStatus.updateByteCount(_upDateBytes.getBytesReceived(), _upDateBytes.getBytesSent());
            }
        } catch (Exception ignored) {
            // service not bound yet / tunnel tearing down: next tick retries
        }
        if (connected && _upDateBytes.isConnected()) {
            uptimeLine.setText("Uptime: " + ConnectionDisplay.formatUptime(_upDateBytes.getElapsedTime()));
        }
        pingLine.setText("Ping: " + ConnectionDisplay.formatPing(connected ? pingMs : -1));
    }

    /** Best-effort ping: time a TCP connect to the active endpoint. */
    private void measurePing() {
        final String host = activeHost;
        final int port = activePort > 0 ? activePort : 443;
        if (host == null || host.isEmpty()) { pingMs = -1; return; }
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 3000);
                pingMs = System.currentTimeMillis() - t0;
            } catch (Exception e) {
                pingMs = -1;
            }
        }).start();
    }

    // ---------------- connect machinery (ported from OpenVPNClient) ----------------

    /**
     * Ring tap routing. SINGLE ACTIVE TUNNEL RULE: if any tunnel is up
     * (legacy engine or a native core), the tap stops THAT tunnel —
     * a second one is never started over it. Otherwise the selected
     * profile's family decides the engine: Xray family -> XrayVpnService,
     * WireGuard -> WgVpnService, everything else -> the legacy path
     * (startOrStopTunnel, unchanged Phase 1 behaviour).
     */
    private void onRingTap() {
        if (CoreTunnelStatus.isActive()) { stopCoreTunnel(); return; }
        if (hLogStatus.isTunnelActive()) { stopTunnelService(); return; }
        ServerProfile sel = ProfilePrefs.selected();
        if (sel != null && XrayConfigGenerator.isXrayProtocol(sel.protocol)) {
            startXrayTunnel(sel);
        } else if (sel != null && sel.protocol == Protocol.WIREGUARD) {
            startWireGuardTunnel(sel);
        } else {
            startOrStopTunnel();
        }
    }

    private void startOrStopTunnel() {
        if (hLogStatus.isTunnelActive()) {
            stopTunnelService();
        } else if (CoreTunnelStatus.isActive()) {
            // Single active tunnel: never start legacy over a core.
            stopCoreTunnel();
        } else {
            mEditor.putInt("loadOnce", 0).apply();
            if (checkConfiguration()) start_connect();
        }
    }

    // ---------------- native core tunnels (Phase 2) ----------------

    private void startXrayTunnel(ServerProfile sel) {
        if (Build.VERSION.SDK_INT < 24) {
            brandedToast("This protocol's core needs Android 7.0+ — the legacy protocols still work on this device");
            return;
        }
        TunnelSettings ts = loadTunnelSettings();
        String json;
        try {
            json = XrayConfigGenerator.generate(sel, ts, resolveProfileByName(ts.doubleVpnProfileName));
        } catch (RuntimeException e) {
            // UnsupportedOperationException (wrong family) and
            // IllegalArgumentException (profile cannot connect) both
            // carry a user-readable message.
            brandedToast(e.getMessage() == null ? "This profile cannot run on the Xray core" : e.getMessage());
            return;
        }
        File file = new File(getFilesDir(), "xray-config.json");
        if (!writeTextFile(file, json)) {
            brandedToast("Could not write the core config");
            return;
        }
        Intent intent = new Intent(this, XrayVpnService.class).setAction(XrayVpnService.ACTION_START);
        intent.putExtra(XrayVpnService.EXTRA_CONFIG_PATH, file.getAbsolutePath());
        intent.putExtra(XrayVpnService.EXTRA_PROFILE_NAME, sel.displayName());
        intent.putExtra(XrayVpnService.EXTRA_DNS1, ts.dns1);
        intent.putExtra(XrayVpnService.EXTRA_DNS2, ts.dns2);
        intent.putExtra(XrayVpnService.EXTRA_MTU, ts.mtu > 0 ? ts.mtu : 1500);
        intent.putExtra(XrayVpnService.EXTRA_SPLIT_ENABLED, ts.splitTunneling);
        if (ts.splitTunneling) {
            intent.putStringArrayListExtra(XrayVpnService.EXTRA_SPLIT_APPS, new ArrayList<>(ts.splitApps));
        }
        activeHost = sel.host;
        activePort = sel.port;
        prepareThenStartCore(intent);
    }

    private void startWireGuardTunnel(ServerProfile sel) {
        if (Build.VERSION.SDK_INT < 24) {
            brandedToast("WireGuard needs Android 7.0+ — the legacy protocols still work on this device");
            return;
        }
        if (sel.wgPrivateKey == null || sel.wgPrivateKey.isEmpty()
            || sel.wgPublicKey == null || sel.wgPublicKey.isEmpty()) {
            brandedToast("WireGuard profile is missing its keys — re-import its .conf file");
            return;
        }
        File file = new File(getFilesDir(), "wireguard.conf");
        if (!writeTextFile(file, WireGuardConfParser.toConf(sel))) {
            brandedToast("Could not write the WireGuard config");
            return;
        }
        TunnelSettings ts = loadTunnelSettings();
        Intent intent = new Intent(this, WgVpnService.class).setAction(WgVpnService.ACTION_START);
        intent.putExtra(WgVpnService.EXTRA_CONFIG_PATH, file.getAbsolutePath());
        intent.putExtra(WgVpnService.EXTRA_PROFILE_NAME, sel.displayName());
        intent.putExtra(WgVpnService.EXTRA_SPLIT_ENABLED, ts.splitTunneling);
        if (ts.splitTunneling) {
            intent.putStringArrayListExtra(WgVpnService.EXTRA_SPLIT_APPS, new ArrayList<>(ts.splitApps));
        }
        activeHost = sel.host;
        activePort = sel.port;
        prepareThenStartCore(intent);
    }

    /** Same VpnService.prepare grant flow as the legacy start_connect. */
    private void prepareThenStartCore(Intent serviceIntent) {
        Intent perm = VpnService.prepare(this);
        if (perm != null) {
            pendingCoreIntent = serviceIntent;
            try {
                startActivityForResult(perm, START_CORE_CALLED);
            } catch (ActivityNotFoundException e) {
                pendingCoreIntent = null;
                brandedToast("VPN permission request failed");
            }
            return;
        }
        launchCoreService(serviceIntent);
    }

    private void launchCoreService(Intent intent) {
        // Both core services call startForeground() immediately, so the
        // foreground-service start is valid (and required on O+) for each.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void stopCoreTunnel() {
        CoreTunnelStatus.Core core = CoreTunnelStatus.getActiveCore();
        if (core == CoreTunnelStatus.Core.XRAY) {
            startService(new Intent(this, XrayVpnService.class).setAction(XrayVpnService.ACTION_STOP));
        } else if (core == CoreTunnelStatus.Core.WIREGUARD) {
            startService(new Intent(this, WgVpnService.class).setAction(WgVpnService.ACTION_STOP));
        }
        statsHandler.removeCallbacks(statsTask);
    }

    /** TunnelSettings from the same prefs the Settings screen writes. */
    private TunnelSettings loadTunnelSettings() {
        TunnelSettings ts = new TunnelSettings();
        try {
            SharedPreferences sp = getSharedPreferences(NexusSettingsActivity.PREFS, MODE_PRIVATE);
            ts.killSwitch = sp.getBoolean(TunnelSettings.KEY_KILL_SWITCH, false);
            ts.splitTunneling = sp.getBoolean(TunnelSettings.KEY_SPLIT, false);
            java.util.Set<String> apps = sp.getStringSet(TunnelSettings.KEY_SPLIT_APPS, null);
            if (apps != null) ts.splitApps = new ArrayList<>(apps);
            ts.doubleVpn = sp.getBoolean(TunnelSettings.KEY_DOUBLE_VPN, false);
            ts.doubleVpnProfileName = sp.getString(TunnelSettings.KEY_DOUBLE_VPN_PROFILE, "");
            ts.dnsLeakProtection = sp.getBoolean(TunnelSettings.KEY_DNS_LEAK, false);
            ts.mtu = sp.getInt(TunnelSettings.KEY_MTU, 0);
            String d1 = sp.getString(TunnelSettings.KEY_DNS1, ts.dns1);
            if (d1 != null && !d1.trim().isEmpty()) ts.dns1 = d1;
            String d2 = sp.getString(TunnelSettings.KEY_DNS2, ts.dns2);
            if (d2 != null && !d2.trim().isEmpty()) ts.dns2 = d2;
        } catch (Exception ignored) {
            // A mistyped pref must never break connect: model defaults apply.
        }
        return ts;
    }

    private ServerProfile resolveProfileByName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try {
            for (ServerProfile p : ProfilePrefs.load()) {
                if (p != null && (name.equals(p.displayName()) || name.equals(p.name))) return p;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean writeTextFile(File file, String text) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void stopTunnelService() {
        if (hLogStatus.isTunnelActive()) {
            connected = false;
            renderState(false, false);
            statsHandler.removeCallbacks(statsTask);
            submitDisconnectIntent("UDP".equals(mConfig.getNetworkType()) ? 0 : 1);
            refreshInfoCard();
        }
    }

    private void start_connect() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            try {
                startActivityForResult(intent, START_BIND_CALLED);
            } catch (ActivityNotFoundException e) {
                brandedToast("VPN permission request failed");
            }
            return;
        }
        scheduleAndStart();
    }

    private void scheduleAndStart() {
        startStatsTicker();
        StatisticGraphData.getStatisticData().getDataTransferStats().startConnected();
        startTunnelService();
    }

    private void startTunnelService() {
        Intent mIntent = new Intent(this, InjectorService.class).setAction(InjectorService.START_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(mIntent);
        else startService(mIntent);
    }

    @Override public void startOpenVPN() {
        resolveExternalPkiAlias(alias -> do_connect(alias));
        super.startOpenVPN();
    }

    private void do_connect(String epkiAlias) {
        prefs.set_string("n_username", mConfig.getSecureString(USERNAME_KEY));
        submitConnectIntent("HarliesDevX", null, prefs.get_string("vpn_proto"), "60",
            mConfig.getSecureString(USERNAME_KEY), mConfig.getSecureString(PASSWORD_KEY),
            false, null, null, epkiAlias, prefs.get_string("compression_mode"),
            null, null, null, true, get_gui_version("net.openvpn.connect.android"));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (request == START_BIND_CALLED) {
            if (result == RESULT_OK) start_connect();
            return;
        }
        if (request == START_CORE_CALLED) {
            Intent pending = pendingCoreIntent;
            pendingCoreIntent = null;
            if (result == RESULT_OK && pending != null) launchCoreService(pending);
            return;
        }
        super.onActivityResult(request, result, data);
    }

    // ---------------- configuration ----------------

    private boolean checkConfiguration() {
        if (!applySelectedProfileOrLegacy()) {
            brandedToast("Error: config load error!");
            return false;
        }
        if (!util.isNetworkAvailable(this)) {
            brandedToast("Please connect to the internet");
            return false;
        }
        if (util.isSniffer(this)) {
            addlogInfo("<b>Another running VPN application has been detected, stop it before</b>");
            brandedToast("Another running VPN application has been detected, stop it before");
            submitDisconnectIntent("UDP".equals(mConfig.getNetworkType()) ? 0 : 1);
            return false;
        }
        if (mConfig.getSecureString(USERNAME_KEY).isEmpty() || mConfig.getSecureString(PASSWORD_KEY).isEmpty()) {
            brandedToast("Server Account is empty! Set it via the profile icon (Account) or pick a server with its own account.");
            return false;
        }
        return true;
    }

    /**
     * Applies the selected ProfileStore profile to the engine when the
     * legacy engine can run it; otherwise engages the legacy selection
     * (name match first, then last-used positions). Returns false only
     * when no configuration could be loaded at all.
     */
    private boolean applySelectedProfileOrLegacy() {
        ServerProfile sel = ProfilePrefs.selected();
        String screenUser = mPref.getString("_screenUsername_key", "");
        String screenPass = mPref.getString("_screenPassword_key", "");
        if (sel != null) {
            LegacyEnginePlan plan = LegacyEnginePlan.fromProfile(sel, screenUser, screenPass);
            if (plan.supported) {
                applyPlan(plan);
                activeHost = plan.serverHost.isEmpty() ? sel.host : plan.serverHost;
                activePort = parsePort(plan.serverPort, sel.port);
                return true;
            }
            // Not applicable in this engine: try the built-in list by name.
            if (selectLegacyByName(sel.displayName()) || selectLegacyByName(sel.name)) {
                reLoad_Configs();
                setActiveFromLegacyRows();
                return true;
            }
            addlogInfo("Selected profile (" + sel.displayName() + ") cannot run on the "
                + "built-in engine yet: " + plan.reason + ". Using the last-used server instead.");
        }
        boolean ok = reLoad_Configs();
        if (ok) setActiveFromLegacyRows();
        return ok;
    }

    /** Active endpoint from the currently selected legacy rows (for ping). */
    private void setActiveFromLegacyRows() {
        try {
            JSONArray s = serverArray();
            JSONArray n = networkArray();
            if (s.length() == 0 || n.length() == 0) return;
            JSONObject sj = s.getJSONObject(clampPos(mPref.getInt(SERVER_POSITION, 0), s.length()));
            JSONObject nj = n.getJSONObject(clampPos(mPref.getInt(NETWORK_POSITION, 0), n.length()));
            activeHost = getServerType(sj, nj);
            String type = getNetworkType(nj);
            if ("UDP".equals(type)) activePort = 0;
            else if (type.startsWith("SSL")) {
                String sp = nj.optString("SSLPort", "");
                activePort = parsePort(sp.isEmpty() ? sj.optString("SSLPort", "") : sp, 443);
            } else activePort = parsePort(sj.optString("TcpPort", ""), 80);
        } catch (Exception ignored) {}
    }

    /** Writes a LegacyEnginePlan into ConfigUtil (the engine's config). */
    private void applyPlan(LegacyEnginePlan plan) {
        ConfigUtil config = mConfig;
        if (plan.keepLegacyNetwork) {
            // Pair the custom cert/remote with the last-used legacy
            // injector network, exactly like the old screen pairs a
            // server row with the chosen network row.
            reLoad_Configs();
        } else {
            config.setServerName(plan.serverName);
            config.setPayloadName(plan.payloadName);
            config.setOvpnCert(mPref.getString(OpenVPN_CERT, ""));
            config.setProxyHost(FileUtils._e2("127.0.0.1"));
            config.setProxyPort("8989");
            config.setIsQueryMode(false);
        }
        if (!plan.ovpnConfig.isEmpty()) config.setOvpnCert(FileUtils.hideJson(plan.ovpnConfig));
        if (!plan.networkType.isEmpty()) config.setNetworkType(plan.networkType);
        if (plan.payloadType > 0) config.setPaylod_type(plan.payloadType);
        if (!plan.serverHost.isEmpty()) config.setServerHost(plan.serverHost);
        if (!plan.serverPort.isEmpty()) config.setServerPort(plan.serverPort);
        if (!plan.proxyHost.isEmpty()) config.setProxyHost(plan.proxyHost);
        if (!plan.proxyPort.isEmpty()) config.setProxyPort(plan.proxyPort);
        if (!plan.payload.isEmpty()) config.setPayload(plan.payload);
        if (!plan.sni.isEmpty()) config.setSni(plan.sni);
        if (!plan.udpConfig.isEmpty()) config.setUDPConfig(plan.udpConfig);
        config.setUser(FileUtils._e2(plan.username));
        config.setUserPass(FileUtils._e2(plan.password));
        mEditor.putBoolean("isAutoLogIn", plan.profileCredentials).apply();
    }

    private boolean selectLegacyByName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        try {
            JSONArray arr = serverArray();
            if (arr == null) return false;
            for (int i = 0; i < arr.length(); i++) {
                if (name.trim().equalsIgnoreCase(arr.getJSONObject(i).optString("Name", ""))) {
                    mEditor.putInt(SERVER_POSITION, i).apply();
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ---- legacy selection load (OpenVPNClient.reLoad_Configs, UI-free) ----

    private JSONArray serverArray() {
        try {
            String d = serverData.getData();
            return d == null ? new JSONArray() : new JSONArray(d);
        } catch (Exception e) { return new JSONArray(); }
    }

    private JSONArray networkArray() {
        try {
            String d = networksData.getData();
            return d == null ? new JSONArray() : new JSONArray(d);
        } catch (Exception e) { return new JSONArray(); }
    }

    private static int clampPos(int pos, int len) {
        return (pos < 0 || pos >= len) ? 0 : pos;
    }

    private String getNetworkType(JSONObject js) throws Exception {
        if (js.has("proto_spin")) {
            return js.getInt("proto_spin") == 0 ? "HTTP" : "UDP";
        } else if (js.has("sslmethod")) {
            int m = js.getInt("sslmethod");
            return m == 0 ? "SSL" : m == 1 ? "SSL+PAYLOAD" : "SSL+PAYLOAD+WS";
        }
        return "";
    }

    private String getServerType(JSONObject sjs, JSONObject pjs) throws Exception {
        String t = pjs.getString("server_type");
        if ("cf".equals(t)) return sjs.getString("ServerIP");
        if ("ws".equals(t)) return sjs.getString("ServerCloudFront");
        return sjs.getString("ServerHTTP");
    }

    private boolean reLoad_Configs() {
        try {
            ConfigUtil config = ConfigUtil.getInstance(this);
            JSONArray jarr1 = serverArray();
            JSONArray jarr2 = networkArray();
            if (jarr1.length() == 0 || jarr2.length() == 0) return false;
            JSONObject s_js = jarr1.getJSONObject(clampPos(mPref.getInt(SERVER_POSITION, 0), jarr1.length()));
            JSONObject p_js = jarr2.getJSONObject(clampPos(mPref.getInt(NETWORK_POSITION, 0), jarr2.length()));
            String p_type = getNetworkType(p_js);
            String mHost = getServerType(s_js, p_js);
            String squidProxy = p_js.getBoolean("UseDefProxy") ? mHost : p_js.getString("SquidProxy");
            config.setServerName(s_js.getString("Name"));
            config.setPayloadName(p_js.getString("Name"));
            config.setOvpnCert(mPref.getString(OpenVPN_CERT, ""));
            config.setProxyHost(FileUtils._e2("127.0.0.1"));
            config.setProxyPort("8989");
            config.setNetworkType(p_type);
            config.setIsQueryMode(false);
            if ("UDP".equals(p_type)) {
                config.setPaylod_type(PAYLOAD_TYPE_UDP);
                config.setServerHost(mHost);
                config.setUDPConfig(p_js.getString("NetworkPayload"));
            } else if ("HTTP".equals(p_type)) {
                String front = p_js.getString("NetworkFrontQuery");
                String back = p_js.getString("NetworkBackQuery");
                config.setPaylod_type(PAYLOAD_TYPE_HTTP_PROXY);
                config.setPayload(p_js.getString("NetworkPayload"));
                config.setServerHost(mHost);
                config.setServerPort(s_js.getString("TcpPort"));
                config.setProxyHost(squidProxy);
                config.setProxyPort(p_js.getString("SquidPort"));
                if (!front.isEmpty()) { config.setIsQueryMode(true); config.setFrontQuery(front); config.setBackQuery(""); }
                else if (!back.isEmpty()) { config.setIsQueryMode(true); config.setBackQuery(back); config.setFrontQuery(""); }
            } else if ("SSL".equals(p_type)) {
                String sslPort = p_js.getString("SSLPort").isEmpty() ? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                config.setPaylod_type(PAYLOAD_TYPE_SSL);
                config.setSni(p_js.getString("SSLSNI"));
                config.setServerHost(mHost);
                config.setServerPort(sslPort);
            } else if ("SSL+PAYLOAD".equals(p_type)) {
                String sslPort = p_js.getString("SSLPort").isEmpty() ? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                config.setPaylod_type(PAYLOAD_TYPE_SSL_PAYLOAD);
                config.setSni(p_js.getString("SSLSNI"));
                config.setPayload(p_js.getString("SSLPayload"));
                config.setServerHost(mHost);
                config.setServerPort(sslPort);
            } else if ("SSL+PAYLOAD+WS".equals(p_type)) {
                String sslPort = p_js.getString("SSLPort").isEmpty() ? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                config.setPaylod_type(PAYLOAD_TYPE_SSL_PROXY);
                config.setSni(p_js.getString("SSLSNI"));
                config.setPayload(p_js.getString("SSLPayload"));
                config.setServerHost(mHost);
                config.setServerPort(sslPort);
                config.setProxyHost(squidProxy);
                config.setProxyPort(p_js.getString("SquidPort"));
            }
            if (s_js.getBoolean("AutoLogIn")) {
                config.setUser(s_js.getString("Username"));
                config.setUserPass(s_js.getString("Password"));
            } else {
                config.setUser(FileUtils._e2(mPref.getString("_screenUsername_key", "")));
                config.setUserPass(FileUtils._e2(mPref.getString("_screenPassword_key", "")));
            }
            mEditor.putBoolean("isAutoLogIn", s_js.getBoolean("AutoLogIn")).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- first-run seeding (ported from OpenVPNClient) ----

    private boolean loadDefaultConfig() {
        if (!mPref.getBoolean("connect_first_time", true)) return false;
        try {
            String data = FileUtils.showJson(BuildConfig.XPTUNNELCONFIG);
            JSONObject obj = new JSONObject(data);
            JSONArray jarr = new JSONArray();
            for (int i = 0; i < obj.getJSONArray("HTTPNetworks").length(); i++) jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
            for (int i = 0; i < obj.getJSONArray("SSLNetworks").length(); i++) jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
            mEditor.putString(CONFIG_VERSION, obj.getString("Version")).apply();
            mEditor.putString("_ReleaseNotes", obj.getString("ReleaseNotes")).apply();
            mEditor.putString("_contactSupport", obj.getString("contactSupport")).apply();
            mEditor.putString(OpenVPN_CERT, obj.getString("Ovpn_Cert")).apply();
            serverData.insertData(obj.getJSONArray("Servers").toString());
            networksData.insertData("" + jarr);
            reLoad_Configs();
            mEditor.putBoolean("connect_first_time", false).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void loadDefaultProfiles() {
        boolean finished = false;
        try {
            String serverName = "HarliesDevX";
            String encoded = String.format("%s.ovpn", URLEncoder.encode(serverName, "UTF-8"));
            String config = FileUtils.readFromRaw(this, R.raw.ovpn_cert);
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new StringReader(config));
            VpnProfile vp = cp.convertProfile();
            vp.mName = serverName;
            if (vp.checkProfile(this) != R.string.no_error_found) return;
            vp.mProfileCreator = getPackageName();
            vp.mUsername = "HarliesDevX";
            vp.mPassword = "HarliesDevX";
            vp.mUseCustomConfig = true;
            vp.mCustomConfigOptions = new StringBuffer().append(vp.mCustomConfigOptions)
                .append("http-proxy 127.0.0.1 8989").toString();
            String cfg = vp.getConfigFile(this, true);
            File out = new File(getFilesDir(), encoded);
            OutputStream os = new FileOutputStream(out);
            os.write(cfg.getBytes());
            os.flush();
            os.close();
            finished = true;
        } catch (Exception ignored) {
            // first-run profile write is best-effort; connect surfaces errors
        }
        if (finished) {
            startService(new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_ADD_PROFILE));
        }
    }

    // ---------------- feed (kept from the previous Home) ----------------

    private void maybeAutoUpdateFeed() {
        if (!FeedUpdater.shouldFetchNow(this)) return;
        new Thread(() -> {
            boolean ok = FeedUpdater.fetchAndApply(NexusHomeActivity.this);
            if (ok && !isFinishing() && !isDestroyed()) {
                runOnUiThread(() -> { refreshHeader(); refreshInfoCard(); });
            }
        }).start();
    }

    private static int parsePort(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private String prettyProtocol(String id) {
        if (id == null || "AUTO".equals(id)) return "Auto";
        String s = id.replace('_', ' ');
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    /** Bottom-center branded toast (her UI rule). */
    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_LONG);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, (int) (28 * getResources().getDisplayMetrics().density));
        t.show();
    }
}
