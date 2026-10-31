package jayar.lee.espiritu.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.jepongdevxyz.fablevpn.R;
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
 *  - "Server Generator" button under the protocol chip -> GeneratorActivity
 *    (in-app generator editing the SAME store — one sync)
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

        findViewById(R.id.cc_home_account).setOnClickListener(v ->
            startActivity(new Intent(this, AccountActivity.class)));

        View.OnClickListener toSettings = v ->
            startActivity(new Intent(this, NexusSettingsActivity.class));
        protocolChip.setOnClickListener(toSettings);
        findViewById(R.id.cc_nav_settings).setOnClickListener(toSettings);

        findViewById(R.id.cc_open_generator).setOnClickListener(v ->
            startActivity(new Intent(this, GeneratorActivity.class)));

        // Anti-mod guard: RELEASE builds signed with a different cert lose
        // the Generator entry (the server exfiltration path). The app itself
        // is never blocked; debug builds are monitor-only (SignatureCheck).
        if (SignatureCheck.isModifiedRelease(this)) {
            View gen = findViewById(R.id.cc_open_generator);
            gen.setEnabled(false);
            gen.setAlpha(0.35f);
            brandedToast("Modified app detected");
        }

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

    private String prettyProtocol(String id) {
        if ("AUTO".equals(id)) return "Auto";
        String s = id.replace('_', ' ');
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 84);
        t.show();
    }
}
