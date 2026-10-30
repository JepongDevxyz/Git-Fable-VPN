package jayar.lee.espiritu.protocols;

/**
 * Maps the Generator form (FormData) to a ServerProfile and back.
 * Covers EVERY service of our VPS script (vpn-server-ubuntu.sh), with the
 * script's exact defaults: VLESS WS /vless, VMess WS /vmess, Trojan WS
 * /trojan, VLESS gRPC serviceName "grpc", VLESS XHTTP /xhttp (all 443 TLS),
 * SSH 22 (Dropbear 109/143 via the free port field), SSH-WS 8080,
 * SSH via SSL/Stunnel (SSL port, default 443), SlowDNS (NS + dnstt pubkey,
 * UDP 53), UDP Custom 36712 (PSK), Shadowsocks 8388 aes-256-gcm,
 * Hysteria2 4443, WireGuard 51820, OpenVPN 1194 udp/tcp (.ovpn text),
 * L2TP/IKEv2/PPTP as honest system-VPN profiles (credentials + PSK saved;
 * Android has no in-app tunnel API for them).
 *
 * Extracted from GeneratorActivity so the mapping unit-tests in pure Java:
 * a profile built here feeds ShareLinkParser.toShareLink and
 * SingBoxConfigGenerator unchanged.
 */
public final class GeneratorProfileBuilder {
    private GeneratorProfileBuilder() {}

    public static class FormData {
        public Protocol protocol = Protocol.SSH_WS;
        public boolean sslStyle = false;      // "SSH via SSL (Stunnel)" pseudo-choice
        public String name = "", flag = "", host = "", info = "";
        public int port = 0, sslPort = 0, proxyPort = 0;
        public String proxyHost = "", username = "", password = "";
        // xray
        public String uuid = "", path = "", serviceName = "", sni = "", security = "";
        public int alterId = 0;
        // shadowsocks
        public String method = "";
        // hysteria2
        public String hyObfs = "", hyObfsPassword = "";
        // wireguard
        public String wgPrivateKey = "", wgAddress = "", wgPublicKey = "",
                      wgPresharedKey = "", wgAllowedIPs = "", wgDns = "";
        public int wgMtu = 0;
        // openvpn
        public String ovpnConfig = "";
        // ssh family / payload
        public String payload = "", networkCode = "", payloadName = "", payloadDns = "", payloadInfo = "";
        // slowdns
        public String dnsNsDomain = "", dnsPubKey = "";
        // udp custom / l2tp psk
        public String psk = "";
        // connection-mode toggles (reference generator)
        public boolean useSsl = false, sslPayload = false, sslRp = false,
                       useCustomProxy = false, enableSlowDns = false, enableUdp = false;
        public String reverseProxy = "";
    }

    public static ServerProfile build(FormData f) {
        if (f == null) f = new FormData();
        Protocol proto = f.protocol == null ? Protocol.SSH_WS : f.protocol;
        ServerProfile p = new ServerProfile(proto);
        p.name = nz(f.name);
        p.host = nz(f.host);
        p.port = f.port;
        p.username = nz(f.username);
        p.password = nz(f.password);
        p.sni = nz(f.sni);
        p.proxyHost = nz(f.proxyHost);
        p.proxyPort = f.proxyPort;
        if (!nz(f.flag).isEmpty()) p.extra.put("flag", f.flag.trim());
        if (!nz(f.info).isEmpty()) p.extra.put("info", f.info);
        if (!nz(f.networkCode).isEmpty()) p.extra.put("network", f.networkCode.trim());
        if (!nz(f.payloadName).isEmpty()) p.extra.put("payloadName", f.payloadName);
        if (!nz(f.payloadDns).isEmpty()) p.extra.put("dns", f.payloadDns.trim());
        if (!nz(f.payloadInfo).isEmpty()) p.extra.put("payloadInfo", f.payloadInfo);
        // connection-mode toggles -> recorded on the profile (injector reads them)
        if (f.useSsl || f.sslStyle) {
            p.extra.put("ssl", "1");
            if (f.sslPort > 0) p.extra.put("sslPort", Integer.toString(f.sslPort));
        }
        if (f.sslPayload) p.extra.put("ssl_payload", "1");
        if (f.sslRp) {
            p.extra.put("ssl_rp", "1");
            if (!nz(f.reverseProxy).isEmpty()) p.extra.put("reverseProxy", f.reverseProxy.trim());
        }
        if (f.useCustomProxy) p.extra.put("custom_proxy", "1");
        if (f.enableSlowDns) p.extra.put("slowdns", "1");
        if (f.enableUdp) p.extra.put("udp", "1");

        switch (proto) {
            case VLESS_WS:
                p.uuid = nz(f.uuid); p.network = "ws";
                p.path = def(f.path, "/vless");
                p.security = def(f.security, "tls");
                if (p.sni.isEmpty()) p.sni = p.host;
                p.hostHeader = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case VLESS_GRPC:
                p.uuid = nz(f.uuid); p.network = "grpc";
                p.serviceName = def(f.serviceName, "grpc");
                p.security = def(f.security, "tls");
                if (p.sni.isEmpty()) p.sni = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case VLESS_XHTTP:
                p.uuid = nz(f.uuid); p.network = "xhttp";
                p.path = def(f.path, "/xhttp");
                p.security = def(f.security, "tls");
                if (p.sni.isEmpty()) p.sni = p.host;
                p.hostHeader = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case VLESS_TCP:
                p.uuid = nz(f.uuid); p.network = "tcp";
                p.security = def(f.security, "tls");
                if (p.sni.isEmpty()) p.sni = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case VMESS_WS:
                p.uuid = nz(f.uuid); p.alterId = f.alterId; p.network = "ws";
                p.path = def(f.path, "/vmess");
                p.security = def(f.security, "tls");
                if (p.sni.isEmpty()) p.sni = p.host;
                p.hostHeader = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case VMESS_TCP:
                p.uuid = nz(f.uuid); p.alterId = f.alterId; p.network = "tcp";
                p.security = def(f.security, "tls");
                if (p.sni.isEmpty()) p.sni = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case TROJAN_WS:
                p.network = "ws";
                p.path = def(f.path, "/trojan");
                p.security = "tls";
                if (p.sni.isEmpty()) p.sni = p.host;
                p.hostHeader = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case TROJAN_TCP:
                p.network = "tcp";
                p.security = "tls";
                if (p.sni.isEmpty()) p.sni = p.host;
                if (p.port == 0) p.port = 443;
                break;
            case SHADOWSOCKS:
                p.method = def(f.method, "aes-256-gcm");
                if (p.port == 0) p.port = 8388;
                break;
            case HYSTERIA2:
                p.security = "tls";
                if (p.sni.isEmpty()) p.sni = p.host;
                p.hyObfs = nz(f.hyObfs);
                p.hyObfsPassword = nz(f.hyObfsPassword);
                if (p.port == 0) p.port = 4443;
                break;
            case WIREGUARD:
                p.wgPrivateKey = nz(f.wgPrivateKey);
                p.wgAddress = nz(f.wgAddress);
                p.wgPublicKey = nz(f.wgPublicKey);
                p.wgPresharedKey = nz(f.wgPresharedKey);
                p.wgAllowedIPs = def(f.wgAllowedIPs, "0.0.0.0/0");
                p.wgDns = nz(f.wgDns);
                p.wgMtu = f.wgMtu;
                if (p.port == 0) p.port = 51820;
                break;
            case OPENVPN_UDP: case OPENVPN_TCP:
                p.ovpnConfig = nz(f.ovpnConfig);
                if ((p.host.isEmpty() || p.port == 0) && !p.ovpnConfig.isEmpty()) {
                    ServerProfile parsed = ShareLinkParser.parse(p.ovpnConfig);
                    if (parsed != null) {
                        if (p.host.isEmpty()) p.host = parsed.host;
                        if (p.port == 0) p.port = parsed.port;
                    }
                }
                if (p.port == 0) p.port = 1194;
                break;
            case SSH_DIRECT:
                if (f.sslStyle) {
                    // Stunnel-style: connect through the SSL port (default 443),
                    // SSH itself stays on 22 behind it.
                    p.extra.put("sshPort", Integer.toString(p.port == 0 ? 22 : p.port));
                    p.port = f.sslPort > 0 ? f.sslPort : 443;
                } else if (p.port == 0) p.port = 22;
                p.payload = nz(f.payload);
                break;
            case SSH_WS:
                if (p.port == 0) p.port = 8080;
                p.payload = nz(f.payload);
                if (p.hostHeader.isEmpty()) p.hostHeader = !p.sni.isEmpty() ? p.sni : p.host;
                break;
            case SLOWDNS_SSH:
                if (p.port == 0) p.port = 22;
                p.dnsNsDomain = nz(f.dnsNsDomain);
                p.dnsPubKey = nz(f.dnsPubKey);
                p.dnsPort = 53;
                p.payload = nz(f.payload);
                break;
            case UDP_CUSTOM:
                if (p.port == 0) p.port = 36712;
                p.udpPsk = nz(f.psk);
                break;
            case L2TP_IPSEC:
                if (p.port == 0) p.port = 1701;
                if (!nz(f.psk).isEmpty()) p.extra.put("psk", f.psk);
                p.extra.put("systemVpn", "1");
                break;
            case IKEV2:
                if (p.port == 0) p.port = 500;
                p.extra.put("systemVpn", "1");
                break;
            case PPTP_LEGACY:
                if (p.port == 0) p.port = 1723;
                p.extra.put("systemVpn", "1");
                p.extra.put("legacy", "1");
                break;
            default:
                break;
        }
        return p;
    }

    /** Inverse mapping for Generator edit-in-place: load a stored profile back into the form. */
    public static FormData toFormData(ServerProfile p) {
        FormData f = new FormData();
        if (p == null) return f;
        f.protocol = p.protocol == null ? Protocol.SSH_WS : p.protocol;
        f.name = nz(p.name); f.host = nz(p.host); f.port = p.port;
        f.username = nz(p.username); f.password = nz(p.password); f.sni = nz(p.sni);
        f.proxyHost = nz(p.proxyHost); f.proxyPort = p.proxyPort;
        f.flag = ex(p, "flag"); f.info = ex(p, "info");
        f.networkCode = ex(p, "network"); f.payloadName = ex(p, "payloadName");
        f.payloadDns = ex(p, "dns");
        f.payloadInfo = ex(p, "payloadInfo");
        f.sslPort = atoi(ex(p, "sslPort"));
        f.useSsl = "1".equals(ex(p, "ssl"));
        f.sslPayload = "1".equals(ex(p, "ssl_payload"));
        f.sslRp = "1".equals(ex(p, "ssl_rp"));
        f.reverseProxy = ex(p, "reverseProxy");
        f.useCustomProxy = "1".equals(ex(p, "custom_proxy"));
        f.enableSlowDns = "1".equals(ex(p, "slowdns"));
        f.enableUdp = "1".equals(ex(p, "udp"));
        f.uuid = nz(p.uuid); f.path = nz(p.path); f.serviceName = nz(p.serviceName);
        f.security = nz(p.security); f.alterId = p.alterId;
        f.method = nz(p.method);
        f.hyObfs = nz(p.hyObfs); f.hyObfsPassword = nz(p.hyObfsPassword);
        f.wgPrivateKey = nz(p.wgPrivateKey); f.wgAddress = nz(p.wgAddress);
        f.wgPublicKey = nz(p.wgPublicKey); f.wgPresharedKey = nz(p.wgPresharedKey);
        f.wgAllowedIPs = nz(p.wgAllowedIPs); f.wgDns = nz(p.wgDns); f.wgMtu = p.wgMtu;
        f.ovpnConfig = nz(p.ovpnConfig);
        f.payload = nz(p.payload);
        f.dnsNsDomain = nz(p.dnsNsDomain); f.dnsPubKey = nz(p.dnsPubKey);
        f.psk = p.protocol == Protocol.UDP_CUSTOM ? nz(p.udpPsk) : ex(p, "psk");
        if (f.protocol == Protocol.SSH_DIRECT && f.useSsl && f.sslPort == p.port && p.port > 0) {
            f.sslStyle = true; // saved through the Stunnel-style choice
        }
        return f;
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String def(String v, String dflt) { return v == null || v.trim().isEmpty() ? dflt : v.trim(); }
    private static String ex(ServerProfile p, String k) {
        if (p == null || p.extra == null) return "";
        String v = p.extra.get(k);
        return v == null ? "" : v;
    }
    private static int atoi(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
