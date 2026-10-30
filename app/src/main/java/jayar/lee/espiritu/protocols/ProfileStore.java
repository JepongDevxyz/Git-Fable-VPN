package jayar.lee.espiritu.protocols;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serializes a List<ServerProfile> to/from a single String so the whole
 * profile library can live in one SharedPreferences value
 * (HarliesApplication private prefs, key "cc_profiles_v1").
 *
 * Format (line-based, versioned):
 *   GFVPN-PROFILES/1
 *   BEGIN
 *   protocol=VLESS_WS
 *   name=...                (plain when single-line, else name_b64=...)
 *   port=443
 *   ovpnConfig_b64=...      (free-text fields are ALWAYS Base64-wrapped)
 *   payload_b64=...
 *   extra_count=1
 *   extra.0=<b64 key>|<b64 value>
 *   END
 *
 * Design rules:
 *  - Every ServerProfile field written by the parsers / generator /
 *    JepongVpsFactory round-trips exactly (null Strings normalize to "").
 *  - Single-line String fields are written plain when they contain no
 *    CR/LF; if they do, a "<key>_b64" line is written instead and the
 *    reader prefers it — so even hostile share-link names survive.
 *  - Free-text fields (ovpnConfig, payload) are always Base64.
 *  - Reader is tolerant: null/empty/garbled input -> empty list,
 *    malformed blocks are skipped, unknown keys are ignored (forward
 *    compatible with future fields).
 *
 * Pure Java (no Android deps) so it unit-tests with plain javac.
 */
public final class ProfileStore {
    private ProfileStore() {}

    public static final String HEADER = "GFVPN-PROFILES/1";

    // ---------------- public API ----------------

    public static String serialize(List<ServerProfile> profiles) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        if (profiles == null) return sb.toString();
        for (ServerProfile p : profiles) {
            if (p == null) continue;
            sb.append("BEGIN\n");
            line(sb, "protocol", p.protocol == null ? "" : p.protocol.name());
            str(sb, "name", p.name);
            str(sb, "host", p.host);
            line(sb, "port", Integer.toString(p.port));
            str(sb, "uuid", p.uuid);
            str(sb, "password", p.password);
            str(sb, "username", p.username);
            line(sb, "alterId", Integer.toString(p.alterId));
            str(sb, "method", p.method);
            str(sb, "network", p.network);
            str(sb, "path", p.path);
            str(sb, "hostHeader", p.hostHeader);
            str(sb, "serviceName", p.serviceName);
            str(sb, "security", p.security);
            str(sb, "sni", p.sni);
            line(sb, "allowInsecure", p.allowInsecure ? "1" : "0");
            str(sb, "flow", p.flow);
            str(sb, "wgPrivateKey", p.wgPrivateKey);
            str(sb, "wgAddress", p.wgAddress);
            str(sb, "wgPublicKey", p.wgPublicKey);
            str(sb, "wgPresharedKey", p.wgPresharedKey);
            str(sb, "wgAllowedIPs", p.wgAllowedIPs);
            str(sb, "wgDns", p.wgDns);
            line(sb, "wgMtu", Integer.toString(p.wgMtu));
            str(sb, "wgReserved", p.wgReserved);
            str(sb, "hyObfs", p.hyObfs);
            str(sb, "hyObfsPassword", p.hyObfsPassword);
            b64(sb, "payload", p.payload);
            str(sb, "proxyHost", p.proxyHost);
            line(sb, "proxyPort", Integer.toString(p.proxyPort));
            str(sb, "dnsNsDomain", p.dnsNsDomain);
            str(sb, "dnsPubKey", p.dnsPubKey);
            line(sb, "dnsPort", Integer.toString(p.dnsPort));
            str(sb, "udpPsk", p.udpPsk);
            b64(sb, "ovpnConfig", p.ovpnConfig);
            if (p.extra != null && !p.extra.isEmpty()) {
                line(sb, "extra_count", Integer.toString(p.extra.size()));
                int i = 0;
                for (Map.Entry<String, String> e : p.extra.entrySet()) {
                    line(sb, "extra." + (i++),
                        b64enc(e.getKey()) + "|" + b64enc(e.getValue()));
                }
            }
            sb.append("END\n");
        }
        return sb.toString();
    }

    public static List<ServerProfile> deserialize(String data) {
        List<ServerProfile> out = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) return out;
        ServerProfile cur = null;
        for (String raw : data.split("[\\r\\n]+")) {
            String ln = raw;
            if ("BEGIN".equals(ln)) { cur = new ServerProfile(); continue; }
            if ("END".equals(ln)) {
                if (cur != null && cur.protocol != null) out.add(cur);
                cur = null;
                continue;
            }
            if (cur == null) continue; // header line / junk between blocks
            int eq = ln.indexOf('=');
            if (eq < 0) continue;
            String key = ln.substring(0, eq);
            String val = ln.substring(eq + 1);
            try { apply(cur, key, val); } catch (Exception ignored) { /* tolerant */ }
        }
        return out;
    }

    /**
     * THE single write path for the profile library (one-sync rule):
     * replace the entry with the same name (case-insensitive), else append.
     * Rename = remove(oldName) + upsert(renamed) -> always a single entry.
     */
    public static boolean upsert(List<ServerProfile> list, ServerProfile p) {
        if (list == null || p == null) return false;
        String n = norm(p.name);
        if (!n.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                if (norm(list.get(i).name).equals(n)) {
                    list.set(i, p);
                    return true;
                }
            }
        }
        list.add(p);
        return true;
    }

    /** Remove the first profile whose name matches (case-insensitive). */
    public static boolean remove(List<ServerProfile> list, String name) {
        if (list == null || name == null) return false;
        String n = norm(name);
        for (int i = 0; i < list.size(); i++) {
            if (norm(list.get(i).name).equals(n)) { list.remove(i); return true; }
        }
        return false;
    }

    /** Add a profile; an existing profile with the same name is replaced. */
    public static boolean addOrReplace(List<ServerProfile> list, ServerProfile p) {
        return upsert(list, p);
    }

    /** Remove the first profile whose name matches (case-insensitive). */
    public static boolean removeByName(List<ServerProfile> list, String name) {
        return remove(list, name);
    }

    // ---------------- share wrapper (sharing to others ONLY) ----------------

    public static final String SHARE_HEADER = "GITFABLEVPN/1";

    /**
     * Export text for sharing a config with another Git Fable VPN install:
     *   GITFABLEVPN/1
     *   NOTES: <release notes, single line>   (optional)
     *   <ProfileStore serialization>
     * This is a SHARING format only — inside the app, screens sync through
     * the one ProfilePrefs store, never through export/import.
     */
    public static String toShareText(List<ServerProfile> profiles, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append(SHARE_HEADER).append('\n');
        if (notes != null && !notes.trim().isEmpty()) {
            sb.append("NOTES: ").append(notes.trim().replace('\n', ' ').replace('\r', ' ')).append('\n');
        }
        sb.append(serialize(profiles));
        return sb.toString();
    }

    /**
     * Parse share text (wrapper or a raw ProfileStore string).
     * Returns null when the text is not in store format at all.
     */
    public static List<ServerProfile> fromShareText(String text) {
        if (text == null || !text.contains(HEADER)) return null;
        return deserialize(text); // reader skips header/NOTES/junk lines
    }

    public static ServerProfile findByName(List<ServerProfile> list, String name) {
        if (list == null || name == null) return null;
        String n = norm(name);
        for (ServerProfile p : list) if (norm(p.name).equals(n)) return p;
        return null;
    }

    // ---------------- internals ----------------

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(); }
    private static String nz(String s) { return s == null ? "" : s; }

    private static void line(StringBuilder sb, String key, String val) {
        sb.append(key).append('=').append(val).append('\n');
    }

    /** Plain when single-line, else a "<key>_b64" line. */
    private static void str(StringBuilder sb, String key, String val) {
        String v = nz(val);
        if (v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            line(sb, key + "_b64", b64enc(v));
        } else {
            line(sb, key, v);
        }
    }

    /** Always Base64 (free-text fields). */
    private static void b64(StringBuilder sb, String key, String val) {
        line(sb, key + "_b64", b64enc(val));
    }

    private static String b64enc(String s) {
        try { return Base64Util.encode(nz(s).getBytes("UTF-8")); }
        catch (Exception e) { return Base64Util.encode(nz(s).getBytes()); }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static void apply(ServerProfile p, String key, String val) {
        switch (key) {
            case "protocol":
                try { p.protocol = Protocol.valueOf(val.trim()); }
                catch (Exception e) { p.protocol = null; }
                break;
            case "name": p.name = val; break;
            case "name_b64": p.name = Base64Util.decodeToString(val); break;
            case "host": p.host = val; break;
            case "port": p.port = parseInt(val, 0); break;
            case "uuid": p.uuid = val; break;
            case "password": p.password = val; break;
            case "username": p.username = val; break;
            case "alterId": p.alterId = parseInt(val, 0); break;
            case "method": p.method = val; break;
            case "network": p.network = val; break;
            case "path": p.path = val; break;
            case "hostHeader": p.hostHeader = val; break;
            case "serviceName": p.serviceName = val; break;
            case "security": p.security = val; break;
            case "sni": p.sni = val; break;
            case "allowInsecure": p.allowInsecure = "1".equals(val.trim()); break;
            case "flow": p.flow = val; break;
            case "wgPrivateKey": p.wgPrivateKey = val; break;
            case "wgAddress": p.wgAddress = val; break;
            case "wgPublicKey": p.wgPublicKey = val; break;
            case "wgPresharedKey": p.wgPresharedKey = val; break;
            case "wgAllowedIPs": p.wgAllowedIPs = val; break;
            case "wgDns": p.wgDns = val; break;
            case "wgMtu": p.wgMtu = parseInt(val, 0); break;
            case "wgReserved": p.wgReserved = val; break;
            case "hyObfs": p.hyObfs = val; break;
            case "hyObfsPassword": p.hyObfsPassword = val; break;
            case "payload_b64": p.payload = Base64Util.decodeToString(val); break;
            case "proxyHost": p.proxyHost = val; break;
            case "proxyPort": p.proxyPort = parseInt(val, 0); break;
            case "dnsNsDomain": p.dnsNsDomain = val; break;
            case "dnsPubKey": p.dnsPubKey = val; break;
            case "dnsPort": p.dnsPort = parseInt(val, 53); break;
            case "udpPsk": p.udpPsk = val; break;
            case "ovpnConfig_b64": p.ovpnConfig = Base64Util.decodeToString(val); break;
            default:
                if (key.startsWith("extra.")) {
                    int bar = val.indexOf('|');
                    if (bar >= 0) {
                        String k = Base64Util.decodeToString(val.substring(0, bar));
                        String v = Base64Util.decodeToString(val.substring(bar + 1));
                        p.extra.put(k, v);
                    }
                }
                // "extra_count" and unknown keys ignored (count is advisory)
                break;
        }
    }
}
