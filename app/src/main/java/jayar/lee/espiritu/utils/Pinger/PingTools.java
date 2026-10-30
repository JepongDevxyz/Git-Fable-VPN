package jayar.lee.espiritu.utils.Pinger;

import java.io.IOException;
import java.net.InetAddress;

/**
* Created by mat on 09/12/15.
*/
public class PingTools {

    private PingTools() {
    }


    public static PingResult doPing(InetAddress ia, PingOptions pingOptions) {

        try {
            return PingTools.doNativePing(ia, pingOptions);
        } catch (InterruptedException e) {
            PingResult pingResult = new PingResult(ia);
            pingResult.isReachable = false;
            pingResult.error = "Interrupted";
            return pingResult;
        } catch (Exception ignored) {
        }

        return PingTools.doJavaPing(ia, pingOptions);
    }


    public static PingResult doNativePing(InetAddress ia, PingOptions pingOptions) throws IOException, InterruptedException {
        return PingNative.ping(ia, pingOptions);
    }

    public static PingResult doJavaPing(InetAddress ia, PingOptions pingOptions) {
        PingResult pingResult = new PingResult(ia);

        if (ia == null) {
            pingResult.isReachable = false;
            return pingResult;
        }

        try {
            long startTime = System.nanoTime();
            final boolean reached = ia.isReachable(null, pingOptions.getTimeToLive(), pingOptions.getTimeoutMillis());
            pingResult.timeTaken = (System.nanoTime() - startTime) / 1e6f;
            pingResult.isReachable = reached;
            if (!reached) pingResult.error = "Timed Out";
        } catch (IOException e) {
            pingResult.isReachable = false;
            pingResult.error = "IOException: " + e.getMessage();
        }
        return pingResult;
    }

}

