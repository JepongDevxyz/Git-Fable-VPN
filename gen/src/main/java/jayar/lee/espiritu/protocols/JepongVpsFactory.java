package jayar.lee.espiritu.protocols;

import java.util.ArrayList;
import java.util.List;

/**
 * "Jepong VPS Quick-Add": builds one ServerProfile per service of our own
 * VPS script (vpn-server-ubuntu/debian.sh, 18 steps) from the values the
 * script prints at the end / saves under /etc/jepongvpn/:
 * xray-creds.txt (UUID_VLESS, UUID_VMESS, TROJAN_PASS), shadowsocks-pass.txt,
 * hysteria-pass.txt, l2tp-creds.txt, ikev2-creds.txt, /etc/dnstt/server.pub,
 * /etc/udp_custom/config.server.json (PSK), wireguard-client.conf.
 * Transports/paths/ports below are copied exactly from that script.
 */
public final class JepongVpsFactory {
    private JepongVpsFactory() {}

    public static class Creds {
        public String domain = "";
        public String nsDomain = "";      // SlowDNS NS domain
        public String uuidVless = "";
        public String uuidVmess = "";
        public String trojanPass = "";
        public String ssPassword = "";    // shadowsocks, aes-256-gcm, 8388
        public String hyPassword = "";    // hysteria2, 4443
        public String sshUser = "";
        public String sshPass = "";
        public String dnsttPubKey = "";   // SlowDNS server public key
        public String udpPsk = "";        // udp_custom PSK
        public String l2tpUser = "", l2tpPass = "", l2tpPsk = "";
        public String ikev2User = "", ikev2Pass = "";
    }

    public static List<ServerProfile> buildAll(Creds c) {
        List<ServerProfile> out = new ArrayList<>();
        String d = c.domain;

        // Xray VLESS WS — 443, path /vless (inbound 10001)
        ServerProfile vlessWs = base(Protocol.VLESS_WS, "Jepong VPS · VLESS WS", d, 443);
        vlessWs.uuid = c.uuidVless; vlessWs.network = "ws"; vlessWs.path = "/vless";
        vlessWs.security = "tls"; vlessWs.sni = d; vlessWs.hostHeader = d;
        out.add(vlessWs);

        // Xray VMess WS — 443, path /vmess (10002)
        ServerProfile vmess = base(Protocol.VMESS_WS, "Jepong VPS · VMess WS", d, 443);
        vmess.uuid = c.uuidVmess; vmess.network = "ws"; vmess.path = "/vmess";
        vmess.security = "tls"; vmess.sni = d; vmess.hostHeader = d;
        out.add(vmess);

        // Xray Trojan WS — 443, path /trojan (10003)
        ServerProfile trojan = base(Protocol.TROJAN_WS, "Jepong VPS · Trojan WS", d, 443);
        trojan.password = c.trojanPass; trojan.network = "ws"; trojan.path = "/trojan";
        trojan.security = "tls"; trojan.sni = d; trojan.hostHeader = d;
        out.add(trojan);

        // Xray VLESS gRPC — 443, serviceName grpc (10004)
        ServerProfile grpc = base(Protocol.VLESS_GRPC, "Jepong VPS · VLESS gRPC", d, 443);
        grpc.uuid = c.uuidVless; grpc.network = "grpc"; grpc.serviceName = "grpc";
        grpc.security = "tls"; grpc.sni = d;
        out.add(grpc);

        // Xray VLESS XHTTP — 443, path /xhttp (10005)
        ServerProfile xhttp = base(Protocol.VLESS_XHTTP, "Jepong VPS · VLESS XHTTP", d, 443);
        xhttp.uuid = c.uuidVless; xhttp.network = "xhttp"; xhttp.path = "/xhttp";
        xhttp.security = "tls"; xhttp.sni = d; xhttp.hostHeader = d;
        out.add(xhttp);

        // SSH over WebSocket — 8080 (ssh-ws.py)
        ServerProfile sshWs = base(Protocol.SSH_WS, "Jepong VPS · SSH-WS", d, 8080);
        sshWs.username = c.sshUser; sshWs.password = c.sshPass; sshWs.hostHeader = d;
        out.add(sshWs);

        // SSH direct — OpenSSH 22 / Dropbear 109,143
        ServerProfile ssh = base(Protocol.SSH_DIRECT, "Jepong VPS · SSH", d, 22);
        ssh.username = c.sshUser; ssh.password = c.sshPass;
        out.add(ssh);

        // SlowDNS — dnstt UDP 53 over SSH 22
        ServerProfile slow = base(Protocol.SLOWDNS_SSH, "Jepong VPS · SlowDNS", d, 22);
        slow.username = c.sshUser; slow.password = c.sshPass;
        slow.dnsNsDomain = c.nsDomain; slow.dnsPubKey = c.dnsttPubKey; slow.dnsPort = 53;
        out.add(slow);

        // UDP Custom — 36712
        ServerProfile udp = base(Protocol.UDP_CUSTOM, "Jepong VPS · UDP Custom", d, 36712);
        udp.udpPsk = c.udpPsk; udp.username = c.sshUser; udp.password = c.sshPass;
        out.add(udp);

        // Shadowsocks — 8388 aes-256-gcm
        ServerProfile ss = base(Protocol.SHADOWSOCKS, "Jepong VPS · Shadowsocks", d, 8388);
        ss.method = "aes-256-gcm"; ss.password = c.ssPassword;
        out.add(ss);

        // Hysteria2 — 4443/udp
        ServerProfile hy = base(Protocol.HYSTERIA2, "Jepong VPS · Hysteria2", d, 4443);
        hy.password = c.hyPassword; hy.security = "tls"; hy.sni = d;
        out.add(hy);

        // L2TP/IPSec + IKEv2 — system VPN guides (no in-app tunnel on Android)
        ServerProfile l2tp = base(Protocol.L2TP_IPSEC, "Jepong VPS · L2TP/IPSec", d, 1701);
        l2tp.username = c.l2tpUser; l2tp.password = c.l2tpPass; l2tp.extra.put("psk", c.l2tpPsk);
        out.add(l2tp);

        ServerProfile ike = base(Protocol.IKEV2, "Jepong VPS · IKEv2", d, 500);
        ike.username = c.ikev2User; ike.password = c.ikev2Pass;
        out.add(ike);

        return out;
        // OpenVPN (UDP+TCP, .ovpn file from the VPS /root/jepong.ovpn) and
        // WireGuard (.conf from /etc/jepongvpn/wireguard-client.conf) are
        // imported as files — their parsers are ShareLinkParser / WireGuardConfParser.
    }

    private static ServerProfile base(Protocol proto, String name, String host, int port) {
        ServerProfile p = new ServerProfile(proto);
        p.name = name; p.host = host; p.port = port;
        return p;
    }
}
