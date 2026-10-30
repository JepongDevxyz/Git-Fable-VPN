package jayar.lee.espiritu.protocols;

/** Parser for WireGuard .conf files — the format our VPS script saves to
 *  /etc/jepongvpn/wireguard-client.conf (Step 11: WireGuard 51820/udp). */
public final class WireGuardConfParser {
    private WireGuardConfParser() {}

    public static ServerProfile parse(String text) {
        ServerProfile p = new ServerProfile(Protocol.WIREGUARD);
        p.name = "WireGuard";
        String section = "";
        for (String raw : text.split("[\\r\\n]+")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.equalsIgnoreCase("[Interface]")) { section = "i"; continue; }
            if (line.equalsIgnoreCase("[Peer]")) { section = "p"; continue; }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim().toLowerCase();
            String val = line.substring(eq + 1).trim();
            if ("i".equals(section)) {
                switch (key) {
                    case "privatekey": p.wgPrivateKey = val; break;
                    case "address": p.wgAddress = val; break;
                    case "dns": p.wgDns = val; break;
                    case "mtu": try { p.wgMtu = Integer.parseInt(val); } catch (Exception ignored) {} break;
                }
            } else if ("p".equals(section)) {
                switch (key) {
                    case "publickey": p.wgPublicKey = val; break;
                    case "presharedkey": p.wgPresharedKey = val; break;
                    case "allowedips": p.wgAllowedIPs = val; break;
                    case "endpoint": {
                        int c = val.lastIndexOf(':');
                        if (c >= 0) { p.host = val.substring(0, c); try { p.port = Integer.parseInt(val.substring(c + 1)); } catch (Exception ignored) {} }
                        else p.host = val;
                        break;
                    }
                }
            }
        }
        if (p.port == 0) p.port = 51820;
        return p;
    }

    /** Render a profile back to .conf text (for export / WireGuard backend). */
    public static String toConf(ServerProfile p) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Interface]\n");
        sb.append("PrivateKey = ").append(p.wgPrivateKey).append("\n");
        if (!p.wgAddress.isEmpty()) sb.append("Address = ").append(p.wgAddress).append("\n");
        if (!p.wgDns.isEmpty()) sb.append("DNS = ").append(p.wgDns).append("\n");
        if (p.wgMtu > 0) sb.append("MTU = ").append(p.wgMtu).append("\n");
        sb.append("\n[Peer]\n");
        sb.append("PublicKey = ").append(p.wgPublicKey).append("\n");
        if (!p.wgPresharedKey.isEmpty()) sb.append("PresharedKey = ").append(p.wgPresharedKey).append("\n");
        sb.append("AllowedIPs = ").append(p.wgAllowedIPs.isEmpty() ? "0.0.0.0/0" : p.wgAllowedIPs).append("\n");
        sb.append("Endpoint = ").append(p.host).append(":").append(p.port > 0 ? p.port : 51820).append("\n");
        sb.append("PersistentKeepalive = 25\n");
        return sb.toString();
    }
}
