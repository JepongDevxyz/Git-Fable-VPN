package jayar.lee.espiritu.protocols;

/**
 * Deep-link import codec: gitfablevpn://import?d=<base64url payload>.
 * The payload is the share text (ProfileStore wrapper) or a GFVENC1
 * encrypted text — the link is a SHARING channel to other installs,
 * never the app's internal sync path.
 * Pure Java so the round-trip unit-tests with plain javac.
 */
public final class DeepLinkCodec {
    private DeepLinkCodec() {}

    public static final String SCHEME = "gitfablevpn";
    public static final String HOST = "import";
    public static final String PREFIX = SCHEME + "://" + HOST + "?d=";

    public static String encode(String text) {
        try {
            return Base64Util.encode((text == null ? "" : text).getBytes("UTF-8"))
                .replace('+', '-').replace('/', '_').replace("=", "");
        } catch (Exception e) { return ""; }
    }

    public static String decode(String data) {
        if (data == null) return "";
        return Base64Util.decodeToString(data.trim());
    }

    public static String buildLink(String shareText) {
        return PREFIX + encode(shareText);
    }

    /** Extract and decode ?d= from a gitfablevpn://import link; "" when absent. */
    public static String extractPayload(String link) {
        if (link == null) return "";
        String l = link.trim();
        if (!l.startsWith(SCHEME + "://" + HOST)) return "";
        int i = l.indexOf("?d=");
        if (i < 0) return "";
        String d = l.substring(i + 3);
        int amp = d.indexOf('&');
        if (amp >= 0) d = d.substring(0, amp);
        return decode(d);
    }
}
