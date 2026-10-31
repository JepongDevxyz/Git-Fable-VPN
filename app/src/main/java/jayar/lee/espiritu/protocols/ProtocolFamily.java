package jayar.lee.espiritu.protocols;

import java.util.ArrayList;
import java.util.List;

/**
 * Protocol FAMILY grouping for the Settings protocol selection
 * (her order, 2026-11-01: "kapag OpenVPN lang naka-select na protocol,
 * yun lang lalabas; kung All naman, lahat sila").
 *
 * The Settings screen stores ONE representative protocol id
 * (TunnelSettings.KEY_DEFAULT_PROTOCOL, e.g. "VLESS_WS" for the whole
 * VLESS row, "OPENVPN_UDP"/"OPENVPN_TCP" for OpenVPN). The Servers list
 * must show every profile whose protocol belongs to the same FAMILY as
 * the selected representative. "AUTO" (or unset/unknown) = no filter.
 *
 * Mapping table (settings representative -> profiles shown):
 *   AUTO / unset            -> ALL protocols (no filtering)
 *   OPENVPN_UDP, OPENVPN_TCP-> OPENVPN_UDP + OPENVPN_TCP        (OPENVPN)
 *   VLESS_WS (row covers    -> VLESS_WS + VLESS_GRPC + VLESS_XHTTP
 *      gRPC / XHTTP too)       + VLESS_TCP                      (VLESS)
 *   VMESS_WS                -> VMESS_WS + VMESS_TCP             (VMESS)
 *   TROJAN_WS               -> TROJAN_WS + TROJAN_TCP           (TROJAN)
 *   SSH_WS                  -> SSH_WS + SSH_DIRECT              (SSH)
 *   SLOWDNS_SSH             -> SLOWDNS_SSH only                 (SLOWDNS)
 *   SHADOWSOCKS             -> SHADOWSOCKS                      (SHADOWSOCKS)
 *   WIREGUARD               -> WIREGUARD                        (WIREGUARD)
 *   HYSTERIA2               -> HYSTERIA2                        (HYSTERIA2)
 *   UDP_CUSTOM              -> UDP_CUSTOM                       (UDP_CUSTOM)
 *   L2TP_IPSEC              -> L2TP_IPSEC                       (L2TP_IPSEC)
 *   IKEV2                   -> IKEV2                            (IKEV2)
 *   PPTP_LEGACY             -> PPTP_LEGACY                      (PPTP_LEGACY)
 *
 * Pure Java (no Android deps) so it unit-tests with plain javac.
 */
public final class ProtocolFamily {
    private ProtocolFamily() {}

    public static final String OPENVPN = "OPENVPN";
    public static final String VLESS = "VLESS";
    public static final String VMESS = "VMESS";
    public static final String TROJAN = "TROJAN";
    public static final String SSH = "SSH";
    public static final String SLOWDNS = "SLOWDNS";
    public static final String SHADOWSOCKS = "SHADOWSOCKS";
    public static final String WIREGUARD = "WIREGUARD";
    public static final String HYSTERIA2 = "HYSTERIA2";
    public static final String UDP_CUSTOM = "UDP_CUSTOM";
    public static final String L2TP_IPSEC = "L2TP_IPSEC";
    public static final String IKEV2 = "IKEV2";
    public static final String PPTP_LEGACY = "PPTP_LEGACY";

    /** Family key for one concrete protocol; null for a null protocol. */
    public static String familyOf(Protocol p) {
        if (p == null) return null;
        switch (p) {
            case OPENVPN_UDP: case OPENVPN_TCP: return OPENVPN;
            case VLESS_WS: case VLESS_GRPC: case VLESS_XHTTP: case VLESS_TCP: return VLESS;
            case VMESS_WS: case VMESS_TCP: return VMESS;
            case TROJAN_WS: case TROJAN_TCP: return TROJAN;
            case SSH_DIRECT: case SSH_WS: return SSH;
            case SLOWDNS_SSH: return SLOWDNS;
            case SHADOWSOCKS: return SHADOWSOCKS;
            case WIREGUARD: return WIREGUARD;
            case HYSTERIA2: return HYSTERIA2;
            case UDP_CUSTOM: return UDP_CUSTOM;
            case L2TP_IPSEC: return L2TP_IPSEC;
            case IKEV2: return IKEV2;
            case PPTP_LEGACY: return PPTP_LEGACY;
            default: return p.name();
        }
    }

    /**
     * Family key for a persisted Settings selection.
     * Returns null when the selection means "show all" (AUTO, null,
     * empty, or an unrecognized id — an unknown id must never hide the
     * whole library, so it degrades to All).
     */
    public static String familyForSelection(String selectionId) {
        if (selectionId == null) return null;
        String id = selectionId.trim();
        if (id.isEmpty() || "AUTO".equalsIgnoreCase(id)) return null;
        try {
            return familyOf(Protocol.valueOf(id.toUpperCase()));
        } catch (Exception e) {
            return null;
        }
    }

    /** True when a profile with protocol {@code p} passes the selection filter. */
    public static boolean matches(String selectionId, Protocol p) {
        String fam = familyForSelection(selectionId);
        if (fam == null) return true;      // All
        if (p == null) return false;       // filtered view: unknown protocol hidden
        return fam.equals(familyOf(p));
    }

    /** The profiles visible under a Settings selection, order preserved. */
    public static List<ServerProfile> filter(List<ServerProfile> profiles, String selectionId) {
        List<ServerProfile> out = new ArrayList<>();
        if (profiles == null) return out;
        for (ServerProfile p : profiles) {
            if (p != null && matches(selectionId, p.protocol)) out.add(p);
        }
        return out;
    }

    /** Human label for the active filter, e.g. "OpenVPN", "All protocols". */
    public static String selectionLabel(String selectionId) {
        String fam = familyForSelection(selectionId);
        if (fam == null) return "All protocols";
        switch (fam) {
            case OPENVPN: return "OpenVPN";
            case VLESS: return "VLESS";
            case VMESS: return "VMess";
            case TROJAN: return "Trojan";
            case SSH: return "SSH";
            case SLOWDNS: return "SlowDNS";
            case SHADOWSOCKS: return "Shadowsocks";
            case WIREGUARD: return "WireGuard";
            case HYSTERIA2: return "Hysteria2";
            case UDP_CUSTOM: return "UDP Custom";
            case L2TP_IPSEC: return "L2TP/IPSec";
            case IKEV2: return "IKEv2";
            case PPTP_LEGACY: return "PPTP";
            default: return fam;
        }
    }
}
