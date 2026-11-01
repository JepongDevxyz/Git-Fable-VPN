package jayar.lee.espiritu.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.activities.NexusHomeActivity;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.view.StatisticGraphData;
import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

/**
 * VPN service for the Xray family (VLESS / VMess / Trojan / Shadowsocks /
 * Hysteria2), backed by the bundled AndroidLibXrayLite core
 * (libv2ray.aar). Follows the library author's production pattern
 * (v2rayNG): this app's VpnService.Builder creates the TUN interface
 * (address 172.19.0.2/30, full route, DNS from TunnelSettings, own
 * package ALWAYS disallowed so the core's own outbound sockets bypass
 * the interface and cannot loop), and the established fd is handed to
 * CoreController.startLoop(configJson, fd); the config carries the
 * matching "tun" inbound (see XrayConfigGenerator).
 *
 * Lifecycle: start with ACTION_START + EXTRA_CONFIG_PATH pointing at a
 * JSON file in filesDir written by Nexus Home; stop with ACTION_STOP.
 * ALL lifecycle work (start and stop) is serialized on a single-
 * threaded executor: a stop requested while the core is still starting
 * (first-run geo-asset extraction takes seconds) queues behind the
 * start and tears down exactly what the start built — it can never
 * orphan a running core, and start-after-stop always builds a fresh
 * CoreController. A bad or missing config file fails into the
 * CoreTunnelStatus ERROR state with a log line; it never crashes.
 * State is reported through CoreTunnelStatus; session traffic is
 * pushed both there and into the legacy stat fan-out
 * (hLogStatus.updateByteCount + DataTransferStats buckets) so Home's
 * ring/speeds/chart and the Statistics screen read core tunnels
 * exactly like legacy ones.
 *
 * The core's native libraries require API 24+ (the AAR's minSdk), while
 * the app supports API 21+: on 21–23 this service reports an honest
 * ERROR via CoreTunnelStatus instead of crashing, and Home keeps those
 * devices on the legacy engine.
 */
public class XrayVpnService extends VpnService implements CoreCallbackHandler {
    private static final String TAG = "XrayVpnService";
    public static final String ACTION_START = "jayar.lee.espiritu.service.XrayVpnService.START";
    public static final String ACTION_STOP = "jayar.lee.espiritu.service.XrayVpnService.STOP";

    public static final String EXTRA_CONFIG_PATH = "config_path";
    public static final String EXTRA_PROFILE_NAME = "profile_name";
    public static final String EXTRA_DNS1 = "dns1";
    public static final String EXTRA_DNS2 = "dns2";
    public static final String EXTRA_MTU = "mtu";
    public static final String EXTRA_SPLIT_ENABLED = "split_enabled";
    public static final String EXTRA_SPLIT_APPS = "split_apps";

    private static final String CHANNEL_ID = "gfv_core_tunnel";
    private static final int NOTIF_ID = 0xFAB1E;
    private static final String TUN_ADDRESS = "172.19.0.2";
    private static boolean envInitialized = false;

    /** Serializes every start/stop so lifecycle races cannot occur. */
    private final ExecutorService lifecycle =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "gfv-xray-lifecycle"));

    private CoreController controller;
    private ParcelFileDescriptor pfd;
    private volatile boolean running = false;
    private Thread statsThread;
    private String profileName = "";

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
        final String dns1 = intent.getStringExtra(EXTRA_DNS1);
        final String dns2 = intent.getStringExtra(EXTRA_DNS2);
        final int mtu = intent.getIntExtra(EXTRA_MTU, 1500);
        final boolean split = intent.getBooleanExtra(EXTRA_SPLIT_ENABLED, false);
        final ArrayList<String> splitApps = intent.getStringArrayListExtra(EXTRA_SPLIT_APPS);

        CoreTunnelStatus.report(CoreTunnelStatus.Core.XRAY,
            CoreTunnelStatus.State.CONNECTING, name, "");
        // Foreground immediately: Home starts this service with
        // startForegroundService on API 26+, which requires it.
        startForeground(NOTIF_ID, buildNotification(name));

        if (Build.VERSION.SDK_INT < 24) {
            CoreTunnelStatus.report(CoreTunnelStatus.Core.XRAY, CoreTunnelStatus.State.ERROR,
                name, "Xray core needs Android 7.0 or newer on this device");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (configPath == null) {
            CoreTunnelStatus.report(CoreTunnelStatus.Core.XRAY, CoreTunnelStatus.State.ERROR,
                name, "Missing core config");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        lifecycle.execute(() -> doStart(name, configPath, dns1, dns2, mtu, split, splitApps));
        return START_STICKY;
    }

    /** Runs on the lifecycle thread. */
    private void doStart(String name, String configPath, String dns1, String dns2, int mtu,
                         boolean split, ArrayList<String> splitApps) {
        if (running) return; // duplicate start: one core session at a time
        try {
            String configJson = readFile(new File(configPath));
            if (configJson == null || configJson.trim().isEmpty()) {
                fail(name, "Core config is empty");
                return;
            }
            File assetDir = ensureGeoAssets();
            synchronized (XrayVpnService.class) {
                if (!envInitialized) {
                    Libv2ray.initCoreEnv(assetDir.getAbsolutePath(), "");
                    envInitialized = true;
                }
            }
            if (controller == null) controller = Libv2ray.newCoreController(this);

            Builder builder = new Builder()
                .setSession("Git Fable VPN")
                .setMtu(mtu > 0 ? mtu : 1500)
                .addAddress(TUN_ADDRESS, 30)
                .addRoute("0.0.0.0", 0);
            if (dns1 != null && !dns1.trim().isEmpty()) builder.addDnsServer(dns1.trim());
            if (dns2 != null && !dns2.trim().isEmpty()) builder.addDnsServer(dns2.trim());
            // Loop prevention: the core process's own sockets must never
            // re-enter the TUN interface (same rule as v2rayNG).
            try { builder.addDisallowedApplication(getPackageName()); }
            catch (PackageManager.NameNotFoundException ignored) {}
            if (split && splitApps != null) {
                for (String pkg : splitApps) {
                    if (pkg == null || pkg.trim().isEmpty()) continue;
                    try { builder.addDisallowedApplication(pkg.trim()); }
                    catch (PackageManager.NameNotFoundException ignored) {}
                }
            }
            pfd = builder.establish();
            if (pfd == null) {
                fail(name, "Could not establish the VPN interface");
                return;
            }
            controller.startLoop(configJson, pfd.getFd());
            if (!controller.getIsRunning()) {
                fail(name, "Xray core did not start");
                return;
            }
            running = true;
            CoreTunnelStatus.report(CoreTunnelStatus.Core.XRAY,
                CoreTunnelStatus.State.CONNECTED, name, "");
            StatisticGraphData.getStatisticData().getDataTransferStats().startConnected();
            startStatsLoop();
        } catch (Throwable t) {
            // Bad config file, core load failure, missing assets: an
            // ERROR state + a log line, never a crash.
            Log.e(TAG, "start failed", t);
            fail(name, t.getMessage() == null ? "Xray core failed to start" : t.getMessage());
        }
    }

    /** Core stats reset on every query — accumulate the deltas into
     *  session totals and fan them out to the legacy stat pipeline. */
    private void startStatsLoop() {
        statsThread = new Thread(() -> {
            long totalRx = 0L, totalTx = 0L;
            while (running) {
                try { Thread.sleep(1000L); } catch (InterruptedException e) { return; }
                if (!running || controller == null) return;
                try {
                    String raw = controller.queryAllOutboundTrafficStats();
                    long dRx = 0L, dTx = 0L;
                    if (raw != null && !raw.isEmpty()) {
                        for (String entry : raw.split(";")) {
                            String[] parts = entry.split(",");
                            if (parts.length != 3) continue;
                            long value;
                            try { value = Long.parseLong(parts[2].trim()); }
                            catch (NumberFormatException nfe) { continue; }
                            String tag = parts[0].trim();
                            if (!("proxy".equals(tag) || "proxy-2".equals(tag))) continue;
                            if ("downlink".equals(parts[1].trim())) dRx += value;
                            else if ("uplink".equals(parts[1].trim())) dTx += value;
                        }
                    }
                    if (dRx > 0 || dTx > 0) {
                        totalRx += dRx;
                        totalTx += dTx;
                        CoreTunnelStatus.reportTraffic(totalRx, totalTx);
                        hLogStatus.updateByteCount(totalRx, totalTx);
                        StatisticGraphData.DataTransferStats stats =
                            StatisticGraphData.getStatisticData().getDataTransferStats();
                        stats.addBytesReceived(dRx);
                        stats.addBytesSent(dTx);
                    }
                } catch (Throwable ignored) {}
            }
        }, "gfv-xray-stats");
        statsThread.start();
    }

    private void fail(String name, String msg) {
        Log.e(TAG, "core start failed: " + msg);
        running = false;
        stopStatsLoop();
        if (controller != null) {
            // A start that failed midway may still have a live core:
            // stop it before dropping the reference (no orphans), and
            // the next start builds a fresh controller.
            try { controller.stopLoop(); } catch (Throwable ignored) {}
            controller = null;
        }
        CoreTunnelStatus.report(CoreTunnelStatus.Core.XRAY,
            CoreTunnelStatus.State.ERROR, name, msg);
        closePfd();
        stopForeground(true);
        stopSelf();
    }

    /** Runs on the lifecycle thread (queued behind any in-flight start). */
    private void doStop() {
        CoreTunnelStatus.report(CoreTunnelStatus.Core.XRAY,
            CoreTunnelStatus.State.DISCONNECTING, profileName, "");
        running = false;
        stopStatsLoop();
        if (controller != null) {
            try { controller.stopLoop(); } catch (Throwable ignored) {}
            controller = null; // next start builds a fresh controller
        }
        closePfd();
        StatisticGraphData.getStatisticData().getDataTransferStats().stop();
        CoreTunnelStatus.report(CoreTunnelStatus.Core.XRAY,
            CoreTunnelStatus.State.IDLE, profileName, "");
        stopForeground(true);
        stopSelf();
    }

    public void stopTunnel() {
        lifecycle.execute(this::doStop);
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        running = false;
        stopStatsLoop();
        if (controller != null) {
            final CoreController c = controller;
            controller = null;
            new Thread(() -> {
                try { c.stopLoop(); } catch (Throwable ignored) {}
            }, "gfv-xray-destroy").start();
        }
        closePfd();
        lifecycle.shutdownNow();
        CoreTunnelStatus.clearIf(CoreTunnelStatus.Core.XRAY);
        super.onDestroy();
    }

    private void stopStatsLoop() {
        if (statsThread != null) { statsThread.interrupt(); statsThread = null; }
    }

    private void closePfd() {
        if (pfd != null) {
            try { pfd.close(); } catch (Throwable ignored) {}
            pfd = null;
        }
    }

    // ---- libv2ray CoreCallbackHandler ----
    @Override public long startup() { return 0; }
    @Override public long shutdown() { return 0; }
    @Override public long onEmitStatus(long code, String msg) {
        if (msg != null && !msg.isEmpty()) Log.i(TAG, "core: " + msg);
        return 0;
    }

    /** Extracts the core's geo assets (bundled in the AAR) to filesDir so
     *  initCoreEnv can point xray.location.asset at a real directory. */
    private File ensureGeoAssets() throws Exception {
        File dir = new File(getFilesDir(), "xray-assets");
        if (!dir.exists()) dir.mkdirs();
        for (String name : new String[]{"geoip.dat", "geosite.dat"}) {
            File out = new File(dir, name);
            if (out.exists() && out.length() > 0) continue;
            try (InputStream in = getAssets().open(name);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            }
        }
        return dir;
    }

    private static String readFile(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            return new String(data, "UTF-8");
        }
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
            .setContentText(name == null || name.isEmpty() ? "Xray core" : name)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }
}
