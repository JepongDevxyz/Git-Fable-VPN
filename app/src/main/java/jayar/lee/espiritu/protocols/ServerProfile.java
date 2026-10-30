package jayar.lee.espiritu.protocols;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified server profile for every protocol in the Jepong VPS script.
 * Pure Java (no Android deps) so it can be unit-tested with javac.
 */
public class ServerProfile {
    public Protocol protocol;
    public String name = "";
    public String host = "";
    public int port = 0;

    // credentials / ids
    public String uuid = "";        // vless/vmess
    public String password = "";    // trojan/hy2/ss/ssh
    public String username = "";    // ssh / l2tp / ikev2
    public int alterId = 0;         // vmess
    public String method = "";      // shadowsocks method, e.g. aes-256-gcm

    // transport
    public String network = "";     // ws / grpc / splithttp(xhttp) / tcp
    public String path = "";        // ws/xhttp path: /vless /vmess /trojan /xhttp
    public String hostHeader = "";  // ws Host header
    public String serviceName = ""; // grpc serviceName, VPS script uses "grpc"
    public String security = "";    // tls / reality / none
    public String sni = "";         // TLS server name (usually the VPS domain)
    public boolean allowInsecure = false;
    public String flow = "";        // vless flow

    // wireguard
    public String wgPrivateKey = "";
    public String wgAddress = "";   // e.g. 10.66.66.2/24
    public String wgPublicKey = ""; // server peer public key
    public String wgPresharedKey = "";
    public String wgAllowedIPs = "0.0.0.0/0";
    public String wgDns = "";
    public int wgMtu = 0;
    public String wgReserved = "";

    // hysteria2
    public String hyObfs = "";      // obfs type (salamander) or ""
    public String hyObfsPassword = "";

    // ssh / injector
    public String payload = "";     // HTTP payload for injector / SSH-WS
    public String proxyHost = "";
    public int proxyPort = 0;

    // slowdns (dnstt)
    public String dnsNsDomain = ""; // NS domain, e.g. t.vpn.example.com
    public String dnsPubKey = "";   // dnstt server public key (hex)
    public int dnsPort = 53;

    // udp custom
    public String udpPsk = "";      // udp_custom password/PSK from config.server.json

    // openvpn
    public String ovpnConfig = "";  // full .ovpn text when imported

    public Map<String, String> extra = new LinkedHashMap<>();

    public ServerProfile() {}
    public ServerProfile(Protocol p) { this.protocol = p; }

    public String displayName() {
        if (name != null && !name.trim().isEmpty()) return name.trim();
        return (protocol == null ? "Server" : protocol.label) + " " + host + ":" + port;
    }

    @Override public String toString() {
        return displayName() + " [" + (protocol == null ? "?" : protocol.label) + "] " + host + ":" + port;
    }
}
