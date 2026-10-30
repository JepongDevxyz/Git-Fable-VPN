package jayar.lee.espiritu.protocols;

/** All tunnel protocols supported by the Jepong VPS script (18 steps). */
public enum Protocol {
    OPENVPN_UDP("OpenVPN UDP"),
    OPENVPN_TCP("OpenVPN TCP"),
    VLESS_WS("VLESS WS"),
    VLESS_GRPC("VLESS gRPC"),
    VLESS_XHTTP("VLESS XHTTP"),
    VLESS_TCP("VLESS TCP"),
    VMESS_WS("VMess WS"),
    VMESS_TCP("VMess TCP"),
    TROJAN_WS("Trojan WS"),
    TROJAN_TCP("Trojan TCP"),
    SHADOWSOCKS("Shadowsocks"),
    WIREGUARD("WireGuard"),
    HYSTERIA2("Hysteria2"),
    SSH_DIRECT("SSH Direct"),
    SSH_WS("SSH over WebSocket"),
    SLOWDNS_SSH("SlowDNS + SSH"),
    UDP_CUSTOM("UDP Custom"),
    L2TP_IPSEC("L2TP/IPSec"),
    IKEV2("IKEv2"),
    PPTP_LEGACY("PPTP (legacy)");

    public final String label;
    Protocol(String label) { this.label = label; }

    /** Protocols that run through the sing-box core. */
    public boolean isSingBox() {
        switch (this) {
            case VLESS_WS: case VLESS_GRPC: case VLESS_XHTTP: case VLESS_TCP:
            case VMESS_WS: case VMESS_TCP:
            case TROJAN_WS: case TROJAN_TCP:
            case SHADOWSOCKS: case HYSTERIA2: case WIREGUARD:
                return true;
            default: return false;
        }
    }
}
