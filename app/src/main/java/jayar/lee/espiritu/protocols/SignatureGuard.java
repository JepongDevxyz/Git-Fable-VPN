package jayar.lee.espiritu.protocols;

import java.security.MessageDigest;

/**
 * Anti-mod signature check — pure, testable core.
 * The runtime wrapper (activities/SignatureCheck) reads the app's own
 * signing certificate bytes from PackageManager and asks this class
 * whether they match the expected release certificate fingerprint.
 *
 * Honest limit: client-side checks raise the bar against casual repacking
 * ("mod" APKs with stolen server configs); they are not unbreakable. The
 * real protection is that profiles are encrypted at rest and exports are
 * AES-GCM encrypted — and no server credentials are baked into the app.
 */
public final class SignatureGuard {
    private SignatureGuard() {}

    /** Lowercase hex SHA-256 of the given bytes ("" on failure). */
    public static String sha256Hex(byte[] data) {
        if (data == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * True when sha256(certBytes) equals expectedHex. The expected value
     * may be written with colons/spaces and in any case (keytool style).
     */
    public static boolean matchesExpected(byte[] certBytes, String expectedHex) {
        if (certBytes == null || certBytes.length == 0 || expectedHex == null) return false;
        String want = expectedHex.replace(":", "").replace(" ", "").trim().toLowerCase();
        if (want.isEmpty()) return false;
        return sha256Hex(certBytes).equals(want);
    }
}
