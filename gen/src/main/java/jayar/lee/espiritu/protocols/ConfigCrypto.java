package jayar.lee.espiritu.protocols;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Password-protected config export ("anti-decryption" for shared configs):
 * PBKDF2 key derivation + AES-256-GCM authenticated encryption.
 *
 * Format (single line):
 *   GFVENC1:v1.<kdfId>.<iterations>.<saltB64url>.<ivB64url>.<cipherB64url>
 *     kdfId 1 = PBKDF2WithHmacSHA256 (API 26+ / JVM), 2 = PBKDF2WithHmacSHA1
 *     (fallback for older Android where the SHA256 variant is missing)
 *     iterations = 120,000; salt = 16 random bytes; IV = 12 random bytes;
 *     GCM tag = 128 bits. Random salt+IV => two encryptions of the same
 *     plaintext always differ; any tamper or wrong password fails the GCM
 *     tag check and decrypt() throws — it never returns garbage.
 *
 * Pure Java (javax.crypto) so it unit-tests with plain javac. This is the
 * crypto for EXPORTS shared with other people; the app's own stored
 * profiles are protected separately (Keystore, SecureStore).
 */
public final class ConfigCrypto {
    private ConfigCrypto() {}

    public static final String PREFIX = "GFVENC1:";
    public static final int ITERATIONS = 120_000;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int KEY_BITS = 256;
    private static final int TAG_BITS = 128;
    private static final int KDF_SHA256 = 1;
    private static final int KDF_SHA1 = 2;

    public static boolean isEncrypted(String text) {
        return text != null && text.trim().startsWith(PREFIX);
    }

    public static String encrypt(String plain, char[] password) throws Exception {
        if (password == null || password.length == 0) throw new IllegalArgumentException("password required");
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[SALT_LEN]; rnd.nextBytes(salt);
        byte[] iv = new byte[IV_LEN]; rnd.nextBytes(iv);
        int kdfId;
        SecretKey key;
        try {
            key = derive(password, salt, ITERATIONS, KDF_SHA256);
            kdfId = KDF_SHA256;
        } catch (Exception e) {
            key = derive(password, salt, ITERATIONS, KDF_SHA1);
            kdfId = KDF_SHA1;
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = c.doFinal((plain == null ? "" : plain).getBytes("UTF-8"));
        return PREFIX + "v1." + kdfId + "." + ITERATIONS + "."
            + b64url(salt) + "." + b64url(iv) + "." + b64url(ct);
    }

    public static String decrypt(String text, char[] password) throws Exception {
        if (password == null || password.length == 0) throw new IllegalArgumentException("password required");
        String t = text == null ? "" : text.trim();
        if (!t.startsWith(PREFIX)) throw new IllegalArgumentException("not a GFVENC1 text");
        String[] parts = t.substring(PREFIX.length()).split("\\.");
        if (parts.length != 6 || !"v1".equals(parts[0])) throw new IllegalArgumentException("bad GFVENC1 format");
        int kdfId = Integer.parseInt(parts[1]);
        int iterations = Integer.parseInt(parts[2]);
        if (iterations < 10_000 || iterations > 5_000_000) throw new IllegalArgumentException("bad iteration count");
        byte[] salt = Base64Util.decode(parts[3]);
        byte[] iv = Base64Util.decode(parts[4]);
        byte[] ct = Base64Util.decode(parts[5]);
        SecretKey key = derive(password, salt, iterations, kdfId);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return new String(c.doFinal(ct), "UTF-8"); // AEADBadTagException on wrong pass / tamper
    }

    private static SecretKey derive(char[] password, byte[] salt, int iterations, int kdfId) throws Exception {
        String algo = kdfId == KDF_SHA1 ? "PBKDF2WithHmacSHA1" : "PBKDF2WithHmacSHA256";
        SecretKeyFactory f = SecretKeyFactory.getInstance(algo);
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        byte[] raw = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(raw, "AES");
    }

    private static String b64url(byte[] data) {
        return Base64Util.encode(data).replace('+', '-').replace('/', '_').replace("=", "");
    }
}
