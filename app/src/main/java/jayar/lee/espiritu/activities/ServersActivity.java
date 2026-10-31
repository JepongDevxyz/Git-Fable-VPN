package jayar.lee.espiritu.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.protocols.FeedUpdater;
import jayar.lee.espiritu.protocols.ProtocolFamily;
import jayar.lee.espiritu.protocols.ServerProfile;
import jayar.lee.espiritu.protocols.TunnelSettings;

/**
 * Servers / Locations screen (Nexus reference shape):
 *  - Real search filter over the saved profiles
 *  - Rows: name, host:port, protocol label chip (NO flag emoji — her rule)
 *  - Selected row = cc_card_selected outline; tap selects (persisted via
 *    ProfilePrefs "cc_selected_profile"); long-press deletes immediately
 *    (no dialogs — her rule) with a branded toast naming the profile
 *  - Import button opens ImportActivity; the list reloads on resume so
 *    freshly imported profiles appear without restarting the screen.
 *  - Protocol organization (her order, 2026-11-01): the Settings default
 *    protocol filters this list by FAMILY via ProtocolFamily — a specific
 *    selection shows only that family (e.g. OpenVPN shows UDP+TCP),
 *    Auto/unset shows everything. Search narrows WITHIN the filtered
 *    set; tap/long-press act on the underlying profile objects.
 * Data: ProfilePrefs / ProfileStore in HarliesApplication private prefs.
 */
public class ServersActivity extends Activity {

    private LinearLayout listBox;
    private EditText searchBox;
    private TextView filterLine;
    private List<ServerProfile> profiles;
    private String activeSelection = "AUTO";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_servers);

        listBox = findViewById(R.id.cc_srv_list);
        searchBox = findViewById(R.id.cc_srv_search);
        filterLine = findViewById(R.id.cc_srv_filter);

        findViewById(R.id.cc_srv_back).setOnClickListener(v -> finish());
        findViewById(R.id.cc_srv_import).setOnClickListener(v ->
            startActivity(new Intent(this, ImportActivity.class)));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { buildRows(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private static long lastSubFetchMs = 0;

    @Override protected void onResume() {
        super.onResume();
        // ONE SYNC: always reload from the single store — Import additions
        // and deletions elsewhere show up immediately.
        profiles = ProfilePrefs.load();
        SharedPreferences sp = getSharedPreferences(NexusSettingsActivity.PREFS, MODE_PRIVATE);
        activeSelection = sp.getString(TunnelSettings.KEY_DEFAULT_PROTOCOL, "AUTO");
        if (filterLine != null) {
            boolean all = ProtocolFamily.familyForSelection(activeSelection) == null;
            filterLine.setText(all
                ? "Showing: All protocols"
                : "Showing: " + ProtocolFamily.selectionLabel(activeSelection) + " only");
        }
        buildRows(searchBox.getText().toString());
        maybeAutoRefreshSubscription();
        maybeAutoRefreshFeed();
    }

    /** Online server feed (her order, 2026-11-01): when a feed refresh is
     *  due (auto-update ON + last success > 6 h ago), fetch it here too so
     *  the list shows fresh feed servers immediately. Background thread,
     *  silent on failure; FeedUpdater.fetchAndApply is single-flight. */
    private void maybeAutoRefreshFeed() {
        if (!FeedUpdater.shouldFetchNow(this)) return;
        new Thread(() -> {
            if (FeedUpdater.fetchAndApply(ServersActivity.this)) {
                runOnUiThread(() -> {
                    profiles = ProfilePrefs.load();
                    buildRows(searchBox.getText().toString());
                });
            }
        }).start();
    }

    /** Modern auto-update: when the user turned it ON in Import, refresh from
     *  the saved subscription URL on open (throttled to once per 30 s). */
    private void maybeAutoRefreshSubscription() {
        if (!ProfilePrefs.subAutoEnabled()) return;
        final String url = ProfilePrefs.subUrl();
        if (url.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastSubFetchMs < 30_000) return;
        lastSubFetchMs = now;
        new Thread(() -> {
            try {
                String body = ImportHelper.fetchUrl(url);
                final int n = ImportHelper.importText(body, "");
                if (n > 0) runOnUiThread(() -> {
                    profiles = ProfilePrefs.load();
                    buildRows(searchBox.getText().toString());
                    brandedToast("Subscription updated: " + n + " profiles");
                });
            } catch (Exception ignored) {
                // offline / bad URL: keep showing the stored list, no nagging
            }
        }).start();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildRows(String filter) {
        listBox.removeAllViews();
        if (profiles == null) profiles = ProfilePrefs.load();
        String f = filter == null ? "" : filter.trim().toLowerCase();
        String selected = ProfilePrefs.selectedName();
        // Protocol-family filter first; search narrows within that set.
        List<ServerProfile> visible = ProtocolFamily.filter(profiles, activeSelection);
        int shown = 0;
        for (final ServerProfile p : visible) {
            String hay = (p.displayName() + " " + p.host + " " + p.port + " "
                + (p.protocol == null ? "" : p.protocol.label)).toLowerCase();
            if (!f.isEmpty() && !hay.contains(f)) continue;
            shown++;
            boolean isSel = p.name != null && p.name.equals(selected);

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackgroundResource(isSel ? R.drawable.cc_card_selected : R.drawable.cc_card_plain);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            item.setLayoutParams(lp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            texts.setLayoutParams(tlp);
            TextView title = new TextView(this);
            title.setText(p.displayName());
            title.setTextSize(15);
            TextView sub = new TextView(this);
            sub.setText(p.host + ":" + p.port);
            sub.setTextSize(12);
            texts.addView(title);
            texts.addView(sub);
            item.addView(texts);

            TextView chip = new TextView(this);
            chip.setText(p.protocol == null ? "?" : p.protocol.label);
            chip.setTextSize(11);
            chip.setBackgroundResource(R.drawable.cc_chip);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            item.addView(chip);

            item.setOnClickListener(v -> {
                ProfilePrefs.setSelectedName(p.name);
                buildRows(searchBox.getText().toString());
                brandedToast(p.displayName() + " selected");
            });
            item.setOnLongClickListener(v -> {
                String name = p.displayName();
                ProfilePrefs.delete(p.name);
                profiles = ProfilePrefs.load();
                buildRows(searchBox.getText().toString());
                brandedToast("Deleted " + name);
                return true;
            });
            listBox.addView(item);
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            String label = ProtocolFamily.selectionLabel(activeSelection);
            boolean filtered = ProtocolFamily.familyForSelection(activeSelection) != null;
            if (profiles.isEmpty()) {
                empty.setText("No servers yet. Tap Import to add share links, a WireGuard .conf, an .ovpn config, or use Jepong VPS Quick-Add.");
            } else if (visible.isEmpty() && filtered) {
                empty.setText("No " + label + " servers yet. Add one via Import, or set the protocol to Auto in Settings to see all servers.");
            } else {
                empty.setText("No servers match your search.");
            }
            empty.setTextSize(13);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            listBox.addView(empty);
        }
    }

    /** Bottom-center branded toast (her UI rule). */
    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(28));
        t.show();
    }
}
