package jayar.lee.espiritu.core.vpnutils;

import static java.lang.Runtime.getRuntime;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VpnUtils {

    public static int findProcessId(String command) throws IOException {

        String[] cmds = {"ps -ef","ps -A","toolbox ps"};

        for (String cmd : cmds) {
            Process procPs = getRuntime().exec(cmd);

            BufferedReader reader = new BufferedReader(new InputStreamReader(procPs.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("PID") && line.contains(command)) {
                    String[] lineParts = line.split("\\s+");
                    try {
                        return Integer.parseInt(lineParts[1]); //for most devices it is the second
                    } catch (NumberFormatException e) {
                        return Integer.parseInt(lineParts[0]); //but for samsungs it is the first
                    } finally {
                        try {
                            procPs.destroy();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        return -1;
    }

    public static void killProcess(File fileProcBin) throws Exception {
        killProcess(fileProcBin, "-9"); // this is -KILL
    }

    public static void killProcess(File fileProcBin, String signal) throws Exception {

        int procId;
        int killAttempts = 0;

        while ((procId = findProcessId(fileProcBin.getName())) != -1) {
            killAttempts++;
            String pidString = String.valueOf(procId);

            String[] cmds = {"","busybox ","toolbox "};

            for (String cmd : cmds) {
                try {
                    getRuntime().exec(cmd + "killall " + signal + " " + fileProcBin.getName
                            ());
                } catch (IOException ignored) {
                }
                try {
                    getRuntime().exec(cmd + "killall " + signal + " " + fileProcBin.getCanonicalPath());
                } catch (IOException ignored) {
                }
            }

            killProcess(pidString, signal);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignored
            }

            if (killAttempts > 4)
                throw new Exception("Cannot kill: " + fileProcBin.getAbsolutePath());
        }

    }

    public static void killProcess(String pidString, String signal) {

        String[] cmds = {"","toolbox ","busybox "};

        for (String cmd : cmds) {
            try {
                getRuntime().exec(cmd + "kill " + signal + " " + pidString);
            } catch (IOException ioe) {
                android.util.Log.d("", String.format("error killing process: %s, %s", pidString, ioe.getMessage()));
            }
        }
    }

    private final static String DEFAULT_PRIMARY_DNS_SERVER = "9.9.9.9";
    private final static String DEFAULT_SECONDARY_DNS_SERVER = "9.9.9.9";

    public static List<String> getNetworkDnsServer(Context context) {
        List<String> dnsResolver = new ArrayList<>();
        try {
            dnsResolver = VpnUtils.getActiveNetworkDnsResolver(context);
        } catch (Exception e) {
            dnsResolver.add(DEFAULT_PRIMARY_DNS_SERVER);
            dnsResolver.add(DEFAULT_SECONDARY_DNS_SERVER);
        }
        return dnsResolver;
    }
    public static List<String> getActiveNetworkDnsResolver(Context context) throws Exception {
        Collection<InetAddress> dnsResolvers = getActiveNetworkDnsResolvers(context);

        if (!dnsResolvers.isEmpty()) {
            ArrayList<String> lista = new ArrayList<>();
            int max = 2;

            for (InetAddress resolver : dnsResolvers) {
                String dnsResolver = resolver.toString();

                // strip the leading slash e.g., "/192.168.1.1"
                if (dnsResolver.startsWith("/")) {
                    dnsResolver = dnsResolver.substring(1);
                }

                // remove ipv6 ips
                if (dnsResolver.contains(":")) {
                    continue;
                }

                lista.add(dnsResolver);

                max -= 1;
                if (max <= 0) break;
            }

            return lista;
        }
        else throw new Exception("no active network DNS resolver");
    }


    private static Collection<InetAddress> getActiveNetworkDnsResolvers(Context context)
            throws Exception {
        final String errorMessage = "getActiveNetworkDnsResolvers failed";
        ArrayList<InetAddress> dnsAddresses = new ArrayList<InetAddress>();
        try {
            // Hidden API
            // - only available in Android 4.0+
            // - no guarantee will be available beyond 4.2, or on all vendor devices
            ConnectivityManager connectivityManager =
                    (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Class<?> LinkPropertiesClass = Class.forName("android.net.LinkProperties");
            Method getActiveLinkPropertiesMethod = ConnectivityManager.class.getMethod("getActiveLinkProperties", new Class []{});
            Object linkProperties = getActiveLinkPropertiesMethod.invoke(connectivityManager);
            if (linkProperties != null) {
                // LinkProperties is public in API 21 (and the DNS function signature has changed)
                dnsAddresses.addAll(((LinkProperties) linkProperties).getDnsServers());
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalArgumentException |
                 IllegalAccessException | InvocationTargetException | NullPointerException e) {
            throw new Exception(errorMessage, e);
        }

        return dnsAddresses;
    }

    public static class PrivateAddress
    {
        public final String mIpAddress;
        public final String mSubnet;
        public final int mPrefixLength;
        public final String mRouter;

        public PrivateAddress(String ipAddress, String subnet, int prefixLength, String router)
        {
            mIpAddress = ipAddress;
            mSubnet = subnet;
            mPrefixLength = prefixLength;
            mRouter = router;
        }
    }
    public static PrivateAddress selectPrivateAddress() throws Exception
    {
        // Select one of 10.0.0.1, 172.16.0.1, or 192.168.0.1 depending on
        // which private address range isn't in use.
        Map<String, PrivateAddress> candidates = new HashMap<>();
        candidates.put("10", new PrivateAddress("10.0.0.1", "10.0.0.0", 8, "10.0.0.2"));
        candidates.put("172", new PrivateAddress("172.16.0.1", "172.16.0.0", 12, "172.16.0.2"));
        candidates.put("192", new PrivateAddress("192.168.0.1", "192.168.0.0", 16, "192.168.0.2"));
        candidates.put("169", new PrivateAddress("169.254.1.1", "169.254.1.0", 24, "169.254.1.2"));

        List<NetworkInterface> netInterfaces;
        try
        {
            netInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
        }
        catch (SocketException e)
        {
            e.printStackTrace();
            throw new Exception("selectPrivateAddress failed", e);
        }

        for (NetworkInterface netInterface : netInterfaces)
        {
            for (InetAddress inetAddress : Collections.list(netInterface.getInetAddresses()))
            {

                if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address)
                {
                    String ipAddress = inetAddress.getHostAddress();
                    assert ipAddress != null;
                    if (ipAddress.startsWith("10."))
                    {
                        candidates.remove("10");
                    }
                    else if (ipAddress.length() >= 6
                            && ipAddress.substring(0, 6).compareTo("172.16") >= 0
                            && ipAddress.substring(0, 6).compareTo("172.31") <= 0)
                    {
                        candidates.remove("172");
                    }
                    else if (ipAddress.startsWith("192.168"))
                    {
                        candidates.remove("192");
                    }
                }
            }
        }

        if (candidates.size() > 0)
        {
            return candidates.values().iterator().next();
        }

        throw new Exception("no private address available");
    }

    private static boolean isPortAvailable(int port)
    {

        try (Socket socket = new Socket()) {
            SocketAddress sockaddr = new InetSocketAddress("127.0.0.1", port);
            socket.connect(sockaddr, 1000);
            // The connect succeeded, so there is already something running on that port
            return false;
        } catch (SocketTimeoutException e) {
            // The socket is in use, but the server didn't respond quickly enough
            return false;
        } catch (IOException e) {
            // The connect failed, so the port is available
            return true;
        }
        /* should not be thrown */
    }

    public static int findAvailablePort(int start_port, int max_increment)
    {
        for(int port = start_port; port < (start_port + max_increment); port++)
        {
            if (isPortAvailable(port))
            {
                return port;
            }
        }

        return 0;
    }
}
