package jayar.lee.espiritu.protocols;

import java.util.Locale;

/**
 * Pure formatting for the Nexus Home / Statistics connection readouts
 * (speeds, totals, uptime, ping) so the exact strings are unit-testable
 * and both screens render identically.
 */
public final class ConnectionDisplay {
    private ConnectionDisplay() {}

    /** Bits-per-second rate, e.g. "0 Mbps", "850 Kbps", "1.25 Mbps". */
    public static String formatRate(long bytesPerSecond) {
        if (bytesPerSecond < 0) bytesPerSecond = 0;
        double bits = bytesPerSecond * 8.0;
        if (bits < 1000) return "0 Mbps";
        if (bits < 1000 * 1000) return trim(bits / 1000.0) + " Kbps";
        if (bits < 1000.0 * 1000 * 1000) return trim(bits / (1000.0 * 1000)) + " Mbps";
        return trim(bits / (1000.0 * 1000 * 1000)) + " Gbps";
    }

    /** Byte total, e.g. "0 B", "1.5 KB", "23.4 MB" (1024-based). */
    public static String formatBytes(long bytes) {
        if (bytes < 0) bytes = 0;
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        do { v /= 1024.0; i++; } while (v >= 1024 && i < units.length - 1);
        return trim(v) + " " + units[i];
    }

    /** Uptime as HH:MM:SS (hours may exceed 24), zero-padded. */
    public static String formatUptime(long millis) {
        if (millis < 0) millis = 0;
        long totalSec = millis / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    /** Ping readout: measured millis, or the em-dash placeholder. */
    public static String formatPing(long millis) {
        return millis >= 0 ? millis + " ms" : "—";
    }

    private static String trim(double v) {
        if (v == Math.floor(v)) return Long.toString(Math.round(v));
        return String.format(Locale.US, "%.2f", v);
    }
}
