package jayar.lee.espiritu.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.jepongdevxyz.fablevpn.R;

import jayar.lee.espiritu.logger.ConnectionStatus;
import jayar.lee.espiritu.logger.HarlieLogs;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.protocols.ConnectionDisplay;
import jayar.lee.espiritu.protocols.ServerProfile;
import jayar.lee.espiritu.service.OpenVPNService;
import jayar.lee.espiritu.view.GraphHelper;

/**
 * Statistics (Nexus bottom-nav) — its own screen, fed by the SAME live
 * sources as Home (her order: no bouncing back to the legacy client):
 *  - State / uptime / speeds: hLogStatus state + byte-count listeners.
 *  - Session totals: OpenVPNService ConnectionStats (non-UDP) or
 *    DataTransferStats (UDP), refreshed on a 1 s ticker — the same
 *    values OpenVPNClient.show_stats() feeds its listeners.
 *  - Traffic chart: GraphHelper rebound to this screen's chart on
 *    resume (the helper is a singleton; whichever screen is resumed
 *    owns the chart).
 *  - Server line: the selected ProfileStore profile, else the engine's
 *    current server/payload names from ConfigUtil.
 *  - Logs button opens the existing HarlieLogs screen.
 * Anything genuinely unavailable outside a live tunnel shows "—".
 */
public class NexusStatisticsActivity extends OpenVPNClientBase
        implements hLogStatus.StateListener, hLogStatus.ByteCountListener {

    private TextView stateLine, serverLine, uptimeLine;
    private TextView totalDown, totalUp, speedDown, speedUp;
    private GraphHelper graph;
    private Handler handler = new Handler();

    private volatile boolean connected;
    private long lastIn = -1, lastOut = -1, lastByteAt = 0;
    private long sessionIn, sessionOut;

    private final Handler statsHandler = new Handler();
    private final Runnable statsTask = new Runnable() {
        @Override public void run() {
            tick();
            statsHandler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nexus_statistics);

        stateLine = findViewById(R.id.cc_stat_state);
        serverLine = findViewById(R.id.cc_stat_server);
        uptimeLine = findViewById(R.id.cc_stat_uptime);
        totalDown = findViewById(R.id.cc_stat_total_down);
        totalUp = findViewById(R.id.cc_stat_total_up);
        speedDown = findViewById(R.id.cc_stat_speed_down);
        speedUp = findViewById(R.id.cc_stat_speed_up);

        LineChart chart = findViewById(R.id.cc_stat_chart);
        if (chart != null) {
            chart.setNoDataText("");
            graph = GraphHelper.getHelper().with(this)
                .color(getResources().getColor(R.color.cc_graph_down)).chart(chart);
        }

        findViewById(R.id.cc_stat_back).setOnClickListener(v -> finish());
        findViewById(R.id.cc_stat_logs).setOnClickListener(v ->
            startActivity(new Intent(this, HarlieLogs.class)));
        doBindService();
    }

    @Override protected void onResume() {
        super.onResume();
        hLogStatus.addStateListener(this);
        hLogStatus.addByteCountListener(this);
        connected = hLogStatus.isTunnelActive() && _upDateBytes.isConnected();
        renderServer();
        renderState();
        if (graph != null && hLogStatus.isTunnelActive()) graph.start();
        statsHandler.removeCallbacks(statsTask);
        statsHandler.post(statsTask);
    }

    @Override protected void onPause() {
        super.onPause();
        statsHandler.removeCallbacks(statsTask);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        statsHandler.removeCallbacks(statsTask);
        hLogStatus.removeStateListener(this);
        hLogStatus.removeByteCountListener(this);
        doUnbindService();
    }

    private void renderServer() {
        ServerProfile sel = ProfilePrefs.selected();
        if (sel != null) {
            serverLine.setText("Server: " + sel.displayName() + " (" + sel.host + ":" + sel.port + ")");
        } else if (mConfig != null && !mConfig.getServerName().isEmpty()) {
            serverLine.setText("Server: " + mConfig.getServerName());
        } else {
            serverLine.setText("Server: —");
        }
    }

    private void renderState() {
        boolean active = hLogStatus.isTunnelActive();
        stateLine.setText("State: " + (!active ? "Disconnected" : connected ? "Connected" : "Connecting…"));
        if (!active) {
            uptimeLine.setText("Uptime: 00:00:00");
            totalDown.setText("—");
            totalUp.setText("—");
            speedDown.setText(ConnectionDisplay.formatRate(0));
            speedUp.setText(ConnectionDisplay.formatRate(0));
        }
    }

    private void tick() {
        if (!hLogStatus.isTunnelActive()) { renderState(); return; }
        try {
            if (!"UDP".equals(mConfig.getNetworkType())) {
                OpenVPNService.ConnectionStats stats = get_connection_stats();
                if (stats != null) {
                    sessionIn = stats.bytes_in;
                    sessionOut = stats.bytes_out;
                    hLogStatus.updateByteCount(stats.bytes_in, stats.bytes_out);
                }
            } else {
                sessionIn = _upDateBytes.getTotalBytesReceived();
                sessionOut = _upDateBytes.getTotalBytesSent();
                hLogStatus.updateByteCount(_upDateBytes.getBytesReceived(), _upDateBytes.getBytesSent());
            }
        } catch (Exception ignored) {
            // unbound / tearing down: keep last values, next tick retries
        }
        if (_upDateBytes.isConnected()) {
            uptimeLine.setText("Uptime: " + ConnectionDisplay.formatUptime(_upDateBytes.getElapsedTime()));
        }
        totalDown.setText(ConnectionDisplay.formatBytes(sessionIn));
        totalUp.setText(ConnectionDisplay.formatBytes(sessionOut));
    }

    @Override public void updateState(String state, String logMessage, int localizedResId,
                                      ConnectionStatus level, int progress) {
        final boolean nowConnected = hLogStatus.VPN_CONNECTED.equals(state)
            || state.equals(resString(R.string.state_connected));
        handler.post(() -> {
            connected = nowConnected;
            if (hLogStatus.VPN_DISCONNECTED.equals(state) || hLogStatus.VPN_AUTH_FAILED.equals(state)) {
                connected = false;
                lastIn = -1; lastOut = -1;
            }
            renderState();
            renderServer();
        });
    }

    @Override public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        long now = System.currentTimeMillis();
        long dt = lastByteAt == 0 ? 0 : now - lastByteAt;
        final long downRate = (lastIn >= 0 && dt > 0) ? Math.max(0, (in - lastIn) * 1000 / dt) : 0;
        final long upRate = (lastOut >= 0 && dt > 0) ? Math.max(0, (out - lastOut) * 1000 / dt) : 0;
        lastIn = in; lastOut = out; lastByteAt = now;
        handler.post(() -> {
            if (hLogStatus.isTunnelActive()) {
                speedDown.setText(ConnectionDisplay.formatRate(downRate));
                speedUp.setText(ConnectionDisplay.formatRate(upRate));
            }
        });
    }
}
