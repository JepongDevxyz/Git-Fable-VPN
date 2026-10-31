package jayar.lee.espiritu.protocols;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsers for every share link / config format used by the Jepong VPS services.
 * Formats follow the de-facto standards used by v2rayNG / NekoBox / sing-box clients:
 * vless://, vmess:// (base64 JSON + URL style), trojan://, ss:// (SIP002 + legacy),
 * hysteria2:// / hy2://, wireguard://, WireGuard .conf text, .ovpn text.
 */
public final class ShareLinkParser {
    private ShareLinkParser() {}

    // ---------------- dispatch ----------------

    public static ServerProfile parse(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        String lower = s.toLowerCase();
        try {
            if (lower.startsWith("vless://")) return parseVless(s);
            if (lower.startsWith("vmess://")) return parseVmess(s);
            if (lower.startsWith("trojan://")) return parseTrojan(s);
            if (lower.startsWith("ss://")) return parseShadowsocks(s);
            if (lower.startsWith("hysteria2://") || lower.startsWith("hy2://")) return parseHysteria2(s);
            if (lower.startsWith("wireguard://") || lower.startsWith("wg://")) return parseWireGuardUrl(s);
            if (s.contains("[Interface]") && s.contains("[Peer]")) return WireGuardConfParser.parse(s);
            if (lower.contains("remote ") && (lower.contains("client") || lower.contains("proto "))) {
                ServerProfile p = new ServerProfile(Protocol.OPENVPN_UDP);
                p.ovpnConfig = s; p.name = "OpenVPN import";
                parseOvpnRemote(p, s);
                return p;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** Import many links at once (subscription body or multi-line paste). */
    public static List<ServerProfile> parseMany(String text) {
        List<ServerProfile> out = new ArrayList<>();
        if (text == null) return out;
        String body = text.trim();
        // subscription bodies are usually one base64 blob
        if (!body.contains("://") && !body.contains("[Interface]")) {
            String dec = Base64Util.decodeToString(body);
            if (dec.contains("://")) body = dec;
        }
        // .ovpn / wg conf are multi-line documents, not per-line links
        if (body.contains("[Interface]") || (body.contains("remote ") && body.split("\n").length > 3 && !body.contains("://"))) {
            ServerProfile p = parse(body);
            if (p != null) out.add(p);
            return out;
        }
        for (String line : body.split("[\\r\\n]+")) {
            ServerProfile p = parse(line);
            if (p != null) out.add(p);
        }
        return out;
    }

    // ---------------- helpers ----------------

    private static String urlDecode(String s) {
        if (s == null) return "";
        try { return URLDecoder.decode(s, "UTF-8"); } catch (UnsupportedEncodingException e) { return s; }
    }

    private static Map<String, String> queryMap(String q) {
        Map<String, String> m = new LinkedHashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) m.put(urlDecode(pair), "");
            else m.put(urlDecode(pair.substring(0, i)), urlDecode(pair.substring(i + 1)));
        }
        return m;
    }

    /** Split scheme://userinfo@host:port?query#name into parts. Handles IPv6 [..]. */
    private static class UrlParts {
        String userinfo = "", host = "", name = "";
        int port = 0;
        Map<String, String> query = new LinkedHashMap<>();
    }

    private static UrlParts splitUrl(String url, String scheme) {
        UrlParts u = new UrlParts();
        String s = url.substring(scheme.length());
        int hash = s.indexOf('#');
        if (hash >= 0) { u.name = urlDecode(s.substring(hash + 1)); s = s.substring(0, hash); }
        int q = s.indexOf('?');
        if (q >= 0) { u.query = queryMap(s.substring(q + 1)); s = s.substring(0, q); }
        int at = s.lastIndexOf('@');
        String hostport;
        if (at >= 0) { u.userinfo = urlDecode(s.substring(0, at)); hostport = s.substring(at + 1); }
        else hostport = s;
        hostport = hostport.replace("/", "");
        if (hostport.startsWith("[")) {
            int close = hostport.indexOf(']');
            u.host = hostport.substring(1, close);
            if (close + 1 < hostport.length() && hostport.charAt(close + 1) == ':')
                u.port = parseInt(hostport.substring(close + 2), 0);
        } else {
            int colon = hostport.lastIndexOf(':');
            if (colon >= 0) { u.host = hostport.substring(0, colon); u.port = parseInt(hostport.substring(colon + 1), 0); }
            else u.host = hostport;
        }
        return u;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static void applyTransport(ServerProfile p, Map<String, String> q) {
        String type = q.getOrDefault("type", q.getOrDefault("net", "tcp")).toLowerCase();
        p.network = type;
        p.path = q.getOrDefault("path", "");
        p.hostHeader = q.getOrDefault("host", "");
        p.serviceName = q.getOrDefault("serviceName", "");
        p.security = q.getOrDefault("security", q.getOrDefault("tls", ""));
        p.sni = q.getOrDefault("sni", q.getOrDefault("peer", ""));
        if (p.sni.isEmpty() && !p.hostHeader.isEmpty() && (p.network.equals("ws") || p.network.equals("tcp"))) {
            // host header is not SNI for ws; leave sni empty in that case
        }
        p.flow = q.getOrDefault("flow", "");
        String ins = q.getOrDefault("allowInsecure", q.getOrDefault("insecure", ""));
        p.allowInsecure = "1".equals(ins) || "true".equalsIgnoreCase(ins);
    }

    private static Protocol xrayProtocol(String base, String network) {
        switch (base) {
            case "vless":
                if ("grpc".equals(network)) return Protocol.VLESS_GRPC;
                if ("splithttp".equals(network) || "xhttp".equals(network)) return Protocol.VLESS_XHTTP;
                if ("ws".equals(network)) return Protocol.VLESS_WS;
                return Protocol.VLESS_TCP;
            case "vmess":
                return "ws".equals(network) ? Protocol.VMESS_WS : Protocol.VMESS_TCP;
            case "trojan":
                return "ws".equals(network) ? Protocol.TROJAN_WS : Protocol.TROJAN_TCP;
            default: return Protocol.VLESS_TCP;
        }
    }

    // ---------------- vless ----------------

    public static ServerProfile parseVless(String url) {
        UrlParts u = splitUrl(url, "vless://");
        ServerProfile p = new ServerProfile();
        p.uuid = u.userinfo;
        p.host = u.host; p.port = u.port > 0 ? u.port : 443;
        p.name = u.name;
        applyTransport(p, u.query);
        if (p.security.isEmpty()) p.security = "tls";
        p.protocol = xrayProtocol("vless", p.network);
        return p;
    }

    // ---------------- trojan ----------------

    public static ServerProfile parseTrojan(String url) {
        UrlParts u = splitUrl(url, "trojan://");
        ServerProfile p = new ServerProfile();
        p.password = u.userinfo;
        p.host = u.host; p.port = u.port > 0 ? u.port : 443;
        p.name = u.name;
        applyTransport(p, u.query);
        if (p.security.isEmpty()) p.security = "tls";
        if (p.sni.isEmpty()) p.sni = u.query.getOrDefault("peer", "");
        p.protocol = xrayProtocol("trojan", p.network);
        return p;
    }

    // ---------------- vmess ----------------

    public static ServerProfile parseVmess(String url) {
        String body = url.substring("vmess://".length()).trim();
        // URL style: vmess://uuid@host:port?query#name
        if (body.contains("@")) {
            UrlParts u = splitUrl(url, "vmess://");
            ServerProfile p = new ServerProfile();
            p.uuid = u.userinfo; p.host = u.host; p.port = u.port > 0 ? u.port : 443; p.name = u.name;
            applyTransport(p, u.query);
            p.alterId = parseInt(u.query.getOrDefault("alterId", "0"), 0);
            p.protocol = xrayProtocol("vmess", p.network);
            return p;
        }
        // classic base64 JSON style
        String json = Base64Util.decodeToString(body);
        ServerProfile p = new ServerProfile();
        p.name = JsonUtil.getString(json, "ps");
        p.host = JsonUtil.getString(json, "add");
        p.port = JsonUtil.getInt(json, "port", 443);
        p.uuid = JsonUtil.getString(json, "id");
        p.alterId = JsonUtil.getInt(json, "aid", 0);
        p.network = JsonUtil.getString(json, "net");
        if (p.network.isEmpty()) p.network = "tcp";
        p.path = JsonUtil.getString(json, "path");
        p.hostHeader = JsonUtil.getString(json, "host");
        p.security = JsonUtil.getString(json, "tls");
        p.sni = JsonUtil.getString(json, "sni");
        p.protocol = xrayProtocol("vmess", p.network);
        return p;
    }

    // ---------------- shadowsocks ----------------

    public static ServerProfile parseShadowsocks(String url) {
        // forms:
        // ss://method:password@host:port#name
        // ss://base64(method:password)@host:port#name
        // ss://base64(method:password@host:port)#name
        String s = url.trim();
        String name = "";
        int hash = s.indexOf('#');
        if (hash >= 0) { name = urlDecode(s.substring(hash + 1)); s = s.substring(0, hash); }
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q); // plugin params ignored in Phase A
        String body = s.substring("ss://".length());
        String userinfo, hostport;
        int at = body.lastIndexOf('@');
        if (at >= 0) { userinfo = body.substring(0, at); hostport = body.substring(at + 1); }
        else { // fully encoded
            String dec = Base64Util.decodeToString(body);
            int a2 = dec.lastIndexOf('@');
            if (a2 < 0) return null;
            userinfo = dec.substring(0, a2); hostport = dec.substring(a2 + 1);
        }
        String methodPass = userinfo.contains(":") ? userinfo : Base64Util.decodeToString(userinfo);
        methodPass = urlDecode(methodPass);
        int colon = methodPass.indexOf(':');
        ServerProfile p = new ServerProfile(Protocol.SHADOWSOCKS);
        if (colon >= 0) { p.method = methodPass.substring(0, colon); p.password = methodPass.substring(colon + 1); }
        else p.password = methodPass;
        hostport = hostport.replace("/", "");
        int c2 = hostport.lastIndexOf(':');
        if (c2 >= 0) { p.host = hostport.substring(0, c2); p.port = parseInt(hostport.substring(c2 + 1), 8388); }
        else { p.host = hostport; p.port = 8388; }
        p.name = name;
        if (p.method.isEmpty()) p.method = "aes-256-gcm";
        return p;
    }

    // ---------------- hysteria2 ----------------

    public static ServerProfile parseHysteria2(String url) {
        String scheme = url.toLowerCase().startsWith("hy2://") ? "hy2://" : "hysteria2://";
        UrlParts u = splitUrl(url, scheme);
        ServerProfile p = new ServerProfile(Protocol.HYSTERIA2);
        // userinfo may be password or user:password
        int colon = u.userinfo.indexOf(':');
        if (colon >= 0) { p.username = u.userinfo.substring(0, colon); p.password = u.userinfo.substring(colon + 1); }
        else p.password = u.userinfo;
        p.host = u.host; p.port = u.port > 0 ? u.port : 4443;
        p.name = u.name;
        p.sni = u.query.getOrDefault("sni", "");
        String ins = u.query.getOrDefault("insecure", "");
        p.allowInsecure = "1".equals(ins) || "true".equalsIgnoreCase(ins);
        p.hyObfs = u.query.getOrDefault("obfs", "");
        p.hyObfsPassword = u.query.getOrDefault("obfs-password", "");
        p.security = "tls";
        return p;
    }

    // ---------------- wireguard url ----------------

    public static ServerProfile parseWireGuardUrl(String url) {
        String scheme = url.toLowerCase().startsWith("wg://") ? "wg://" : "wireguard://";
        UrlParts u = splitUrl(url, scheme);
        ServerProfile p = new ServerProfile(Protocol.WIREGUARD);
        p.wgPrivateKey = u.userinfo;
        p.host = u.host; p.port = u.port > 0 ? u.port : 51820;
        p.name = u.name;
        p.wgPublicKey = u.query.getOrDefault("publickey", "");
        p.wgPresharedKey = u.query.getOrDefault("presharedkey", "");
        p.wgAddress = u.query.getOrDefault("address", "");
        p.wgReserved = u.query.getOrDefault("reserved", "");
        p.wgMtu = parseInt(u.query.getOrDefault("mtu", "0"), 0);
        return p;
    }

    // ---------------- openvpn remote ----------------

    private static void parseOvpnRemote(ServerProfile p, String text) {
        boolean tcp = false;
        for (String line : text.split("[\\r\\n]+")) {
            String t = line.trim().toLowerCase();
            if (t.startsWith("proto ") && t.contains("tcp")) tcp = true;
            if (t.startsWith("remote ")) {
                String[] parts = t.split("\\s+");
                if (parts.length >= 2) p.host = parts[1];
                if (parts.length >= 3) p.port = parseInt(parts[2], 1194);
                if (parts.length >= 4 && parts[3].contains("tcp")) tcp = true;
            }
        }
        if (p.port == 0) p.port = 1194;
        p.protocol = tcp ? Protocol.OPENVPN_TCP : Protocol.OPENVPN_UDP;
    }

    // ---------------- builders for our own VPS (Jepong VPS quick-add + share) ----------------

    /** Build a share link back from a profile (vless/vmess-url/trojan/ss/hy2). */
    public static String toShareLink(ServerProfile p) {
        if (p == null || p.protocol == null) return "";
        String name = p.name == null ? "" : p.name.replace(" ", "%20");
        switch (p.protocol) {
            case VLESS_WS: case VLESS_GRPC: case VLESS_XHTTP: case VLESS_TCP: {
                String type = p.protocol == Protocol.VLESS_GRPC ? "grpc"
                    : p.protocol == Protocol.VLESS_XHTTP ? "xhttp"
                    : p.protocol == Protocol.VLESS_WS ? "ws" : "tcp";
                StringBuilder q = new StringBuilder("?type=").append(type)
                    .append("&security=").append(p.security.isEmpty() ? "tls" : p.security);
                if (!p.path.isEmpty()) q.append("&path=").append(p.path.replace("/", "%2F"));
                if (!p.hostHeader.isEmpty()) q.append("&host=").append(p.hostHeader);
                if (!p.sni.isEmpty()) q.append("&sni=").append(p.sni);
                if (!p.serviceName.isEmpty()) q.append("&serviceName=").append(p.serviceName);
                return "vless://" + p.uuid + "@" + p.host + ":" + p.port + q + "#" + name;
            }
            case TROJAN_WS: case TROJAN_TCP: {
                StringBuilder q = new StringBuilder("?security=tls");
                if (p.protocol == Protocol.TROJAN_WS) q.append("&type=ws&path=").append(p.path.replace("/", "%2F"));
                if (!p.sni.isEmpty()) q.append("&sni=").append(p.sni);
                return "trojan://" + p.password + "@" + p.host + ":" + p.port + q + "#" + name;
            }
            case SHADOWSOCKS:
                return "ss://" + Base64Util.encodeUrlSafe(p.method + ":" + p.password) + "@" + p.host + ":" + p.port + "#" + name;
            case HYSTERIA2:
                return "hysteria2://" + p.password + "@" + p.host + ":" + p.port + "?sni=" + p.sni
                    + (p.allowInsecure ? "&insecure=1" : "") + "#" + name;
            case VMESS_WS: case VMESS_TCP: {
                // URL style (parseVmess handles the uuid@host form)
                String type = p.protocol == Protocol.VMESS_WS ? "ws" : "tcp";
                StringBuilder q = new StringBuilder("?type=").append(type)
                    .append("&alterId=").append(p.alterId)
                    .append("&security=").append(p.security.isEmpty() ? "tls" : p.security);
                if (!p.path.isEmpty()) q.append("&path=").append(p.path.replace("/", "%2F"));
                if (!p.hostHeader.isEmpty()) q.append("&host=").append(p.hostHeader);
                if (!p.sni.isEmpty()) q.append("&sni=").append(p.sni);
                return "vmess://" + p.uuid + "@" + p.host + ":" + p.port + q + "#" + name;
            }
            case WIREGUARD: {
                // keys are base64 and may contain + / = -> percent-encode for URL safety
                StringBuilder q = new StringBuilder("?publickey=").append(urlEnc(p.wgPublicKey));
                if (!p.wgPresharedKey.isEmpty()) q.append("&presharedkey=").append(urlEnc(p.wgPresharedKey));
                if (!p.wgAddress.isEmpty()) q.append("&address=").append(urlEnc(p.wgAddress));
                if (p.wgMtu > 0) q.append("&mtu=").append(p.wgMtu);
                if (!p.wgReserved.isEmpty()) q.append("&reserved=").append(urlEnc(p.wgReserved));
                return "wireguard://" + urlEnc(p.wgPrivateKey) + "@" + p.host + ":" + p.port + q + "#" + name;
            }
            default:
                return "";
        }
    }

    /** Percent-encode the characters that would corrupt a URL userinfo/query value. */
    private static String urlEnc(String s) {
        if (s == null) return "";
        return s.replace("%", "%25").replace("+", "%2B").replace("/", "%2F")
                .replace("@", "%40").replace("?", "%3F").replace("#", "%23")
                .replace("&", "%26").replace("=", "%3D").replace(" ", "%20");
    }
}
