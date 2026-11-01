package jayar.lee.espiritu.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.protocols.FeedUpdater;
import jayar.lee.espiritu.protocols.Protocol;
import jayar.lee.espiritu.protocols.TunnelSettings;
import jayar.lee.espiritu.theme.ThemeSettings;

/**
 * Nexus-style Settings screen (her reference, right screen):
 *  - Appearance (FIRST section, her order 2026-11-01): System / Dark Mode /
 *    Light Mode radio rows; the choice persists in ThemeSettings and is
 *    applied app-wide at once via AppCompatDelegate (DayNight sync).
 *  - VPN Protocols: search + radio list; selection persists as the default
 *    protocol (TunnelSettings.KEY_DEFAULT_PROTOCOL) and is read by the
 *    Home protocol chip / new-profile flow. REAL selection, not decoration.
 *  - Quick Toggles: Kill Switch, Split Tunneling, Double VPN, DNS Leak
 *    Protection — each Switch writes its TunnelSettings key immediately;
 *    TunnelSettings.buildSingBoxConfig() consumes them for real
 *    (strict_route / exclude_package / detour / hijack-dns).
 *  - "Auto-update servers" binds to FeedUpdater (cc_feed_auto in the
 *    client private prefs, NOT this screen's settings file).
 * Defaults are OFF on first run (her standing rule) — with ONE deliberate
 * exception: "Auto-update servers" defaults ON, because she explicitly
 * ordered the feed to update by itself with no manual import.
 */
public class NexusSettingsActivity extends AppCompatActivity {

    public static final String PREFS = "gitfablevpn_settings";

    private SharedPreferences prefs;
    private LinearLayout themeList;
    private LinearLayout protocolList;
    private LinearLayout toggleList;
    private String selectedProtocol;
    private String selectedTheme;

    private static class ThemeRow {
        final String id, title, desc;
        ThemeRow(String id, String title, String desc) { this.id = id; this.title = title; this.desc = desc; }
    }

    // Appearance choices (her order, 2026-11-01): System / Dark / Light,
    // persisted + applied app-wide by ThemeSettings.
    private static final ThemeRow[] THEME_ROWS = {
        new ThemeRow(ThemeSettings.MODE_SYSTEM, "System", "Follow device setting"),
        new ThemeRow(ThemeSettings.MODE_DARK, "Dark Mode", "Always use the dark theme"),
        new ThemeRow(ThemeSettings.MODE_LIGHT, "Light Mode", "Always use the light theme"),
    };

    private static class ProtoRow {
        final String id, title, desc;
        ProtoRow(String id, String title, String desc) { this.id = id; this.title = title; this.desc = desc; }
    }

    private static final ProtoRow[] ROWS = {
        new ProtoRow("AUTO", "Auto (Recommended)", "Automatically chooses the best protocol"),
        new ProtoRow(Protocol.WIREGUARD.name(), "WireGuard", "Ultra-fast, secure, and modern"),
        new ProtoRow(Protocol.OPENVPN_UDP.name(), "OpenVPN (UDP)", "Ideal for speed and security"),
        new ProtoRow(Protocol.OPENVPN_TCP.name(), "OpenVPN (TCP)", "Ideal for unstable connections"),
        new ProtoRow(Protocol.VLESS_WS.name(), "VLESS (WS / gRPC / XHTTP)", "Xray over TLS 443"),
        new ProtoRow(Protocol.VMESS_WS.name(), "VMess (WS)", "Xray classic"),
        new ProtoRow(Protocol.TROJAN_WS.name(), "Trojan (WS)", "TLS-native"),
        new ProtoRow(Protocol.HYSTERIA2.name(), "Hysteria2", "Fast on lossy networks, UDP 4443"),
        new ProtoRow(Protocol.SHADOWSOCKS.name(), "Shadowsocks", "Helps bypass censorship"),
        new ProtoRow(Protocol.SSH_WS.name(), "SSH over WebSocket", "SSH via WS 8080"),
        new ProtoRow(Protocol.SLOWDNS_SSH.name(), "SlowDNS", "SSH over DNS, port 53"),
        new ProtoRow(Protocol.UDP_CUSTOM.name(), "UDP Custom", "UDP tunnel 36712"),
        new ProtoRow(Protocol.IKEV2.name(), "IKEv2", "Great for mobile, quick reconnection (system VPN)"),
        new ProtoRow(Protocol.L2TP_IPSEC.name(), "L2TP/IPSec", "Standard security (system VPN)"),
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nexus_settings);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        selectedProtocol = prefs.getString(TunnelSettings.KEY_DEFAULT_PROTOCOL, "AUTO");
        selectedTheme = ThemeSettings.getMode(this);

        themeList = findViewById(R.id.cc_theme_list);
        buildThemeRows();
        protocolList = findViewById(R.id.cc_protocol_list);
        toggleList = findViewById(R.id.cc_toggle_list);
        buildProtocolRows("");
        buildToggles();

        EditText search = findViewById(R.id.cc_protocol_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { buildProtocolRows(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        findViewById(R.id.cc_set_back).setOnClickListener(v -> finish());
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    /** Appearance radio rows — same row shape as the protocol list.
     *  Tapping a row persists the mode AND applies it immediately via
     *  ThemeSettings (AppCompatDelegate recreates the UI in the new
     *  mode, app-wide). */
    private void buildThemeRows() {
        themeList.removeAllViews();
        selectedTheme = ThemeSettings.getMode(this);
        for (final ThemeRow row : THEME_ROWS) {
            boolean selected = row.id.equals(selectedTheme);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackgroundResource(selected ? R.drawable.cc_card_selected : R.drawable.cc_card_plain);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            item.setLayoutParams(lp);

            TextView radio = new TextView(this);
            radio.setText(selected ? "●" : "○");
            radio.setTextSize(16);
            radio.setTextColor(getResources().getColor(
                selected ? R.color.cc_accent : R.color.cc_muted));
            item.addView(radio);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(10);
            texts.setLayoutParams(tlp);
            TextView title = new TextView(this);
            title.setText(row.title); title.setTextSize(15);
            title.setTextColor(getResources().getColor(R.color.cc_ink));
            TextView desc = new TextView(this);
            desc.setText(row.desc); desc.setTextSize(12);
            desc.setTextColor(getResources().getColor(R.color.cc_muted));
            texts.addView(title); texts.addView(desc);
            item.addView(texts);

            if (selected) {
                TextView check = new TextView(this);
                check.setText("✓"); check.setTextSize(16);
                check.setTextColor(getResources().getColor(R.color.cc_accent));
                item.addView(check);
            }
            item.setOnClickListener(v -> {
                ThemeSettings.setMode(NexusSettingsActivity.this, row.id);
                ThemeSettings.applySaved(NexusSettingsActivity.this);
                buildThemeRows();
                brandedToast("Theme: " + row.title);
            });
            themeList.addView(item);
        }
    }

    private void buildProtocolRows(String filter) {
        protocolList.removeAllViews();
        String f = filter == null ? "" : filter.toLowerCase();
        for (final ProtoRow row : ROWS) {
            if (!f.isEmpty() && !(row.title + " " + row.desc).toLowerCase().contains(f)) continue;
            boolean selected = row.id.equals(selectedProtocol);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackgroundResource(selected ? R.drawable.cc_card_selected : R.drawable.cc_card_plain);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            item.setLayoutParams(lp);

            TextView radio = new TextView(this);
            radio.setText(selected ? "●" : "○");
            radio.setTextSize(16);
            item.addView(radio);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(10);
            texts.setLayoutParams(tlp);
            TextView title = new TextView(this);
            title.setText(row.title); title.setTextSize(15);
            TextView desc = new TextView(this);
            desc.setText(row.desc); desc.setTextSize(12);
            texts.addView(title); texts.addView(desc);
            item.addView(texts);

            if (selected) {
                TextView check = new TextView(this);
                check.setText("✓"); check.setTextSize(16);
                item.addView(check);
            }
            item.setOnClickListener(v -> {
                selectedProtocol = row.id;
                prefs.edit().putString(TunnelSettings.KEY_DEFAULT_PROTOCOL, row.id).apply();
                buildProtocolRows(((EditText) findViewById(R.id.cc_protocol_search)).getText().toString());
                brandedToast(row.title + " selected");
            });
            protocolList.addView(item);
        }
    }

    private void addToggle(String key, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView title = new TextView(this);
        title.setText(label); title.setTextSize(15);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        item.addView(title);
        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, false));
        sw.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            brandedToast(label + (checked ? " ON" : " OFF"));
        });
        item.addView(sw);
        toggleList.addView(item);
    }

    /** Same row shape as addToggle, but bound to FeedUpdater's pref
     *  (client private prefs, cc_feed_auto, DEFAULT ON — see class doc). */
    private void addFeedToggle() {
        final String label = "Auto-update servers";
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView title = new TextView(this);
        title.setText(label); title.setTextSize(15);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        item.addView(title);
        Switch sw = new Switch(this);
        sw.setChecked(FeedUpdater.isAutoEnabled(this));
        sw.setOnCheckedChangeListener((btn, checked) -> {
            FeedUpdater.setAutoEnabled(NexusSettingsActivity.this, checked);
            brandedToast(label + (checked ? " ON" : " OFF"));
        });
        item.addView(sw);
        toggleList.addView(item);
    }

    private void buildToggles() {
        toggleList.removeAllViews();
        addToggle(TunnelSettings.KEY_KILL_SWITCH, "Kill Switch");
        addToggle(TunnelSettings.KEY_SPLIT, "Split Tunneling");
        addToggle(TunnelSettings.KEY_DOUBLE_VPN, "Double VPN");
        addToggle(TunnelSettings.KEY_DNS_LEAK, "DNS Leak Protection");
        addToggle(TunnelSettings.KEY_AUTOCONNECT, "Auto-connect on boot");
        addToggle(TunnelSettings.KEY_RECONNECT, "Auto reconnect");
        addFeedToggle();
    }

    /** Bottom-center branded toast (her UI rule). */
    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(28));
        t.show();
    }
}
