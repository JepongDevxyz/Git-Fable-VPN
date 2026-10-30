package jayar.lee.espiritu.utils.Pinger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class PingNative {

    private PingNative() {
    }

    public static PingResult ping(InetAddress host, PingOptions pingOptions) throws IOException, InterruptedException {
        PingResult pingResult = new PingResult(host);

        if (host == null) {
            pingResult.isReachable = false;
            return pingResult;
        }

        StringBuilder echo = new StringBuilder();
        Runtime runtime = Runtime.getRuntime();

        int timeoutSeconds = Math.max(pingOptions.getTimeoutMillis() / 1000, 1);
        int ttl = Math.max(pingOptions.getTimeToLive(), 1);

        String address = host.getHostAddress();
        String pingCommand = "ping";

        if (address != null) {
            if (IPTools.isIPv6Address(address)) {
                pingCommand = "ping6";
            } else {
                IPTools.isIPv4Address(address);
            }
        } else {
            address = host.getHostName();
        }

        Process proc = runtime.exec(pingCommand + " -c 1 -W " + timeoutSeconds + " -t " + ttl + " " + address);
        proc.waitFor();
        int exit = proc.exitValue();
        String pingError;
        switch (exit) {
            case 0:
                InputStreamReader reader = new InputStreamReader(proc.getInputStream());
                BufferedReader buffer = new BufferedReader(reader);
                String line;
                while ((line = buffer.readLine()) != null) {
                    echo.append(line).append("\n");
                }
                return getPingStats(pingResult, echo.toString());
            case 1:
                pingError = "failed, exit = 1";
                break;
            default:
                pingError = "error, exit = 2";
                break;
        }
        pingResult.error = pingError;
        proc.destroy();
        return pingResult;
    }

    public static PingResult getPingStats(PingResult pingResult, String s) {
        String pingError;
        if (s.contains("0% packet loss")) {
            int start = s.indexOf("/mdev = ");
            int end = s.indexOf(" ms\n", start);
            pingResult.fullString = s;
            if (start == -1 || end == -1) {
                pingError = "Error: " + s;
            } else {
                s = s.substring(start + 8, end);
                String stats[] = s.split("/");
                pingResult.isReachable = true;
                pingResult.result = s;
                pingResult.timeTaken = Float.parseFloat(stats[1]);
                return pingResult;
            }
        } else if (s.contains("100% packet loss")) {
            pingError = "100% packet loss";
        } else if (s.contains("% packet loss")) {
            pingError = "partial packet loss";
        } else if (s.contains("unknown host")) {
            pingError = "unknown host";
        } else {
            pingError = "unknown error in getPingStats";
        }
        pingResult.error = pingError;
        return pingResult;
    }
}

