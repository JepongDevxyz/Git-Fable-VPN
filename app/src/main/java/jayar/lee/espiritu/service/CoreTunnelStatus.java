package jayar.lee.espiritu.service;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared, process-wide status for the native-core tunnels (Xray core via
 * XrayVpnService, WireGuard via WgVpnService). The legacy injector/OpenVPN
 * engine reports through hLogStatus as before; Nexus Home and Statistics
 * observe BOTH: this holder for core tunnels, hLogStatus for legacy.
 *
 * Single-active-tunnel rule: Home never starts a core tunnel while the
 * legacy engine is up (and vice versa) — the ring stops whichever tunnel
 * is active instead. The {@code activeCore} here says which core (if
 * any) currently owns the tunnel.
 *
 * State changes notify listeners (weak refs; UI posts to its handler).
 * Traffic totals are cumulative session bytes pushed by the services and
 * polled by the UI in its existing 1-second ticker — no listener spam.
 */
public final class CoreTunnelStatus {
    public enum Core { NONE, XRAY, WIREGUARD }
    public enum State { IDLE, CONNECTING, CONNECTED, DISCONNECTING, ERROR }

    public interface Listener { void onCoreStatusChanged(); }

    private static Core activeCore = Core.NONE;
    private static State state = State.IDLE;
    private static long startedAt = 0L;
    private static String profileName = "";
    private static String message = "";
    private static long totalRx = 0L;
    private static long totalTx = 0L;
    private static final List<WeakReference<Listener>> LISTENERS = new ArrayList<>();

    private CoreTunnelStatus() {}

    public static synchronized void report(Core core, State newState, String profile, String msg) {
        activeCore = core;
        state = newState;
        if (profile != null) profileName = profile;
        message = msg == null ? "" : msg;
        if (newState == State.CONNECTING) { totalRx = 0L; totalTx = 0L; startedAt = 0L; }
        if (newState == State.CONNECTED && startedAt == 0L) startedAt = System.currentTimeMillis();
        if (newState == State.IDLE || newState == State.ERROR) {
            if (newState == State.IDLE) activeCore = Core.NONE;
            startedAt = 0L;
        }
        notifyListeners();
    }

    public static synchronized void reportTraffic(long rxBytes, long txBytes) {
        totalRx = rxBytes;
        totalTx = txBytes;
    }

    /** Marks the core idle only if it is the one currently tracked. */
    public static synchronized void clearIf(Core core) {
        if (activeCore == core) {
            activeCore = Core.NONE;
            state = State.IDLE;
            startedAt = 0L;
            profileName = "";
            message = "";
            notifyListeners();
        }
    }

    public static synchronized Core getActiveCore() { return activeCore; }
    public static synchronized State getState() { return state; }
    public static synchronized long getStartedAt() { return startedAt; }
    public static synchronized String getProfileName() { return profileName; }
    public static synchronized String getMessage() { return message; }
    public static synchronized long getTotalRx() { return totalRx; }
    public static synchronized long getTotalTx() { return totalTx; }

    /** A core tunnel is up or coming up (ring should offer "stop"). */
    public static synchronized boolean isActive() {
        return activeCore != Core.NONE
            && (state == State.CONNECTING || state == State.CONNECTED || state == State.DISCONNECTING);
    }

    public static synchronized boolean isConnected() {
        return activeCore != Core.NONE && state == State.CONNECTED;
    }

    public static void addListener(Listener l) {
        synchronized (CoreTunnelStatus.class) {
            LISTENERS.add(new WeakReference<>(l));
        }
    }

    public static void removeListener(Listener l) {
        synchronized (CoreTunnelStatus.class) {
            for (int i = LISTENERS.size() - 1; i >= 0; i--) {
                Listener x = LISTENERS.get(i).get();
                if (x == null || x == l) LISTENERS.remove(i);
            }
        }
    }

    private static void notifyListeners() {
        List<Listener> snapshot = new ArrayList<>();
        synchronized (CoreTunnelStatus.class) {
            for (int i = LISTENERS.size() - 1; i >= 0; i--) {
                Listener x = LISTENERS.get(i).get();
                if (x == null) LISTENERS.remove(i); else snapshot.add(x);
            }
        }
        for (Listener l : snapshot) {
            try { l.onCoreStatusChanged(); } catch (Throwable ignored) {}
        }
    }
}
