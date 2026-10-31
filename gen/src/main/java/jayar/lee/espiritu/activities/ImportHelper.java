package jayar.lee.espiritu.activities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import jayar.lee.espiritu.protocols.ConfigCrypto;
import jayar.lee.espiritu.protocols.ProfileStore;
import jayar.lee.espiritu.protocols.ServerProfile;
import jayar.lee.espiritu.protocols.ShareLinkParser;

/**
 * The ONE import pipeline, shared by ImportActivity (paste / clipboard /
 * deep link / subscription fetch) and ServersActivity (subscription
 * auto-refresh). Accepted inputs, in order:
 *   1. GFVENC1: encrypted text  -> decrypted with the given password
 *      (empty password or wrong password -> no profiles, never garbage)
 *   2. GITFABLEVPN/1 wrapper or raw ProfileStore text -> ProfileStore
 *   3. Anything else -> ShareLinkParser.parseMany (share links, base64
 *      subscription bodies, WireGuard .conf, .ovpn)
 * Imported profiles land in the same single store (ProfilePrefs) via
 * ProfileStore.upsert — importing never creates a second copy.
 */
public final class ImportHelper {
    private ImportHelper() {}

    public static List<ServerProfile> parse(String text, String password) {
        List<ServerProfile> out = new ArrayList<>();
        if (text == null) return out;
        String t = text.trim();
        if (t.isEmpty()) return out;
        try {
            if (ConfigCrypto.isEncrypted(t)) {
                if (password == null || password.isEmpty()) return out;
                String plain = ConfigCrypto.decrypt(t, password.toCharArray());
                return parse(plain, "");
            }
        } catch (Exception e) {
            return out; // wrong password / tampered -> import nothing
        }
        List<ServerProfile> fromStore = ProfileStore.fromShareText(t);
        if (fromStore != null) return fromStore;
        return ShareLinkParser.parseMany(t);
    }

    /** Parse + save into the one store; returns how many profiles were parsed. */
    public static int importText(String text, String password) {
        List<ServerProfile> parsed = parse(text, password);
        if (!parsed.isEmpty()) {
            ProfilePrefs.addAll(parsed);
            if (ProfilePrefs.selectedName().isEmpty()) {
                ProfilePrefs.setSelectedName(parsed.get(0).name);
            }
        }
        return parsed.size();
    }

    /** GET a subscription URL (background thread callers only). 15s timeouts, follows redirects. */
    public static String fetchUrl(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "GitFableVPN/1.0");
            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in == null) throw new java.io.IOException("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
