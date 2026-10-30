package jayar.lee.espiritu.activities;

import android.app.Activity;
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

import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.protocols.Protocol;
import jayar.lee.espiritu.protocols.TunnelSettings;

/**
 * Nexus-style Settings screen (her reference, right screen):
 *  - VPN Protocols: search + radio list; selection persists as the default
 *    protocol (TunnelSettings.KEY_DEFAULT_PROTOCOL) and is read by the
 *    Home protocol chip / new-profile flow. REAL selection, not decoration.
 *  - Quick Toggles: Kill Switch, Split Tunneling, Double VPN, DNS Leak
 *    Protection — each Switch writes its TunnelSettings key immediately;
 *    TunnelSettings.buildSingBoxConfig() consumes them for real
 *    (strict_route / exclude_package / detour / hijack-dns).
 * Defaults are OFF on first run (her standing rule).
 */
public class NexusSettingsActivity extends Activity {

    public static final String PREFS = "gitfablevpn_settings";

    private SharedPreferences prefs;
    private LinearLayout protocolList;
    private LinearLayout toggleList;
    private String selectedProtocol;

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

    private void buildToggles() {
        toggleList.removeAllViews();
        addToggle(TunnelSettings.KEY_KILL_SWITCH, "Kill Switch");
        addToggle(TunnelSettings.KEY_SPLIT, "Split Tunneling");
        addToggle(TunnelSettings.KEY_DOUBLE_VPN, "Double VPN");
        addToggle(TunnelSettings.KEY_DNS_LEAK, "DNS Leak Protection");
        addToggle(TunnelSettings.KEY_AUTOCONNECT, "Auto-connect on boot");
        addToggle(TunnelSettings.KEY_RECONNECT, "Auto reconnect");
    }

    /** Bottom-center branded toast (her UI rule). */
    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(28));
        t.show();
    }
}
