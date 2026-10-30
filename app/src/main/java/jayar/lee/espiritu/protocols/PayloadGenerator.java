package jayar.lee.espiritu.protocols;

/**
 * HTTP-injector payload builder (SocksHttp family concept, re-implemented
 * from scratch for Git Fable VPN — no Trinet code). Produces a payload
 * TEMPLATE: the injector substitutes the placeholders at connect time:
 *   [host] [port] [host_port] [protocol] [crlf] [ua]
 *
 * Methods (mirroring the reference generator):
 *   NORMAL — full request + headers
 *   SPLIT  — request line, then a [split] marker, then the headers
 *            (the tunnel sends the two parts separately)
 *   DIRECT — minimal CONNECT line only
 *   ROTATE — NORMAL with a [rotate=h1,h2,...] marker in front
 * A non-empty rawPayload always wins and is returned unchanged.
 *
 * Pure Java (no Android deps) so it unit-tests with plain javac.
 */
public final class PayloadGenerator {
    private PayloadGenerator() {}

    public enum Method { NORMAL, SPLIT, DIRECT, ROTATE }

    public static class Inputs {
        public Method method = Method.NORMAL;
        public String requestMethod = "GET";   // GET / CONNECT / POST ...
        public String frontQuery = "";         // e.g. "http://" (default when empty)
        public String backQuery = "";          // appended after [host_port]
        public String onlineHost = "";         // X-Online-Host / Host header
        public String reverseProxy = "";
        public String userAgent = "";          // empty -> [ua] placeholder
        public String forwardHost = "";
        public boolean keepAlive = true;
        public String referer = "";
        public String extraHeader = "";        // raw extra header lines, one per line
        public String rawPayload = "";         // override: returned unchanged
        public String host = "";               // informational (template keeps [host])
        public int port = 0;                   // informational (template keeps [port])
        public String sni = "";                // Host-header fallback
        public String rotateList = "";         // comma-separated hosts for ROTATE
    }

    public static String build(Inputs in) {
        if (in == null) in = new Inputs();
        if (in.rawPayload != null && !in.rawPayload.trim().isEmpty()) return in.rawPayload;
        Method m = in.method == null ? Method.NORMAL : in.method;

        if (m == Method.DIRECT) {
            return "CONNECT [host_port] [protocol][crlf]Host: [host][crlf][crlf]";
        }

        String reqMethod = nz(in.requestMethod).trim().isEmpty() ? "GET" : in.requestMethod.trim();
        String front = nz(in.frontQuery).trim().isEmpty() ? "http://" : in.frontQuery;
        StringBuilder sb = new StringBuilder();
        if (m == Method.ROTATE) {
            String list = nz(in.rotateList).trim().isEmpty() ? "[host_port]" : in.rotateList.trim();
            sb.append("[rotate=").append(list).append("]");
        }
        // request line
        sb.append(reqMethod).append(' ').append(front).append("[host_port]")
          .append(nz(in.backQuery)).append(" [protocol][crlf]");
        if (m == Method.SPLIT) sb.append("[split]");
        // headers, fixed order
        String hostHeader = !nz(in.onlineHost).isEmpty() ? in.onlineHost
            : (!nz(in.sni).isEmpty() ? in.sni : "[host]");
        sb.append("Host: ").append(hostHeader).append("[crlf]");
        if (!nz(in.onlineHost).isEmpty())
            sb.append("X-Online-Host: ").append(in.onlineHost).append("[crlf]");
        sb.append("User-Agent: ").append(nz(in.userAgent).isEmpty() ? "[ua]" : in.userAgent).append("[crlf]");
        if (!nz(in.forwardHost).isEmpty())
            sb.append("X-Forward-Host: ").append(in.forwardHost).append("[crlf]");
        if (!nz(in.reverseProxy).isEmpty())
            sb.append("X-Reverse-Proxy: ").append(in.reverseProxy).append("[crlf]");
        sb.append("Connection: ").append(in.keepAlive ? "Keep-Alive" : "close").append("[crlf]");
        if (!nz(in.referer).isEmpty())
            sb.append("Referer: ").append(in.referer).append("[crlf]");
        if (!nz(in.extraHeader).trim().isEmpty()) {
            for (String line : in.extraHeader.split("[\\r\\n]+")) {
                String t = line.trim();
                if (!t.isEmpty()) sb.append(t).append("[crlf]");
            }
        }
        sb.append("[crlf]");
        return sb.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
