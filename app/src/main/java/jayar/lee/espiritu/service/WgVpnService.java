package jayar.lee.espiritu.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.Interface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.activities.NexusHomeActivity;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.view.StatisticGraphData;

/**
 * WireGuard control service, backed by the official WireGuard tunnel
 * library (com.wireguard.android:tunnel, GoBackend).
 *
 * This is deliberately NOT an android.net.VpnService: the library ships
 * its own GoBackend$VpnService (declared in the library manifest and
 * merged automatically), which owns the TUN interface. This class is a
 * plain foreground control service: it parses the .conf file Nexus Home
 * rendered from the selected profile (WireGuardConfParser.toConf),
 * brings the tunnel up/down through GoBackend, and mirrors state +
 * traffic into CoreTunnelStatus and the legacy stat fan-out
 * (hLogStatus.updateByteCount + DataTransferStats), so Home's ring,
 * speeds and chart read WireGuard exactly like the other engines.
 * Home runs VpnService.prepare() before starting this service — the
 * library's backend service needs that grant.
 *
 * Lifecycle: ALL start/stop work is serialized on a single-threaded
 * executor (GoBackend.setState blocks). A stop requested while the
 * tunnel is still coming up queues behind the start and brings down
 * exactly what the start brought up — it can never orphan a live
 * tunnel behind a dead control service. A malformed .conf fails into
 * the CoreTunnelStatus ERROR state with a log line; it never crashes.
 *
 * Split tunneling maps to the WireGuard config's excluded applications
 * (the selected apps bypass the tunnel), applied by rebuilding the
 * parsed Config with Interface.Builder.
 *
 * The library's AAR requires API 24+ while the app supports 21+: on
 * 21–23 this service reports an honest ERROR via CoreTunnelStatus
 * instead of crashing, and Home keeps those devices on legacy engines.
 */
public class WgVpnService extends Service {
    private static final String TAG = "WgVpnService";
    public static final String ACTION_START = "jayar.lee.espiritu.service.WgVpnService.START";
    public static final String ACTION_STOP = "jayar.lee.espiritu.service.WgVpnService.STOP";

    public static final String EXTRA_CONFIG_PATH = "config_path";
    public static final String EXTRA_PROFILE_NAME = "profile_name";
    public static final String EXTRA_SPLIT_ENABLED = "split_enabled";
    public static final String EXTRA_SPLIT_APPS = "split_apps";

    private static final String CHANNEL_ID = "gfv_core_tunnel";
    private static final int NOTIF_ID = 0xFAB1E;

    /** Serializes every start/stop so lifecycle races cannot occur. */
    private final ExecutorService lifecycle =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "gfv-wg-lifecycle"));

    private GoBackend backend;
    private WgTunnel tunnel;
    private volatile boolean running = false;
    private Thread statsThread;
    private String profileName = "";

    /** Tunnel identity for the library: name is sanitized to its rules.
     *  State callbacks arrive on the library's own thread; they only
     *  publish state (and start the stats loop) — never UI work. */
    private final class WgTunnel implements Tunnel {
        private final String name;
        WgTunnel(String name) { this.name = name; }
        @Override public String getName() { return name; }
        @Override public void onStateChange(State newState) {
            if (newState == State.UP) {
                running = true;
                CoreTunnelStatus.report(CoreTunnelStatus.Core.WIREGUARD,
                    CoreTunnelStatus.State.CONNECTED, profileName, "");
                StatisticGraphData.getStatisticData().getDataTransferStats().startConnected();
                startStatsLoop();
            } else if (newState == State.DOWN) {
                running = false;
                stopStatsLoop();
            }
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            lifecycle.execute(this::doStop);
            return START_NOT_STICKY;
        }
        if (intent == null) return START_NOT_STICKY;
        profileName = intent.getStringExtra(EXTRA_PROFILE_NAME);
        if (profileName == null) profileName = "";
        final String name = profileName;
        final String configPath = intent.getStringExtra(EXTRA_CONFIG_PATH);
        final boolean split = intent.getBooleanExtra(EXTRA_SPLIT_ENABLED, false);
        final ArrayList<String> splitApps = intent.getStringArrayListExtra(EXTRA_SPLIT_APPS);

        CoreTunnelStatus.report(CoreTunnelStatus.Core.WIREGUARD,
            CoreTunnelStatus.State.CONNECTING, name, "");
        // Foreground immediately: Home starts this service with
        // startForegroundService on API 26+, which requires it.
        startForeground(NOTIF_ID, buildNotification(name));

        if (Build.VERSION.SDK_INT < 24) {
            fail(name, "WireGuard core needs Android 7.0 or newer on this device");
            return START_NOT_STICKY;
        }
        if (configPath == null) {
            fail(name, "Missing WireGuard config");
            return START_NOT_STICKY;
        }
        lifecycle.execute(() -> doStart(name, configPath, split, splitApps));
        return START_STICKY;
    }

    /** Runs on the lifecycle thread (setState blocks). */
    private void doStart(String name, String configPath, boolean split, ArrayList<String> splitApps) {
        if (running) return; // duplicate start: one tunnel at a time
        try {
            Config config;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(configPath)), "UTF-8"))) {
                config = Config.parse(reader);
            }
            if (split && splitApps != null && !splitApps.isEmpty()) {
                config = withExcludedApps(config, splitApps);
            }
            if (backend == null) backend = new GoBackend(this);
            tunnel = new WgTunnel(tunnelName(name));
            Tunnel.State result = backend.setState(tunnel, Tunnel.State.UP, config);
            if (result != Tunnel.State.UP) {
                fail(name, "WireGuard tunnel did not come up");
            }
            // CONNECTED itself is reported by WgTunnel.onStateChange.
        } catch (Throwable t) {
            // Bad .conf (BadConfigException), missing keys, backend
            // failure: an ERROR state + a log line, never a crash.
            Log.e(TAG, "start failed", t);
            fail(name, t.getMessage() == null ? "WireGuard failed to start" : t.getMessage());
        }
    }

    /** Rebuilds the parsed config with split-tunnel exclusions. */
    private static Config withExcludedApps(Config config, ArrayList<String> apps) throws Exception {
        Interface old = config.getInterface();
        Interface.Builder b = new Interface.Builder();
        b.setKeyPair(old.getKeyPair());
        b.addAddresses(old.getAddresses());
        b.addDnsServers(old.getDnsServers());
        b.addDnsSearchDomains(old.getDnsSearchDomains());
        if (old.getListenPort().isPresent()) b.setListenPort(old.getListenPort().get());
        if (old.getMtu().isPresent()) b.setMtu(old.getMtu().get());
        b.excludeApplications(old.getExcludedApplications());
        b.excludeApplications(apps);
        b.includeApplications(old.getIncludedApplications());
        return new Config.Builder().setInterface(b.build()).addPeers(config.getPeers()).build();
    }

    private void startStatsLoop() {
        stopStatsLoop();
        statsThread = new Thread(() -> {
            long prevRx = -1, prevTx = -1;
            while (running) {
                try { Thread.sleep(1000L); } catch (InterruptedException e) { return; }
                if (!running || backend == null || tunnel == null) return;
                try {
                    Statistics stats = backend.getStatistics(tunnel);
                    if (stats == null) continue;
                    long rx = stats.totalRx(), tx = stats.totalTx();
                    CoreTunnelStatus.reportTraffic(rx, tx);
                    hLogStatus.updateByteCount(rx, tx);
                    if (prevRx >= 0) {
                        long dRx = Math.max(0, rx - prevRx), dTx = Math.max(0, tx - prevTx);
                        if (dRx > 0 || dTx > 0) {
                            StatisticGraphData.DataTransferStats s =
                                StatisticGraphData.getStatisticData().getDataTransferStats();
                            s.addBytesReceived(dRx);
                            s.addBytesSent(dTx);
                        }
                    }
                    prevRx = rx; prevTx = tx;
                } catch (Throwable ignored) {}
            }
        }, "gfv-wg-stats");
        statsThread.start();
    }

    private void stopStatsLoop() {
        if (statsThread != null) { statsThread.interrupt(); statsThread = null; }
    }

    private void fail(String name, String msg) {
        Log.e(TAG, "wireguard start failed: " + msg);
        running = false;
        stopStatsLoop();
        // Best effort: never leave a half-started tunnel up behind us.
        if (backend != null && tunnel != null) {
            try { backend.setState(tunnel, Tunnel.State.DOWN, null); } catch (Throwable ignored) {}
        }
        CoreTunnelStatus.report(CoreTunnelStatus.Core.WIREGUARD,
            CoreTunnelStatus.State.ERROR, name, msg);
        stopForeground(true);
        stopSelf();
    }

    /** Runs on the lifecycle thread (queued behind any in-flight start). */
    private void doStop() {
        CoreTunnelStatus.report(CoreTunnelStatus.Core.WIREGUARD,
            CoreTunnelStatus.State.DISCONNECTING, profileName, "");
        running = false;
        stopStatsLoop();
        if (backend != null && tunnel != null) {
            try { backend.setState(tunnel, Tunnel.State.DOWN, null); } catch (Throwable ignored) {}
        }
        StatisticGraphData.getStatisticData().getDataTransferStats().stop();
        CoreTunnelStatus.report(CoreTunnelStatus.Core.WIREGUARD,
            CoreTunnelStatus.State.IDLE, profileName, "");
        stopForeground(true);
        stopSelf();
    }

    public void stopTunnel() {
        lifecycle.execute(this::doStop);
    }

    @Override
    public void onDestroy() {
        running = false;
        stopStatsLoop();
        if (backend != null && tunnel != null) {
            final GoBackend b = backend;
            final WgTunnel t = tunnel;
            new Thread(() -> {
                try { b.setState(t, Tunnel.State.DOWN, null); } catch (Throwable ignored) {}
            }, "gfv-wg-destroy").start();
        }
        lifecycle.shutdownNow();
        CoreTunnelStatus.clearIf(CoreTunnelStatus.Core.WIREGUARD);
        super.onDestroy();
    }

    /** Library tunnel names: [a-zA-Z0-9_-]{1,15}. */
    static String tunnelName(String profile) {
        String src = profile == null ? "" : profile;
        StringBuilder sb = new StringBuilder("gfv-");
        for (int i = 0; i < src.length() && sb.length() < 15; i++) {
            char c = src.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-') sb.append(c);
        }
        return sb.length() > 4 ? sb.toString() : "gfv-wg";
    }

    private Notification buildNotification(String name) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Git Fable VPN", NotificationManager.IMPORTANCE_LOW));
            }
        }
        Intent open = new Intent(this, NexusHomeActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return b.setContentTitle("Git Fable VPN")
            .setContentText(name == null || name.isEmpty() ? "WireGuard" : name)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }
}
