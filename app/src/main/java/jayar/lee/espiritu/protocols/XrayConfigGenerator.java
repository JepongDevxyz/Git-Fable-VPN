package jayar.lee.espiritu.protocols;

/**
 * Generates an Xray-core client config JSON for a ServerProfile, for the
 * bundled AndroidLibXrayLite core (libv2ray.aar, Xray-core v1.260327.1).
 *
 * Shape mirrors the library author's own client template (v2rayNG's
 * v2ray_config_with_tun.json) and SingBoxConfigGenerator's semantics:
 *  - TUN inbound (protocol "tun", tag "tun") — on Android the core reads
 *    the interface fd from the xray.tun.fd env key that
 *    CoreController.startLoop(config, tunFd) sets; the VpnService
 *    Builder owns addresses/routes/DNS.
 *  - stats {} + policy.system statsOutboundUplink/Downlink so
 *    CoreController.queryAllOutboundTrafficStats() returns data.
 *  - DNS module with TunnelSettings dns1/dns2; leak protection adds a
 *    dedicated "dns" outbound (tag "dns-out") + a port-53 routing rule,
 *    the same hijack pattern v2rayNG uses.
 *  - LAN/private ranges route to "direct" (freedom); kill switch drops
 *    the direct outbound entirely and sends private ranges to "block"
 *    instead, so there is no non-tunnel fallback.
 *  - Double VPN: the second profile becomes outbound "proxy-2" and the
 *    primary's streamSettings.sockopt.dialerProxy chains through it —
 *    the Xray equivalent of sing-box's detour chain.
 *  - Split tunneling is NOT a config concern here: it is applied at the
 *    VpnService Builder via addDisallowedApplication (see XrayVpnService).
 *
 * Hysteria2 IS supported by this core build (verified in the .so:
 * HysteriaClientConfig + the hysteria transport). Its outbound protocol
 * name in current Xray-core JSON is "hysteria" with settings
 * {version: 2, address, port}; the auth password travels in
 * streamSettings.hysteriaSettings.auth and salamander obfs in
 * streamSettings.finalmask.udp — both from infra/conf of Xray-core.
 *
 * Protocols outside the Xray family (injector, OpenVPN, WireGuard,
 * system-VPN) throw UnsupportedOperationException — Home routes those
 * to their own engines and never calls this generator for them.
 * Pure Java string building, unit-tested like the sing-box generator.
 */
public final class XrayConfigGenerator {
    private XrayConfigGenerator() {}

    /** Protocols this generator (and the Xray core) can connect. */
    public static boolean isXrayProtocol(Protocol p) {
        if (p == null) return false;
        switch (p) {
            case VLESS_WS: case VLESS_GRPC: case VLESS_XHTTP: case VLESS_TCP:
            case VMESS_WS: case VMESS_TCP:
            case TROJAN_WS: case TROJAN_TCP:
            case SHADOWSOCKS: case HYSTERIA2:
                return true;
            default: return false;
        }
    }

    /** Null-safe string: profile fields are "" by default but parsers and
     *  hand-built profiles can leave them null — never NPE on them. */
    private static String nz(String s) { return s == null ? "" : s; }

    public static String generate(ServerProfile p, TunnelSettings settings, ServerProfile secondProfile) {
        if (p == null || p.protocol == null || !isXrayProtocol(p.protocol)) {
            throw new UnsupportedOperationException("Xray core does not handle "
                + (p == null || p.protocol == null ? "null" : p.protocol.label));
        }
        // Hostile-input guard: a profile that cannot possibly connect
        // fails HERE with a clear message, not as an obscure core error
        // (and never as corrupt JSON or an NPE).
        if (nz(p.host).trim().isEmpty()) {
            throw new IllegalArgumentException("Profile has no server address");
        }
        if (p.port < 1 || p.port > 65535) {
            throw new IllegalArgumentException("Profile has an invalid port: " + p.port);
        }
        switch (p.protocol) {
            case VLESS_WS: case VLESS_GRPC: case VLESS_XHTTP: case VLESS_TCP:
            case VMESS_WS: case VMESS_TCP:
                if (nz(p.uuid).trim().isEmpty())
                    throw new IllegalArgumentException("Profile is missing its UUID");
                break;
            case TROJAN_WS: case TROJAN_TCP: case SHADOWSOCKS: case HYSTERIA2:
                if (nz(p.password).isEmpty())
                    throw new IllegalArgumentException("Profile is missing its password");
                break;
            default: break;
        }
        TunnelSettings s = settings == null ? new TunnelSettings() : settings;
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"log\": {\"loglevel\": \"warning\"},\n");
        sb.append("  \"stats\": {},\n");
        sb.append("  \"policy\": {\"levels\": {\"8\": {\"handshake\": 4, \"connIdle\": 300, \"uplinkOnly\": 1, \"downlinkOnly\": 1}}, "
                + "\"system\": {\"statsOutboundUplink\": true, \"statsOutboundDownlink\": true}},\n");
        // DNS module (core-side resolution; app DNS rides the tunnel via
        // the Builder DNS servers and the routing rules below).
        sb.append("  \"dns\": {\"servers\": [");
        boolean first = true;
        for (String dns : new String[]{s.dns1, s.dns2}) {
            if (dns == null || dns.trim().isEmpty()) continue;
            if (!first) sb.append(", ");
            sb.append("\"").append(JsonUtil.esc(dns.trim())).append("\"");
            first = false;
        }
        sb.append("], \"queryStrategy\": \"UseIP\"},\n");
        // TUN inbound — the fd itself is injected by startLoop.
        int mtu = s.mtu > 0 ? s.mtu : 1500;
        sb.append("  \"inbounds\": [{\"tag\": \"tun\", \"protocol\": \"tun\", \"settings\": {\"name\": \"xray0\", \"MTU\": ")
          .append(mtu).append(", \"userLevel\": 8}, "
          + "\"sniffing\": {\"enabled\": true, \"destOverride\": [\"http\", \"tls\", \"quic\"]}}],\n");
        // Outbounds.
        sb.append("  \"outbounds\": [");
        boolean chain = s.doubleVpn && secondProfile != null && isXrayProtocol(secondProfile.protocol);
        sb.append(outboundJson(p, "proxy", chain ? "proxy-2" : null));
        if (chain) sb.append(", ").append(outboundJson(secondProfile, "proxy-2", null));
        if (s.dnsLeakProtection) sb.append(", {\"tag\": \"dns-out\", \"protocol\": \"dns\"}");
        if (!s.killSwitch) {
            sb.append(", {\"tag\": \"direct\", \"protocol\": \"freedom\", \"streamSettings\": {\"sockopt\": {\"domainStrategy\": \"UseIP\"}}}");
        }
        sb.append(", {\"tag\": \"block\", \"protocol\": \"blackhole\", \"settings\": {\"response\": {\"type\": \"http\"}}}");
        sb.append("],\n");
        // Routing: DNS hijack (leak protection), LAN bypass, else the
        // first outbound ("proxy") is Xray's default route.
        sb.append("  \"routing\": {\"domainStrategy\": \"AsIs\", \"rules\": [");
        boolean wroteRule = false;
        if (s.dnsLeakProtection) {
            sb.append("{\"type\": \"field\", \"network\": \"udp\", \"port\": \"53\", \"outboundTag\": \"dns-out\"}");
            wroteRule = true;
        }
        if (wroteRule) sb.append(", ");
        sb.append("{\"type\": \"field\", \"ip\": [\"geoip:private\"], \"outboundTag\": \"")
          .append(s.killSwitch ? "block" : "direct").append("\"}");
        sb.append("]}\n}\n");
        return sb.toString();
    }

    /** One outbound object. dialerProxyTag != null chains this outbound's
     *  dialer through that outbound (double VPN). */
    public static String outboundJson(ServerProfile p, String tag, String dialerProxyTag) {
        if (p == null || p.protocol == null) {
            throw new UnsupportedOperationException("Xray core does not handle a null profile");
        }
        StringBuilder o = new StringBuilder();
        o.append("{\"tag\": \"").append(JsonUtil.esc(tag)).append("\"");
        switch (p.protocol) {
            case VLESS_WS: case VLESS_GRPC: case VLESS_XHTTP: case VLESS_TCP:
                o.append(", \"protocol\": \"vless\"");
                o.append(", \"settings\": {\"vnext\": [{\"address\": \"").append(JsonUtil.esc(p.host))
                 .append("\", \"port\": ").append(p.port)
                 .append(", \"users\": [{\"id\": \"").append(JsonUtil.esc(p.uuid))
                 .append("\", \"encryption\": \"none\"");
                if (!nz(p.flow).isEmpty()) o.append(", \"flow\": \"").append(JsonUtil.esc(p.flow)).append("\"");
                o.append("}]}]}");
                appendStream(o, p, dialerProxyTag);
                break;
            case VMESS_WS: case VMESS_TCP:
                o.append(", \"protocol\": \"vmess\"");
                o.append(", \"settings\": {\"vnext\": [{\"address\": \"").append(JsonUtil.esc(p.host))
                 .append("\", \"port\": ").append(p.port)
                 .append(", \"users\": [{\"id\": \"").append(JsonUtil.esc(p.uuid))
                 .append("\", \"alterId\": ").append(p.alterId)
                 .append(", \"security\": \"auto\"}]}]}");
                appendStream(o, p, dialerProxyTag);
                break;
            case TROJAN_WS: case TROJAN_TCP:
                o.append(", \"protocol\": \"trojan\"");
                o.append(", \"settings\": {\"servers\": [{\"address\": \"").append(JsonUtil.esc(p.host))
                 .append("\", \"port\": ").append(p.port)
                 .append(", \"password\": \"").append(JsonUtil.esc(p.password)).append("\"}]}");
                appendStream(o, p, dialerProxyTag);
                break;
            case SHADOWSOCKS:
                o.append(", \"protocol\": \"shadowsocks\"");
                o.append(", \"settings\": {\"servers\": [{\"address\": \"").append(JsonUtil.esc(p.host))
                 .append("\", \"port\": ").append(p.port)
                 .append(", \"method\": \"").append(JsonUtil.esc(nz(p.method).isEmpty() ? "aes-256-gcm" : p.method))
                 .append("\", \"password\": \"").append(JsonUtil.esc(p.password)).append("\"}]}");
                if (dialerProxyTag != null) appendSockoptOnly(o, dialerProxyTag);
                break;
            case HYSTERIA2:
                o.append(", \"protocol\": \"hysteria\"");
                o.append(", \"settings\": {\"version\": 2, \"address\": \"").append(JsonUtil.esc(p.host))
                 .append("\", \"port\": ").append(p.port).append("}");
                appendStream(o, p, dialerProxyTag);
                break;
            default:
                throw new UnsupportedOperationException("Xray core does not handle " + p.protocol.label);
        }
        o.append("}");
        return o.toString();
    }

    private static void appendSockoptOnly(StringBuilder o, String dialerProxyTag) {
        o.append(", \"streamSettings\": {\"sockopt\": {\"dialerProxy\": \"")
         .append(JsonUtil.esc(dialerProxyTag)).append("\"}}");
    }

    /** streamSettings: transport + security (+ dialer chain). */
    private static void appendStream(StringBuilder o, ServerProfile p, String dialerProxyTag) {
        String net = transportOf(p);
        StringBuilder st = new StringBuilder();
        st.append("\"network\": \"").append(net).append("\"");
        // --- transport settings ---
        switch (net) {
            case "ws":
                st.append(", \"wsSettings\": {\"path\": \"").append(JsonUtil.esc(nz(p.path).isEmpty() ? "/" : p.path)).append("\"");
                if (!nz(p.hostHeader).isEmpty())
                    st.append(", \"headers\": {\"Host\": \"").append(JsonUtil.esc(p.hostHeader)).append("\"}");
                st.append("}");
                break;
            case "grpc":
                st.append(", \"grpcSettings\": {\"serviceName\": \"")
                  .append(JsonUtil.esc(nz(p.serviceName).isEmpty() ? "grpc" : p.serviceName))
                  .append("\", \"multiMode\": false}");
                break;
            case "xhttp":
                st.append(", \"xhttpSettings\": {\"path\": \"")
                  .append(JsonUtil.esc(nz(p.path).isEmpty() ? "/xhttp" : p.path)).append("\"");
                if (!nz(p.hostHeader).isEmpty())
                    st.append(", \"host\": \"").append(JsonUtil.esc(p.hostHeader)).append("\"");
                String mode = p.extra == null ? null : p.extra.get("mode");
                st.append(", \"mode\": \"").append(JsonUtil.esc(mode == null || mode.isEmpty() ? "auto" : mode)).append("\"}");
                break;
            case "hysteria":
                st.append(", \"hysteriaSettings\": {\"auth\": \"").append(JsonUtil.esc(p.password)).append("\"}");
                break;
            default: // tcp: no transport settings block
                break;
        }
        // --- security ---
        boolean forceTls = p.protocol == Protocol.TROJAN_WS || p.protocol == Protocol.TROJAN_TCP
            || p.protocol == Protocol.HYSTERIA2;
        String sec = p.security == null ? "" : p.security.trim().toLowerCase();
        if ("reality".equals(sec)) {
            st.append(", \"security\": \"reality\", \"realitySettings\": {\"serverName\": \"")
              .append(JsonUtil.esc(nz(p.sni).isEmpty() ? p.host : p.sni))
              .append("\", \"fingerprint\": \"").append(JsonUtil.esc(fp(p, "chrome")))
              .append("\", \"publicKey\": \"").append(JsonUtil.esc(extra(p, "pbk", "publicKey")))
              .append("\", \"shortId\": \"").append(JsonUtil.esc(extra(p, "sid", "shortId"))).append("\"");
            String spx = extra(p, "spx", "spiderX");
            if (!spx.isEmpty()) st.append(", \"spiderX\": \"").append(JsonUtil.esc(spx)).append("\"");
            st.append("}");
        } else if (forceTls || "tls".equals(sec)) {
            st.append(", \"security\": \"tls\", \"tlsSettings\": {\"serverName\": \"")
              .append(JsonUtil.esc(nz(p.sni).isEmpty() ? p.host : p.sni)).append("\"");
            if (p.allowInsecure) st.append(", \"allowInsecure\": true");
            if (p.protocol == Protocol.HYSTERIA2) st.append(", \"alpn\": [\"h3\"]");
            else st.append(", \"alpn\": [\"h2\", \"http/1.1\"]");
            String fp = fp(p, null);
            if (fp != null && !fp.isEmpty()) st.append(", \"fingerprint\": \"").append(JsonUtil.esc(fp)).append("\"");
            st.append("}");
        }
        // --- hysteria2 salamander obfs via finalmask ---
        if (p.protocol == Protocol.HYSTERIA2 && !nz(p.hyObfs).isEmpty()) {
            st.append(", \"finalmask\": {\"udp\": [{\"type\": \"")
              .append(JsonUtil.esc(p.hyObfs))
              .append("\", \"settings\": {\"password\": \"").append(JsonUtil.esc(p.hyObfsPassword)).append("\"}}]}");
        }
        // --- double-VPN dialer chain ---
        if (dialerProxyTag != null) {
            st.append(", \"sockopt\": {\"dialerProxy\": \"").append(JsonUtil.esc(dialerProxyTag)).append("\"}");
        }
        o.append(", \"streamSettings\": {").append(st).append("}");
    }

    /** Transport name for Xray streamSettings.network. The Protocol enum
     *  is authoritative; ServerProfile.network is the fallback (it uses
     *  sing-box spellings, so "splithttp" is normalized to "xhttp"). */
    static String transportOf(ServerProfile p) {
        if (p == null || p.protocol == null) return "tcp";
        switch (p.protocol) {
            case VLESS_WS: case VMESS_WS: case TROJAN_WS: return "ws";
            case VLESS_GRPC: return "grpc";
            case VLESS_XHTTP: return "xhttp";
            case HYSTERIA2: return "hysteria";
            case VLESS_TCP: case VMESS_TCP: case TROJAN_TCP: return "tcp";
            default: break;
        }
        String n = p.network == null ? "" : p.network.trim().toLowerCase();
        if ("splithttp".equals(n)) return "xhttp";
        if (n.isEmpty()) return "tcp";
        return n;
    }

    private static String extra(ServerProfile p, String... keys) {
        if (p == null || p.extra == null) return "";
        for (String k : keys) {
            String v = p.extra.get(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    /** uTLS fingerprint the profile carries (extra "fp"/"fingerprint"), or def. */
    private static String fp(ServerProfile p, String def) {
        String v = extra(p, "fp", "fingerprint");
        return v.isEmpty() && def != null ? def : v;
    }
}
