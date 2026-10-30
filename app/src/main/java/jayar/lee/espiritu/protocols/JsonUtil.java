package jayar.lee.espiritu.protocols;

/** Minimal flat-JSON reader for VMess share-link objects. Not a general parser. */
public final class JsonUtil {
    private JsonUtil() {}

    public static String getString(String json, String key) {
        if (json == null) return "";
        // find "key"
        String pat = "\"" + key + "\"";
        int i = json.indexOf(pat);
        if (i < 0) return "";
        int colon = json.indexOf(':', i + pat.length());
        if (colon < 0) return "";
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        if (j >= json.length()) return "";
        if (json.charAt(j) == '"') {
            StringBuilder sb = new StringBuilder();
            j++;
            while (j < json.length()) {
                char c = json.charAt(j);
                if (c == '\\' && j + 1 < json.length()) { sb.append(json.charAt(j + 1)); j += 2; continue; }
                if (c == '"') break;
                sb.append(c); j++;
            }
            return sb.toString();
        }
        int k = j;
        while (k < json.length() && ",}] \t\r\n".indexOf(json.charAt(k)) < 0) k++;
        return json.substring(j, k).trim();
    }

    public static int getInt(String json, String key, int def) {
        String v = getString(json, key);
        try { return v.isEmpty() ? def : Integer.parseInt(v); } catch (Exception e) { return def; }
    }

    /** Escape a string for inclusion in generated JSON. */
    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
