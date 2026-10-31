package jayar.lee.espiritu.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.protocols.FeedUpdater;
import jayar.lee.espiritu.protocols.ServerProfile;
import jayar.lee.espiritu.protocols.TunnelSettings;

/**
 * Nexus-style Home (her reference). Top bar: logo + PROFILE icon only
 * (Settings chip removed on her order; profile icon opens the Account /
 * userpass screen, also on her order).
 *
 * Wiring status (honest):
 *  - Profile icon -> AccountActivity (real, same prefs as connect flow)
 *  - Protocol chip -> NexusSettingsActivity (real selection, persisted)
 *  - Bottom nav Settings -> NexusSettingsActivity
 *  - Change Location + bottom nav Locations -> ServersActivity (real
 *    profile library: ProfilePrefs/ProfileStore, select/import/delete)
 *  - Online server feed (her order, 2026-11-01): onResume quietly asks
 *    FeedUpdater for a refresh when the 6 h throttle allows it — servers
 *    update by themselves, no manual import. Background thread only,
 *    silent on failure.
 *  - NO Server Generator in this app (her clarified order, 2026-10-31):
 *    the generator is an ADMIN-ONLY tool and lives in the separate
 *    "Git Fable Generator" app (module :gen, com.jepongdevxyz.fablegen);
 *    this client is user-only — users receive profiles via Import.
 *  - Connect ring + bottom nav Statistics still delegate to the existing
 *    working OpenVPNClient screen (which owns the live OpenVPN tunnel,
 *    server/network spinners and traffic graph) until the sing-box /
 *    WireGuard native service binding lands — real navigations to the
 *    functional screen, not dead buttons.
 *  - This activity IS the app Home since the 2026-10-31 fix: SplashScreen
 *    now routes here (her phone test of the first CI APK showed the old
 *    client first because the entry still pointed at OpenVPNClient).
 */
public class NexusHomeActivity extends Activity {

    private TextView protocolChip;
    private TextView currentServer;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nexus_home);

        protocolChip = findViewById(R.id.cc_protocol_chip);
        currentServer = findViewById(R.id.cc_current_server);

        // No live data is bound to the Home chart yet — blank the library's
        // default "No chart data available." placeholder text.
        LineChart chart = findViewById(R.id.cc_chart);
        if (chart != null) chart.setNoDataText("");

        findViewById(R.id.cc_home_account).setOnClickListener(v ->
            startActivity(new Intent(this, AccountActivity.class)));

        View.OnClickListener toSettings = v ->
            startActivity(new Intent(this, NexusSettingsActivity.class));
        protocolChip.setOnClickListener(toSettings);
        findViewById(R.id.cc_nav_settings).setOnClickListener(toSettings);

        View.OnClickListener toClient = v ->
            startActivity(new Intent(this, OpenVPNClient.class));
        findViewById(R.id.cc_connect_ring).setOnClickListener(toClient);
        findViewById(R.id.cc_nav_stats).setOnClickListener(toClient);

        View.OnClickListener toServers = v ->
            startActivity(new Intent(this, ServersActivity.class));
        findViewById(R.id.cc_change_location).setOnClickListener(toServers);
        findViewById(R.id.cc_nav_locations).setOnClickListener(toServers);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshHeader();
        maybeAutoUpdateFeed();
    }

    private void refreshHeader() {
        SharedPreferences sp = getSharedPreferences(NexusSettingsActivity.PREFS, MODE_PRIVATE);
        String proto = sp.getString(TunnelSettings.KEY_DEFAULT_PROTOCOL, "AUTO");
        protocolChip.setText("Protocol: " + prettyProtocol(proto));
        if (currentServer != null) {
            ServerProfile sel = ProfilePrefs.selected();
            currentServer.setText(sel == null
                ? "Current server: none selected"
                : "Current server: " + sel.displayName());
        }
    }

    /**
     * Online feed auto-update: when FeedUpdater says a refresh is due
     * (auto-update ON + last success > 6 h ago), fetch on a background
     * thread — never blocking the UI, no dialogs — then refresh the
     * "Current server" label if this screen is still alive.
     * FeedUpdater.fetchAndApply is single-flight, so overlapping calls
     * from Home / Servers are harmless.
     */
    private void maybeAutoUpdateFeed() {
        if (!FeedUpdater.shouldFetchNow(this)) return;
        new Thread(() -> {
            boolean ok = FeedUpdater.fetchAndApply(NexusHomeActivity.this);
            if (ok && !isFinishing() && !isDestroyed()) {
                runOnUiThread(this::refreshHeader);
            }
        }).start();
    }

    private String prettyProtocol(String id) {
        if ("AUTO".equals(id)) return "Auto";
        String s = id.replace('_', ' ');
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
