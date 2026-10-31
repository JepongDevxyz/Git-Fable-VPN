package jayar.lee.espiritu.protocols;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jayar.lee.espiritu.HarliesApplication;
import jayar.lee.espiritu.activities.ProfilePrefs;

/**
 * Online auto-updating server feed (her order, 2026-11-01): servers must
 * update online in the client automatically — like a GitHub raw link —
 * with NO manual import. The admin publishes servers/servers.txt once
 * (from the Git Fable Generator's "Publish feed"); every installed client
 * refreshes its Servers list by itself.
 *
 * Feed format: MIXED — the standard subscription lines PLUS a
 * full-fidelity profile-store block:
 *   - share-link lines (vless://, vmess://, trojan://, ss://, hy2://,
 *     wireguard://), read by ShareLinkParser.parseMany;
 *   - one ProfileStore block (the GFVPN-PROFILES/1 serialization, read
 *     by ProfileStore.fromShareText) carrying EVERY profile — including
 *     the injector / OpenVPN types (SSH Direct, SSH over WebSocket,
 *     SlowDNS, UDP Custom, OpenVPN, L2TP, IKEv2, PPTP) that have no
 *     share-link form and would otherwise be silently dropped.
 * Lines starting with # are comments. Merge order: share-link profiles
 * first, store-block profiles LAST, so on a name collision the store
 * version wins (it carries payload / SNI / userpass that links lose).
 *
 * Empty-feed rule: a fetched text that yields ZERO profiles AND contains
 * no store header is treated as a failed/garbage fetch — applyFeed
 * returns a no-op Result (flagged {@code noOp}) and fetchAndApply neither
 * saves nor stamps last_fetch, so a bad response can never wipe the
 * user's servers. A text that DOES contain the store header is a
 * deliberate publish: an empty store block legitimately clears all
 * feed-owned profiles.
 *
 * Replace semantics, scoped to FEED-OWNED profiles only (their normalized
 * names are tracked in the cc_feed_ids pref): a refresh removes feed
 * profiles that vanished from the feed and upserts the current ones.
 * Profiles the user imported manually are never removed. One exception,
 * by store design (ProfileStore.upsert is name-keyed): if a manual
 * profile has the SAME name as a feed profile, the feed upsert wins and
 * that name becomes feed-owned.
 *
 * The pure core (applyFeed / shouldFetch / fetch) is Android-free and
 * unit-tested; the Android glue (prefs + ProfilePrefs store) is thin.
 */
public final class FeedUpdater {
    private FeedUpdater() {}

    /** The published feed: servers/servers.txt on the repo's main branch. */
    public static final String DEFAULT_FEED_URL =
        "https://raw.githubusercontent.com/JepongDevxyz/Git-Fable-VPN/main/servers/servers.txt";

    // Keys in the client's private prefs (HarliesApplication private prefs,
    // the same store ProfilePrefs uses for cc_profiles_v1 / cc_sub_*).
    public static final String KEY_FEED_URL = "cc_feed_url";           // override; "" = default
    public static final String KEY_FEED_AUTO = "cc_feed_auto";         // boolean
    public static final String KEY_FEED_LAST_FETCH = "cc_feed_last_fetch"; // epoch ms, success only
    public static final String KEY_FEED_IDS = "cc_feed_ids";           // StringSet of normalized names

    /** Refresh at most every 6 hours. */
    public static final long FETCH_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    // ---------------- result ----------------

    public static final class Result {
        /** The store after applying (the same list instance passed in, mutated). */
        public final List<ServerProfile> profiles;
        /** Normalized names now owned by the feed — persist to cc_feed_ids. */
        public final Set<String> feedOwned;
        public final int added, updated, removed;
        /** True when nothing was applied (empty/garbage feed guard). */
        public final boolean noOp;
        Result(List<ServerProfile> profiles, Set<String> feedOwned, int a, int u, int r,
               boolean noOp) {
            this.profiles = profiles; this.feedOwned = feedOwned;
            this.added = a; this.updated = u; this.removed = r;
            this.noOp = noOp;
        }
    }

    // ---------------- pure core ----------------

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(); }

    /**
     * Apply feed text to a profile list with feed-scoped replace semantics.
     * Mutates {@code store} in place; {@code previousFeedOwned} holds the
     * normalized names recorded by the previous successful apply.
     *
     * The feed is MIXED: share-link lines are parsed first
     * (ShareLinkParser.parseMany), then — when the text contains the
     * ProfileStore header — the store block is parsed with
     * ProfileStore.fromShareText (the same parse path ImportHelper uses
     * for share text) and appended LAST, so the full-fidelity store
     * version wins on name collisions when upserted.
     *
     * Empty-feed guard: zero parsed profiles AND no store header in the
     * text -> no-op Result (store and owned set returned untouched).
     * A store header with an empty block is a legitimate clear-all.
     */
    public static Result applyFeed(String feedText, List<ServerProfile> store,
                                   Set<String> previousFeedOwned) {
        if (store == null) store = new ArrayList<>();
        if (feedText == null) feedText = "";
        boolean hasStoreBlock = feedText.contains(ProfileStore.HEADER);
        List<ServerProfile> parsed =
            new ArrayList<>(ShareLinkParser.parseMany(feedText));
        if (hasStoreBlock) {
            List<ServerProfile> stored = ProfileStore.fromShareText(feedText);
            if (stored != null) parsed.addAll(stored);
        }
        if (parsed.isEmpty() && !hasStoreBlock) {
            Set<String> keep = previousFeedOwned == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<>(previousFeedOwned);
            return new Result(store, keep, 0, 0, 0, true);
        }
        Set<String> newOwned = new LinkedHashSet<>();
        for (ServerProfile p : parsed) {
            // Share links may carry no #name; give them a stable generated
            // name (protocol + endpoint) so upsert/feed-tracking can key
            // on it instead of appending a duplicate on every refresh.
            if (p.name == null || p.name.trim().isEmpty()) p.name = p.displayName();
            newOwned.add(norm(p.name));
        }
        int removed = 0;
        if (previousFeedOwned != null && !previousFeedOwned.isEmpty()) {
            for (int i = store.size() - 1; i >= 0; i--) {
                String n = norm(store.get(i).name);
                if (previousFeedOwned.contains(n) && !newOwned.contains(n)) {
                    store.remove(i);
                    removed++;
                }
            }
        }
        int added = 0, updated = 0;
        for (ServerProfile p : parsed) {
            boolean existed = ProfileStore.findByName(store, p.name) != null;
            ProfileStore.upsert(store, p);
            if (existed) updated++; else added++;
        }
        return new Result(store, newOwned, added, updated, removed, false);
    }

    /** Pure throttle check: auto-update on AND the last success is > 6 h old. */
    public static boolean shouldFetch(long nowMs, boolean autoEnabled, long lastFetchMs) {
        return autoEnabled && nowMs - lastFetchMs > FETCH_INTERVAL_MS;
    }

    /**
     * GET a feed URL (10 s timeouts). Throws on any non-2xx response so a
     * missing/garbage feed can never reach applyFeed and wipe feed-owned
     * profiles. Callers run this on a background thread.
     */
    public static String fetch(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlText.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
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

    // ---------------- Android glue (client private prefs) ----------------

    private static SharedPreferences prefs(Context ctx) {
        // Same underlying prefs file ProfilePrefs uses: HarliesApplication
        // opens getSharedPreferences(PREFS_GERAL, MODE_PRIVATE) on the app
        // context, and SharedPreferences are per-name singletons per app.
        Context app = ctx.getApplicationContext();
        return app.getSharedPreferences(HarliesApplication.PREFS_GERAL, Context.MODE_PRIVATE);
    }

    public static String feedUrl(Context ctx) {
        String s = prefs(ctx).getString(KEY_FEED_URL, "");
        return (s == null || s.trim().isEmpty()) ? DEFAULT_FEED_URL : s.trim();
    }

    /**
     * DEFAULT = ON. This is the one deliberate exception to her
     * "new toggles default OFF" rule: she explicitly ordered this feature
     * as "servers update online automatically, no manual import", which
     * only works if the toggle starts ON. Users can still turn it off.
     */
    public static boolean isAutoEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_FEED_AUTO, true);
    }

    public static void setAutoEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_FEED_AUTO, on).apply();
    }

    public static boolean shouldFetchNow(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        return shouldFetch(System.currentTimeMillis(),
            sp.getBoolean(KEY_FEED_AUTO, true),
            sp.getLong(KEY_FEED_LAST_FETCH, 0L));
    }

    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    /**
     * Fetch the feed and apply it to the real store. BLOCKING (network +
     * store I/O) — call from a background thread only. Single-flight:
     * returns false immediately if another fetch is already running.
     * Any failure (offline, HTTP error, bad body) is a silent no-op and
     * last_fetch is stamped ONLY on success, so offline users keep their
     * existing servers untouched.
     */
    public static boolean fetchAndApply(Context ctx) {
        if (!IN_FLIGHT.compareAndSet(false, true)) return false;
        try {
            String body = fetch(feedUrl(ctx));
            if (body == null || body.trim().isEmpty()) return false;
            SharedPreferences sp = prefs(ctx);
            Set<String> owned = sp.getStringSet(KEY_FEED_IDS, null);
            List<ServerProfile> store = ProfilePrefs.load();
            Result r = applyFeed(body, store,
                owned == null ? new LinkedHashSet<String>() : new LinkedHashSet<>(owned));
            // Garbage/empty body that parsed to nothing: no save, no
            // last_fetch stamp — the existing servers stay untouched.
            if (r.noOp) return false;
            ProfilePrefs.save(r.profiles);
            sp.edit()
                .putStringSet(KEY_FEED_IDS, r.feedOwned)
                .putLong(KEY_FEED_LAST_FETCH, System.currentTimeMillis())
                .apply();
            return true;
        } catch (Exception e) {
            return false; // offline / bad feed: silent no-op by design
        } finally {
            IN_FLIGHT.set(false);
        }
    }
}
