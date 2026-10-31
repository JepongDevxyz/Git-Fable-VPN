package jayar.lee.espiritu.protocols;

/**
 * Pure mapping from a ProfileStore {@link ServerProfile} to the values
 * the LEGACY tunnel engine actually reads.
 *
 * How a selection reaches the engine (investigated in OpenVPNClient /
 * ConfigUtil / InjectorService, 2026-11-01):
 *  - The old screen's spinners only persist SERVER_POSITION /
 *    NETWORK_POSITION; OpenVPNClient.reLoad_Configs() then translates
 *    the chosen ServerData/NetworksData JSON rows into ConfigUtil
 *    (the singleton both services read): server name, payload name,
 *    network-type string ("UDP"/"HTTP"/"SSL"/"SSL+PAYLOAD"/
 *    "SSL+PAYLOAD+WS"), payload type (SettingsConstants 1..5), server
 *    host/port, proxy host/port, SNI, payload, the DIRECT_UDP_CONFIG
 *    JSON and the credentials — all through ConfigUtil's public
 *    setters. InjectorService (START_SERVICE) and OpenVPNService
 *    (getOvpnConfig(): stored cert + SERVER_KEY/SERVER_PORT +
 *    user/pass + "http-proxy 127.0.0.1 8989") consume exactly those
 *    values. There is no other sanctioned entry point.
 *  - ConfigUtil's setters ARE the custom-config entry point, so a
 *    ProfileStore profile can be injected through them — this class
 *    decides, per protocol, which values to write. Values are kept
 *    PLAINTEXT here; the Android applier encodes them with
 *    FileUtils._e2 exactly like the old screen encodes the
 *    Account-screen user/pass (the secure keys are _d2-decoded on
 *    read), and hideJson() for the .ovpn cert (read back via showJson).
 *
 * What can and cannot be applied (honest limits of the legacy engine —
 * it is an OpenVPN + HTTP/SSL-payload injector + Hysteria-UDP client,
 * nothing else):
 *  - SSH_DIRECT / SSH_WS with a payload (or plain SSL mode): APPLIES —
 *    payload type HTTP_PROXY / SSL / SSL_PAYLOAD / SSL_PROXY, host,
 *    port, SNI, proxy and credentials map 1:1 onto the injector.
 *  - OPENVPN_UDP / OPENVPN_TCP with a full .ovpn text: APPLIES the
 *    cert + remote host/port + credentials via ConfigUtil; the paired
 *    injector network (payload/proxy) still comes from the last-used
 *    legacy network selection, exactly like the old screen pairs a
 *    cert with the chosen network row (keepLegacyNetwork = true).
 *  - UDP_CUSTOM: APPLIES only when the profile carries a full legacy
 *    UDP client JSON (payload / extra "udpConfig" / "networkPayload"
 *    containing an "obfs" field) — that JSON is what UDPThread runs.
 *    A bare PSK (Generator/Quick-Add profiles) is NOT enough to build
 *    that JSON, so those profiles fall back.
 *  - SLOWDNS_SSH: CANNOT apply — the engine has no dnstt/DNS-tunnel
 *    settings at all (no ConfigUtil keys for NS domain / pubkey).
 *  - Sing-box protocols (VLESS/VMess/Trojan/SS/HY2/WireGuard) and the
 *    system-VPN protocols (L2TP/IKEv2/PPTP): CANNOT apply — no core
 *    for them in this engine yet.
 * Anything not applied falls back to the last-used legacy selection
 * (SERVER_POSITION / NETWORK_POSITION + reLoad_Configs semantics),
 * which is precisely what the old home screen would have connected.
 *
 * Payload-type constants mirror SettingsConstants (1..5) so this
 * class stays Android-free and unit-testable.
 */
public final class LegacyEnginePlan {
    private LegacyEnginePlan() {}

    public static final int TYPE_HTTP_PROXY = 1;   // SettingsConstants.PAYLOAD_TYPE_HTTP_PROXY
    public static final int TYPE_SSL = 2;          // PAYLOAD_TYPE_SSL
    public static final int TYPE_SSL_PAYLOAD = 3;  // PAYLOAD_TYPE_SSL_PAYLOAD
    public static final int TYPE_SSL_PROXY = 4;    // PAYLOAD_TYPE_SSL_PROXY
    public static final int TYPE_UDP = 5;          // PAYLOAD_TYPE_UDP

    public boolean supported;
    public String reason = "";
    /** True when only the cert/remote/credentials are custom and the
     *  injector network must come from the legacy selection. */
    public boolean keepLegacyNetwork;
    public String networkType = "";   // "HTTP" / "SSL" / "SSL+PAYLOAD" / "SSL+PAYLOAD+WS" / "UDP"
    public int payloadType = 0;
    public String serverName = "";
    public String payloadName = "";
    public String serverHost = "";
    public String serverPort = "";
    public String proxyHost = "";
    public String proxyPort = "";
    public String payload = "";
    public String sni = "";
    public String udpConfig = "";
    public String ovpnConfig = "";
    public String username = "";
    public String password = "";
    /** True when username/password came from the profile itself. */
    public boolean profileCredentials;

    private static String nz(String s) { return s == null ? "" : s; }
    private static String ex(ServerProfile p, String k) {
        if (p == null || p.extra == null) return "";
        String v = p.extra.get(k);
        return v == null ? "" : v;
    }
    private static boolean flag(ServerProfile p, String k) { return "1".equals(ex(p, k)); }
    private static int atoi(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    public static LegacyEnginePlan fromProfile(ServerProfile p, String screenUser, String screenPass) {
        LegacyEnginePlan plan = new LegacyEnginePlan();
        if (p == null) { plan.reason = "no profile selected"; return plan; }
        plan.serverName = p.displayName();
        plan.payloadName = firstNonEmpty(ex(p, "payloadName"), p.name);
        plan.serverHost = nz(p.host).trim();
        plan.serverPort = p.port > 0 ? Integer.toString(p.port) : "";
        // Credentials: the profile's own account wins; the Account
        // screen's user/pass (same prefs the old flow reads) is the
        // fallback, exactly like AutoLogIn=false legacy servers.
        if (!nz(p.username).isEmpty() || !nz(p.password).isEmpty()) {
            plan.username = nz(p.username);
            plan.password = nz(p.password);
            plan.profileCredentials = true;
        } else {
            plan.username = nz(screenUser);
            plan.password = nz(screenPass);
        }
        if (p.protocol == null) { plan.reason = "profile has no protocol"; return plan; }

        switch (p.protocol) {
            case OPENVPN_UDP: case OPENVPN_TCP: {
                if (nz(p.ovpnConfig).trim().isEmpty()) {
                    plan.reason = "OpenVPN profile has no .ovpn config text";
                    return plan;
                }
                plan.supported = true;
                plan.keepLegacyNetwork = true;
                plan.ovpnConfig = p.ovpnConfig;
                plan.reason = "applies cert + remote + credentials; injector network stays the last-used legacy network";
                return plan;
            }
            case SSH_DIRECT: case SSH_WS: {
                boolean ssl = flag(p, "ssl");
                boolean sslPayload = flag(p, "ssl_payload");
                boolean sslRp = flag(p, "ssl_rp");
                int sslPort = atoi(ex(p, "sslPort"), 0);
                plan.sni = firstNonEmpty(p.sni, p.hostHeader, p.host);
                plan.payload = nz(p.payload);
                if (ssl && sslRp) {
                    plan.payloadType = TYPE_SSL_PROXY;
                    plan.networkType = "SSL+PAYLOAD+WS";
                    plan.serverPort = Integer.toString(sslPort > 0 ? sslPort : p.port);
                    String rp = ex(p, "reverseProxy");
                    plan.proxyHost = firstNonEmpty(p.proxyHost, hostOf(rp), p.host);
                    plan.proxyPort = Integer.toString(p.proxyPort > 0 ? p.proxyPort : portOf(rp, p.port));
                    if (plan.payload.isEmpty()) { plan.reason = "SSL+proxy mode needs a payload"; return plan; }
                } else if (ssl && sslPayload) {
                    plan.payloadType = TYPE_SSL_PAYLOAD;
                    plan.networkType = "SSL+PAYLOAD";
                    plan.serverPort = Integer.toString(sslPort > 0 ? sslPort : p.port);
                    if (plan.payload.isEmpty()) { plan.reason = "SSL+payload mode needs a payload"; return plan; }
                } else if (ssl) {
                    plan.payloadType = TYPE_SSL;
                    plan.networkType = "SSL";
                    plan.serverPort = Integer.toString(sslPort > 0 ? sslPort : p.port);
                } else {
                    plan.payloadType = TYPE_HTTP_PROXY;
                    plan.networkType = "HTTP";
                    if (plan.payload.isEmpty()) { plan.reason = "SSH profile has no payload for the injector"; return plan; }
                    if (flag(p, "custom_proxy") && !nz(p.proxyHost).isEmpty()) {
                        plan.proxyHost = p.proxyHost.trim();
                        plan.proxyPort = Integer.toString(p.proxyPort > 0 ? p.proxyPort : p.port);
                    } else {
                        // No separate proxy: the payload is sent to the
                        // server endpoint itself (legacy networks do the
                        // same when server_type picks the server as proxy).
                        plan.proxyHost = plan.serverHost;
                        plan.proxyPort = plan.serverPort;
                    }
                }
                if (plan.serverHost.isEmpty()) { plan.reason = "profile has no server host"; return plan; }
                plan.supported = true;
                plan.reason = "injector plan: " + plan.networkType;
                return plan;
            }
            case UDP_CUSTOM: {
                String json = firstNonEmpty(p.payload, ex(p, "udpConfig"), ex(p, "networkPayload"));
                if (json.startsWith("{") && json.contains("\"obfs\"")) {
                    plan.supported = true;
                    plan.payloadType = TYPE_UDP;
                    plan.networkType = "UDP";
                    plan.udpConfig = json;
                    plan.reason = "legacy UDP client JSON applied";
                    return plan;
                }
                plan.reason = "UDP Custom profile has no legacy UDP client JSON (bare PSK is not enough)";
                return plan;
            }
            case SLOWDNS_SSH:
                plan.reason = "legacy engine has no SlowDNS (dnstt) support";
                return plan;
            default:
                plan.reason = "legacy engine has no core for " + p.protocol.label;
                return plan;
        }
    }

    private static String hostOf(String hostPort) {
        String s = nz(hostPort).trim();
        int colon = s.lastIndexOf(':');
        return colon > 0 ? s.substring(0, colon) : s;
    }
    private static int portOf(String hostPort, int def) {
        String s = nz(hostPort).trim();
        int colon = s.lastIndexOf(':');
        if (colon > 0 && colon < s.length() - 1) return atoi(s.substring(colon + 1), def);
        return def;
    }
}
