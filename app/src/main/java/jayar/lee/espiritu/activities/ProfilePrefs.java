package jayar.lee.espiritu.activities;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import jayar.lee.espiritu.HarliesApplication;
import jayar.lee.espiritu.protocols.ProfileStore;
import jayar.lee.espiritu.protocols.ServerProfile;

/**
 * Shared storage for the profile library, used by Home / Servers / Import
 * so all screens read and write ONE copy:
 *   HarliesApplication.getPrivateSharedPreferences()
 *     "cc_profiles_v1"      -> ProfileStore string (all profiles),
 *                              encrypted at rest via SecureStore (Keystore)
 *                              when available — see load()/save()
 *     "cc_selected_profile" -> name of the selected profile ("" = none)
 */
public final class ProfilePrefs {
    private ProfilePrefs() {}

    public static final String KEY_PROFILES = "cc_profiles_v1";
    public static final String KEY_SELECTED = "cc_selected_profile";

    private static SharedPreferences prefs() {
        return HarliesApplication.getPrivateSharedPreferences();
    }

    /**
     * Reads the profile library. The stored value is encrypted at rest
     * (SecureStore, Android Keystore, "ENC1:..." format) whenever the
     * device supports it (API 23+). MIGRATION: a legacy plaintext value is
     * read as-is and immediately re-saved encrypted.
     */
    public static List<ServerProfile> load() {
        String raw = prefs().getString(KEY_PROFILES, "");
        String plain = SecureStore.decryptFromStore(raw == null ? "" : raw);
        List<ServerProfile> out = ProfileStore.deserialize(plain);
        if (raw != null && !raw.isEmpty() && !SecureStore.isEncrypted(raw)
            && SecureStore.encryptionAvailable()) {
            try { save(out); } catch (Exception ignored) { /* keep the read result */ }
        }
        return out;
    }

    public static void save(List<ServerProfile> profiles) {
        String blob = ProfileStore.serialize(profiles);
        // Encrypted at rest when Keystore is available; on API 21-22 (or a
        // Keystore failure) SecureStore returns the plaintext for the
        // app-private prefs — an honest, documented fallback.
        prefs().edit().putString(KEY_PROFILES, SecureStore.encryptForStore(blob)).apply();
    }

    /** Add profiles (same-name entries are replaced via ProfileStore.upsert). Returns list size after. */
    public static int addAll(List<ServerProfile> incoming) {
        List<ServerProfile> cur = load();
        if (incoming != null) {
            for (ServerProfile p : incoming) ProfileStore.upsert(cur, p);
        }
        save(cur);
        return cur.size();
    }

    /**
     * Generator edit-in-place save (one-sync rule): when oldName is given and
     * the profile was renamed, the old-name entry is removed first, then the
     * profile is upserted — the store always holds exactly ONE entry.
     * The selection follows a rename.
     */
    public static void saveEdited(String oldName, ServerProfile p) {
        if (p == null) return;
        List<ServerProfile> cur = load();
        if (oldName != null && !oldName.isEmpty() && !oldName.equals(p.name)) {
            ProfileStore.remove(cur, oldName);
        }
        ProfileStore.upsert(cur, p);
        save(cur);
        if (oldName != null && oldName.equals(selectedName())) setSelectedName(p.name);
    }

    /** Delete by name; clears the selection if the deleted profile was selected. */
    public static boolean delete(String name) {
        List<ServerProfile> cur = load();
        boolean removed = ProfileStore.remove(cur, name);
        if (removed) {
            save(cur);
            if (name != null && name.equals(selectedName())) setSelectedName("");
        }
        return removed;
    }

    // ---------------- subscription (modern auto-update) ----------------

    public static final String KEY_SUB_URL = "cc_sub_url";
    public static final String KEY_SUB_AUTO = "cc_sub_auto";

    public static String subUrl() {
        String s = prefs().getString(KEY_SUB_URL, "");
        return s == null ? "" : s;
    }

    public static void setSubUrl(String url) {
        prefs().edit().putString(KEY_SUB_URL, url == null ? "" : url.trim()).apply();
    }

    public static boolean subAutoEnabled() {
        return prefs().getBoolean(KEY_SUB_AUTO, false); // default OFF (her toggle rule)
    }

    public static void setSubAuto(boolean on) {
        prefs().edit().putBoolean(KEY_SUB_AUTO, on).apply();
    }

    public static String selectedName() {
        String s = prefs().getString(KEY_SELECTED, "");
        return s == null ? "" : s;
    }

    public static void setSelectedName(String name) {
        prefs().edit().putString(KEY_SELECTED, name == null ? "" : name).apply();
    }

    public static ServerProfile selected() {
        String n = selectedName();
        if (n.isEmpty()) return null;
        return ProfileStore.findByName(load(), n);
    }

    /** First profile in the store (helper for defaults). */
    public static ServerProfile firstOrNull(List<ServerProfile> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    public static List<ServerProfile> emptyIfNull(List<ServerProfile> list) {
        return list == null ? new ArrayList<ServerProfile>() : list;
    }
}
