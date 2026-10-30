package jayar.lee.espiritu.utils.Pinger;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
* Created by mat on 09/12/15.
*/
public class Ping {

    private Ping() {
    }

    public interface PingListener {
        void onResult(PingResult pingResult);
        void onFinished(PingStats pingStats);
        void onError(Exception e);
    }

    private String addressString = null;
    private InetAddress address;
    private final PingOptions pingOptions = new PingOptions();
    private int delayBetweenScansMillis = 0;
    private int times = 1;
    private boolean cancelled = false;

    public static Ping onAddress(String address) {
        Ping ping = new Ping();
        ping.setAddressString(address);
        return ping;
    }

    public static Ping onAddress(InetAddress ia) {
        Ping ping = new Ping();
        ping.setAddress(ia);
        return ping;
    }

    public Ping setTimeOutMillis(int timeOutMillis) {
        if (timeOutMillis < 0) throw new IllegalArgumentException("Times cannot be less than 0");
        pingOptions.setTimeoutMillis(timeOutMillis);
        return this;
    }


    public Ping setTimeToLive(int timeToLive) {
        if (timeToLive < 1) throw new IllegalArgumentException("TTL cannot be less than 1");
        pingOptions.setTimeToLive(timeToLive);
        return this;
    }

    public Ping setTimes(int noTimes) {
        if (noTimes < 0) throw new IllegalArgumentException("Times cannot be less than 0");
        this.times = noTimes;
        return this;
    }

    private void setAddress(InetAddress address) {
        this.address = address;
    }

    private void setAddressString(String addressString) {
        this.addressString = addressString;
    }

    private void resolveAddressString() throws UnknownHostException {
        if (address == null && addressString != null) {
            address = InetAddress.getByName(addressString);
        }
    }

    public void cancel() {
        this.cancelled = true;
    }


    public PingResult doPing() throws UnknownHostException {
        cancelled = false;
        resolveAddressString();
        return PingTools.doPing(address, pingOptions);
    }

    public Ping doPing(final PingListener pingListener) {
        new Thread(new Runnable() {
                @Override
                public void run(){
                    try {
                        resolveAddressString();
                    } catch (UnknownHostException e) {
                        pingListener.onError(e);
                        return;
                    }
                    if (address == null) {
                        pingListener.onError(new NullPointerException("Address is null"));
                        return;
                    }
                    long pingsCompleted = 0;
                    long noLostPackets = 0;
                    float totalPingTime = 0;
                    float minPingTime = -1;
                    float maxPingTime = -1;
                    cancelled = false;
                    int noPings = times;
                    while (noPings > 0 || times == 0) {
                        PingResult pingResult = PingTools.doPing(address, pingOptions);

                        if (pingListener != null) {
                            pingListener.onResult(pingResult);
                        }

                        // Update ping stats
                        pingsCompleted++;

                        if (pingResult.hasError()) {
                            noLostPackets++;
                        } else {
                            float timeTaken = pingResult.getTimeTaken();
                            totalPingTime += timeTaken;
                            if (maxPingTime == -1 || timeTaken > maxPingTime) maxPingTime = timeTaken;
                            if (minPingTime == -1 || timeTaken < minPingTime) minPingTime = timeTaken;
                        }

                        noPings--;
                        if (cancelled) break;

                        try {
                            Thread.sleep(delayBetweenScansMillis);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (pingListener != null) {
                        pingListener.onFinished(new PingStats(address, pingsCompleted, noLostPackets,
                                                              totalPingTime, minPingTime, maxPingTime));
                    }
                }
            }).start();
        return this;
    }

}
