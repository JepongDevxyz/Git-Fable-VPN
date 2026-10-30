package jayar.lee.espiritu.protocols;

/**
 * Generates a sing-box (1.14.x) client config JSON for a ServerProfile:
 * TUN inbound -> profile outbound. Covers every sing-box protocol in our VPS:
 * VLESS (ws/grpc/splithttp), VMess, Trojan, Shadowsocks, Hysteria2, WireGuard.
 * Pure Java string building — output is validated in the Phase A test harness.
 */
public final class SingBoxConfigGenerator {
    private SingBoxConfigGenerator() {}

    public static String generate(ServerProfile p, int localDnsPort) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"log\": {\"level\": \"info\", \"timestamp\": true},\n");
        sb.append("  \"dns\": {\"servers\": [{\"type\": \"udp\", \"tag\": \"dns-remote\", \"server\": \"1.1.1.1\"}], \"final\": \"dns-remote\"},\n");
        sb.append("  \"inbounds\": [{\"type\": \"tun\", \"tag\": \"tun-in\", \"address\": [\"172.19.0.1/30\"], \"auto_route\": true, \"strict_route\": true, \"stack\": \"system\"}],\n");
        sb.append("  \"outbounds\": [");
        sb.append(outboundJson(p));
        sb.append(", {\"type\": \"direct\", \"tag\": \"direct\"}],\n");
        sb.append("  \"route\": {\"auto_detect_interface\": true, \"final\": \"proxy\"}\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static String outboundJson(ServerProfile p) {
        StringBuilder o = new StringBuilder();
        o.append("{\"tag\": \"proxy\"");
        switch (p.protocol) {
            case VLESS_WS: case VLESS_GRPC: case VLESS_XHTTP: case VLESS_TCP:
                o.append(", \"type\": \"vless\"");
                o.append(", \"server\": \"").append(JsonUtil.esc(p.host)).append("\"");
                o.append(", \"server_port\": ").append(p.port);
                o.append(", \"uuid\": \"").append(JsonUtil.esc(p.uuid)).append("\"");
                if (!p.flow.isEmpty()) o.append(", \"flow\": \"").append(JsonUtil.esc(p.flow)).append("\"");
                appendTls(o, p);
                appendTransport(o, p);
                break;
            case VMESS_WS: case VMESS_TCP:
                o.append(", \"type\": \"vmess\"");
                o.append(", \"server\": \"").append(JsonUtil.esc(p.host)).append("\"");
                o.append(", \"server_port\": ").append(p.port);
                o.append(", \"uuid\": \"").append(JsonUtil.esc(p.uuid)).append("\"");
                o.append(", \"alter_id\": ").append(p.alterId);
                o.append(", \"security\": \"auto\"");
                appendTls(o, p);
                appendTransport(o, p);
                break;
            case TROJAN_WS: case TROJAN_TCP:
                o.append(", \"type\": \"trojan\"");
                o.append(", \"server\": \"").append(JsonUtil.esc(p.host)).append("\"");
                o.append(", \"server_port\": ").append(p.port);
                o.append(", \"password\": \"").append(JsonUtil.esc(p.password)).append("\"");
                appendTls(o, p);
                appendTransport(o, p);
                break;
            case SHADOWSOCKS:
                o.append(", \"type\": \"shadowsocks\"");
                o.append(", \"server\": \"").append(JsonUtil.esc(p.host)).append("\"");
                o.append(", \"server_port\": ").append(p.port);
                o.append(", \"method\": \"").append(JsonUtil.esc(p.method.isEmpty() ? "aes-256-gcm" : p.method)).append("\"");
                o.append(", \"password\": \"").append(JsonUtil.esc(p.password)).append("\"");
                break;
            case HYSTERIA2:
                o.append(", \"type\": \"hysteria2\"");
                o.append(", \"server\": \"").append(JsonUtil.esc(p.host)).append("\"");
                o.append(", \"server_port\": ").append(p.port);
                o.append(", \"password\": \"").append(JsonUtil.esc(p.password)).append("\"");
                if (!p.hyObfs.isEmpty()) {
                    o.append(", \"obfs\": {\"type\": \"").append(JsonUtil.esc(p.hyObfs))
                     .append("\", \"password\": \"").append(JsonUtil.esc(p.hyObfsPassword)).append("\"}");
                }
                appendTls(o, p);
                break;
            case WIREGUARD:
                o.append(", \"type\": \"wireguard\"");
                o.append(", \"server\": \"").append(JsonUtil.esc(p.host)).append("\"");
                o.append(", \"server_port\": ").append(p.port);
                o.append(", \"local_address\": [\"").append(JsonUtil.esc(p.wgAddress)).append("\"]");
                o.append(", \"private_key\": \"").append(JsonUtil.esc(p.wgPrivateKey)).append("\"");
                o.append(", \"peer_public_key\": \"").append(JsonUtil.esc(p.wgPublicKey)).append("\"");
                if (!p.wgPresharedKey.isEmpty())
                    o.append(", \"pre_shared_key\": \"").append(JsonUtil.esc(p.wgPresharedKey)).append("\"");
                if (p.wgMtu > 0) o.append(", \"mtu\": ").append(p.wgMtu);
                break;
            default:
                o.append(", \"type\": \"direct\"");
                break;
        }
        o.append("}");
        return o.toString();
    }

    private static void appendTls(StringBuilder o, ServerProfile p) {
        boolean tls = "tls".equalsIgnoreCase(p.security) || p.protocol == Protocol.TROJAN_WS
            || p.protocol == Protocol.TROJAN_TCP || p.protocol == Protocol.HYSTERIA2;
        if (!tls) return;
        String sni = !p.sni.isEmpty() ? p.sni : p.host;
        o.append(", \"tls\": {\"enabled\": true, \"server_name\": \"").append(JsonUtil.esc(sni)).append("\"");
        if (p.allowInsecure) o.append(", \"insecure\": true");
        o.append(", \"alpn\": [\"h2\", \"http/1.1\"]}");
    }

    private static void appendTransport(StringBuilder o, ServerProfile p) {
        if ("ws".equals(p.network)) {
            o.append(", \"transport\": {\"type\": \"ws\", \"path\": \"").append(JsonUtil.esc(p.path.isEmpty() ? "/" : p.path)).append("\"");
            if (!p.hostHeader.isEmpty())
                o.append(", \"headers\": {\"Host\": \"").append(JsonUtil.esc(p.hostHeader)).append("\"}");
            o.append("}");
        } else if ("grpc".equals(p.network)) {
            o.append(", \"transport\": {\"type\": \"grpc\", \"service_name\": \"")
             .append(JsonUtil.esc(p.serviceName.isEmpty() ? "grpc" : p.serviceName)).append("\"}");
        } else if ("splithttp".equals(p.network) || "xhttp".equals(p.network)) {
            // sing-box calls XHTTP "splithttp"
            o.append(", \"transport\": {\"type\": \"splithttp\", \"path\": \"")
             .append(JsonUtil.esc(p.path.isEmpty() ? "/xhttp" : p.path)).append("\"");
            if (!p.hostHeader.isEmpty())
                o.append(", \"host\": \"").append(JsonUtil.esc(p.hostHeader)).append("\"");
            o.append("}");
        }
    }
}
