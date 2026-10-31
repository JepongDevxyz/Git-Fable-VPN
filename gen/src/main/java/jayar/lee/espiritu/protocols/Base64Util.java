package jayar.lee.espiritu.protocols;

/** Base64 (standard + URL-safe, padded or not). Pure Java: works on minSdk 21
 *  (java.util.Base64 needs API 26, android.util.Base64 is Android-only). */
public final class Base64Util {
    private Base64Util() {}

    private static final String ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    public static byte[] decode(String in) {
        if (in == null) return new byte[0];
        String s = in.trim().replace('-', '+').replace('_', '/').replaceAll("\\s", "");
        int pad = 0;
        while (s.endsWith("=")) { s = s.substring(0, s.length() - 1); pad++; }
        int[] rev = new int[128];
        java.util.Arrays.fill(rev, -1);
        for (int i = 0; i < ALPHABET.length(); i++) rev[ALPHABET.charAt(i)] = i;
        int len = s.length();
        int outLen = (len * 6) / 8;
        byte[] out = new byte[outLen + 3];
        int buf = 0, bits = 0, o = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c >= 128 || rev[c] < 0) continue;
            buf = (buf << 6) | rev[c];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) ((buf >> bits) & 0xFF);
            }
        }
        byte[] r = new byte[o];
        System.arraycopy(out, 0, r, 0, o);
        return r;
    }

    public static String decodeToString(String in) {
        try { return new String(decode(in), "UTF-8"); }
        catch (Exception e) { return new String(decode(in)); }
    }

    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 3) {
            int b0 = data[i] & 0xFF;
            int b1 = i + 1 < data.length ? data[i + 1] & 0xFF : 0;
            int b2 = i + 2 < data.length ? data[i + 2] & 0xFF : 0;
            sb.append(ALPHABET.charAt(b0 >> 2));
            sb.append(ALPHABET.charAt(((b0 & 3) << 4) | (b1 >> 4)));
            sb.append(i + 1 < data.length ? ALPHABET.charAt(((b1 & 15) << 2) | (b2 >> 6)) : '=');
            sb.append(i + 2 < data.length ? ALPHABET.charAt(b2 & 63) : '=');
        }
        return sb.toString();
    }

    public static String encodeUrlSafe(String text) {
        try {
            return encode(text.getBytes("UTF-8")).replace('+', '-').replace('/', '_').replace("=", "");
        } catch (Exception e) { return ""; }
    }
}
