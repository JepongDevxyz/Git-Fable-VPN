package jayar.lee.espiritu.protocols;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings behind the Nexus-style Settings screen — every toggle maps to a
 * REAL behaviour in the tunnel builders (see UI_DESIGN.md):
 *  killSwitch        -> sing-box strict_route + no direct fallback / OpenVPN blocking mode
 *  splitTunneling    -> VpnService disallowed apps / sing-box excluded_package
 *  doubleVpn         -> sing-box chained outbound (detour) via second profile
 *  dnsLeakProtection -> sing-box DNS hijack rules / OpenVPN pushed DNS only
 * New toggles default OFF on first run (her standing order); the Android side
 * persists the user's choices afterwards in SharedPreferences with these keys.
 * Pure Java model — Android glue reads/writes it.
 */
public class TunnelSettings {
    public static final String KEY_KILL_SWITCH = "cc_kill_switch";
    public static final String KEY_SPLIT = "cc_split_tunneling";
    public static final String KEY_SPLIT_APPS = "cc_split_apps";
    public static final String KEY_DOUBLE_VPN = "cc_double_vpn";
    public static final String KEY_DOUBLE_VPN_PROFILE = "cc_double_vpn_profile";
    public static final String KEY_DNS_LEAK = "cc_dns_leak_protection";
    public static final String KEY_AUTOCONNECT = "cc_autoconnect_boot";
    public static final String KEY_RECONNECT = "cc_auto_reconnect";
    public static final String KEY_THEME = "cc_theme"; // system|dark|light
    public static final String KEY_MTU = "cc_mtu";
    public static final String KEY_DNS1 = "cc_dns1";
    public static final String KEY_DNS2 = "cc_dns2";
    public static final String KEY_DEFAULT_PROTOCOL = "cc_default_protocol";

    public boolean killSwitch = false;
    public boolean splitTunneling = false;
    public List<String> splitApps = new ArrayList<>(); // packages that bypass the VPN
    public boolean doubleVpn = false;
    public String doubleVpnProfileName = "";
    public boolean dnsLeakProtection = false;
    public boolean autoConnectBoot = false;
    public boolean autoReconnect = false;
    public String theme = "system";
    public int mtu = 0;
    public String dns1 = "1.1.1.1";
    public String dns2 = "8.8.8.8";
    public String defaultProtocol = "AUTO";

    /** Apply these settings to a sing-box config generated for profile p.
     *  secondProfile is used only when doubleVpn is on. Returns full config JSON. */
    public String buildSingBoxConfig(ServerProfile p, ServerProfile secondProfile) {
        String primary = SingBoxConfigGenerator.outboundJson(p);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"log\": {\"level\": \"info\", \"timestamp\": true},\n");
        // DNS: leak protection ON -> remote DNS through the tunnel + hijack rule.
        sb.append("  \"dns\": {\"servers\": [{\"type\": \"udp\", \"tag\": \"dns-remote\", \"server\": \"")
          .append(JsonUtil.esc(dns1)).append("\", \"detour\": \"proxy\"}], \"final\": \"dns-remote\"");
        if (dnsLeakProtection) sb.append(", \"strategy\": \"ipv4_only\"");
        sb.append("},\n");
        // TUN inbound: strict_route is the sing-box kill-switch mechanism.
        sb.append("  \"inbounds\": [{\"type\": \"tun\", \"tag\": \"tun-in\", \"address\": [\"172.19.0.1/30\"], \"auto_route\": true, \"strict_route\": ")
          .append(killSwitch ? "true" : "false");
        if (mtu > 0) sb.append(", \"mtu\": ").append(mtu);
        if (splitTunneling && !splitApps.isEmpty()) {
            sb.append(", \"exclude_package\": [");
            for (int i = 0; i < splitApps.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(JsonUtil.esc(splitApps.get(i))).append("\"");
            }
            sb.append("]");
        }
        sb.append(", \"stack\": \"system\"}],\n");
        // Outbounds: double VPN chains proxy -> proxy-2 via detour.
        sb.append("  \"outbounds\": [");
        if (doubleVpn && secondProfile != null && secondProfile.protocol != null && secondProfile.protocol.isSingBox()) {
            String chained = primary.replace("\"tag\": \"proxy\"", "\"tag\": \"proxy\", \"detour\": \"proxy-2\"");
            String second = SingBoxConfigGenerator.outboundJson(secondProfile)
                .replace("\"tag\": \"proxy\"", "\"tag\": \"proxy-2\"");
            sb.append(chained).append(", ").append(second).append(", ");
        } else {
            sb.append(primary).append(", ");
        }
        sb.append("{\"type\": \"direct\", \"tag\": \"direct\"}],\n");
        sb.append("  \"route\": {\"auto_detect_interface\": true");
        if (dnsLeakProtection) sb.append(", \"rules\": [{\"port\": 53, \"action\": \"hijack-dns\"}]");
        sb.append(", \"final\": \"proxy\"}\n}\n");
        return sb.toString();
    }
}
