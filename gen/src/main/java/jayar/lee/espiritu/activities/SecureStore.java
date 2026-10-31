package jayar.lee.espiritu.activities;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import jayar.lee.espiritu.protocols.Base64Util;

/**
 * Encryption at rest for the profile store (anti server-theft): the
 * cc_profiles_v1 value is stored as
 *   ENC1:<base64 iv>:<base64 ciphertext>
 * using AES-256-GCM with a key held in the Android Keystore
 * (alias "gitfable_profile_key", created on first use, API 23+).
 *
 * Honest fallbacks:
 *  - API 21-22 has no KeyGenParameterSpec -> the value stays plaintext in
 *    the app's PRIVATE preferences (still not world-readable, but not
 *    encrypted). Noted, not hidden.
 *  - Any Keystore failure -> plaintext fallback rather than losing the
 *    user's server list (availability first), and decrypt() of a legacy
 *    plaintext value returns it as-is so ProfilePrefs can migrate it.
 *
 * The pure crypto core (encryptWithKey / decryptWithKey with a caller
 * supplied SecretKey) is unit-tested in the JVM harness; the Keystore
 * wrapper is compile-checked only.
 */
public final class SecureStore {
    private SecureStore() {}

    public static final String PREFIX = "ENC1:";
    private static final String KEY_ALIAS = "gitfable_profile_key";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    // ---------------- pure crypto core (unit-tested) ----------------

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public static String encryptWithKey(SecretKey key, String plain) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = c.doFinal((plain == null ? "" : plain).getBytes("UTF-8"));
        return PREFIX + Base64Util.encode(iv) + ":" + Base64Util.encode(ct);
    }

    public static String decryptWithKey(SecretKey key, String stored) throws Exception {
        if (!isEncrypted(stored)) throw new IllegalArgumentException("not ENC1");
        String body = stored.substring(PREFIX.length());
        int sep = body.indexOf(':');
        if (sep < 0) throw new IllegalArgumentException("bad ENC1 format");
        byte[] iv = Base64Util.decode(body.substring(0, sep));
        byte[] ct = Base64Util.decode(body.substring(sep + 1));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return new String(c.doFinal(ct), "UTF-8");
    }

    // ---------------- Keystore wrapper (API 23+) ----------------

    private static SecretKey keystoreKey() {
        if (Build.VERSION.SDK_INT < 23) return null; // honest API 21-22 fallback
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) {
                return (SecretKey) ks.getKey(KEY_ALIAS, null);
            }
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
            return kg.generateKey();
        } catch (Exception e) {
            return null;
        }
    }

    /** True when at-rest encryption is actually available on this device. */
    public static boolean encryptionAvailable() {
        return keystoreKey() != null;
    }

    /** Encrypt for storage; returns the plaintext unchanged when unavailable. */
    public static String encryptForStore(String plain) {
        SecretKey key = keystoreKey();
        if (key == null) return plain == null ? "" : plain;
        try { return encryptWithKey(key, plain); }
        catch (Exception e) { return plain == null ? "" : plain; }
    }

    /**
     * Decrypt a stored value. Legacy plaintext (no ENC1: prefix) is
     * returned unchanged — ProfilePrefs.load() re-saves it encrypted
     * (migration). Returns "" if an encrypted value cannot be read.
     */
    public static String decryptFromStore(String stored) {
        if (stored == null) return "";
        if (!isEncrypted(stored)) return stored;
        SecretKey key = keystoreKey();
        if (key == null) return "";
        try { return decryptWithKey(key, stored); }
        catch (Exception e) { return ""; }
    }
}
