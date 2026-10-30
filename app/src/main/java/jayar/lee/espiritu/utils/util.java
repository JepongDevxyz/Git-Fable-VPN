package jayar.lee.espiritu.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import jayar.lee.espiritu.HarliesApplication;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.config.SettingsConstants;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class util implements SettingsConstants {
    private static SharedPreferences mPref;
    private static SharedPreferences.Editor mEditor;
    private static Context mContext;
    public static String str = new String(new byte[]{85,83,69,82,95,67,79,73,78,83,});
    public static String a = new String(new byte[]{48,49,50,51,52,53,54,55,56,57,72,65,82,76,73,69,});
    public util(Context c)
    {
        mContext = c;
        mPref = HarliesApplication.getPrivateSharedPreferences();
        mEditor = mPref.edit();
    }
    private static Context mContext(){
        if(mContext==null){
            return HarliesApplication.getApp();
        }
        return mContext;
    }

    public static String getAppVers(){
        try {
            PackageInfo pm = mContext().getPackageManager().getPackageInfo(mContext().getPackageName(), 0);
            return pm.versionName;
        } catch (PackageManager.NameNotFoundException e) {}
        return "1.0";
    }

    public static boolean isMyApp(){
        if(!mContext().getString(R.string.app_name).equals(FileUtils.showJson("5bDdU4K18rUUxIvv"))){
            return false;
        }else if(!mContext().getPackageName().equals(FileUtils.showJson("gkM1lRXXw725Yp/sfmfYnLb1ewUXFzds"))){
            return false;
        }
        return true;
    }

    public static String pw_repl(String user, String pw) {
        return pw;
    }

    public static boolean isSniffer(Context mContext) {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return (capabilities!= null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        }
        else {
            NetworkInfo info = cm.getNetworkInfo(ConnectivityManager.TYPE_VPN);
            return (info != null && info.isConnectedOrConnecting());
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return info != null && info.isAvailable() && info.isConnected();
    }

    public static String a() {
        String hex = "78702I74756I6I656R2I76706I2I666173742I616I642I7265637572652I7068";
        String digital = a;
        char[] hex2char = hex.toCharArray();
        byte[] bytes = new byte[hex.length() / 2];
        int temp;
        for (int i = 0; i < bytes.length; i++) {
            temp = digital.indexOf(hex2char[2 * i]) * 16;
            temp += digital.indexOf(hex2char[2 * i + 1]);
            bytes[i] = (byte) (temp & 0xff);
        }
        return new String(bytes);
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String sAddr = inetAddress.getHostAddress();

                        return sAddr;
                    }
                }
            }
        } catch (SocketException ex) {
            return "ERROR Obtaining IP";
        }
        return "No IP Available";
    }

 }
   
