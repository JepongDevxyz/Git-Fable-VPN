package jayar.lee.espiritu.core.vpnutils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.jepongdevxyz.fablevpn.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;


public class Pdnsd extends Thread {
    private final static String TAG = "PdnsdThread";
    private final static String PDNSD_SERVER = "server {\n label= \"%1$s\";\n ip = %2$s;\n port = %3$d;\n uptest = none;\n }\n";
    private final static String PDNSD_BIN = "libpdnsd";

    private OnPdnsdListener mListener;
    public interface OnPdnsdListener {
        public void onStart();
        public void onStop();
    }

    private Process mProcess;
    private File filePdnsd;

    private final Context mContext;
    private final String[] mDnsHosts;
    private final int mDnsPort;
    private final String mPdnsdHost;
    private final int mPdnsdPort;

    public Pdnsd(Context context, String[] dnsHosts, int dnsPort, String pdnsdHost, int pdnsdPort) {
        mContext = context;

        mDnsHosts = dnsHosts;
        mDnsPort = dnsPort;
        mPdnsdHost = pdnsdHost;
        mPdnsdPort = pdnsdPort;
    }

    @Override
    public void run() {

        if (mListener != null) {
            mListener.onStart();
        }

        try {

            //File filePdnsd = CustomNativeLoader.loadExecutableBinary(mContext, "libpdnsd.so");
            filePdnsd = CustomNativeLoader.loadNativeBinary(mContext, PDNSD_BIN, new File(mContext.getFilesDir(), PDNSD_BIN));

            if (filePdnsd == null) {
                throw new IOException("Bin Pdnsd not found");
            }

            File fileConf = makePdnsdConf(mContext.getFilesDir(), mDnsHosts, mDnsPort, mPdnsdHost, mPdnsdPort);

            String cmdString = filePdnsd.getCanonicalPath() + " -v9 -c " + fileConf.toString();

            mProcess = Runtime.getRuntime().exec(cmdString);

            StreamGobbler.OnLineListener onLineListener = new StreamGobbler.OnLineListener(){
                @Override
                public void onLine(String log){
                    android.util.Log.d("Pdnsd:",log);
                }
            };

            StreamGobbler stdoutGobbler = new StreamGobbler(mProcess.getInputStream(), onLineListener);
            StreamGobbler stderrGobbler = new StreamGobbler(mProcess.getErrorStream(), onLineListener);

            stdoutGobbler.start();
            stderrGobbler.start();

            mProcess.waitFor();

        } catch (IOException e) {
            android.util.Log.e("Pdnsd Error", e.getMessage());
        } catch(Exception e){
            addLog("Pdnsd Error: " + e);
        }

        mProcess = null;
        if (mListener != null) {
            mListener.onStop();
        }

    }

    @Override
    public synchronized void interrupt()
    {
        // TODO: Implement this method
        super.interrupt();

        if (mProcess != null)
            mProcess.destroy();

        try {
            if (filePdnsd != null)
                VpnUtils.killProcess(filePdnsd);
        } catch(Exception ignored) {}

        mProcess = null;
        filePdnsd = null;
    }

    @SuppressLint("DefaultLocale")
    private File makePdnsdConf(File fileDir, String[] dnsHosts, int dnsPort, String pdnsdHost, int pdnsdPort) throws FileNotFoundException, IOException {
        String content = readFromRaw(mContext, R.raw.pdnsd_local);

        // dns servers
        StringBuilder server_dns = new StringBuilder();
        for (int i = 0; i < dnsHosts.length; i++){
            String dnsHost = dnsHosts[i];
            server_dns.append(String.format(PDNSD_SERVER, "server" + Integer.toString(i+1), dnsHost, dnsPort));
            //server_dns.append(String.format(PDNSD_SERVER_TEST, "server" + Integer.toString(i+1), "127.0.0.1", 8865));
        }

        String conf = String.format(content, server_dns.toString(), fileDir.getCanonicalPath(), pdnsdHost, pdnsdPort);

        Log.d(TAG,"pdnsd conf:" + conf);

        File f = new File(fileDir,"pdnsd.conf");
        if (f.exists()) {
            f.delete();
        }
        saveTextFile(f, conf);

        File cache = new File(fileDir,"pdnsd.cache");
        if (!cache.exists()) {
            try {
                cache.createNewFile();
            } catch (Exception ignored) {}
        }

        return f;
    }

    public void setOnPdnsdListener(OnPdnsdListener listener){
        this.mListener = listener;
    }

    public static String readFromRaw(Context context, int resId) {
        InputStream in = context.getResources().openRawResource(resId);
        Scanner scanner = new Scanner(in,"UTF-8").useDelimiter("\\A");
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNext()) {
            sb.append(scanner.next());
        }
        scanner.close();
        return sb.toString();
    }

    public static void saveTextFile(File file, String contents)
    {
        try {

            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter writer = new FileWriter(file, false);
            writer.write(contents);

            writer.close();

        } catch (IOException e) {
            //	Log.d(TAG, "error writing file: " + path, e);
            e.printStackTrace();
        }
    }
    private void addLog(String log) {
        //LogFragment.addLog(log);
    }
}