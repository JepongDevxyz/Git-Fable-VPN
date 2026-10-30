package jayar.lee.espiritu.service;

import android.app.*;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.content.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.*;
import jayar.lee.espiritu.HarliesApplication;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.config.SettingsConstants;
import jayar.lee.espiritu.logger.ConnectionStatus;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.thread.BackServer;
import jayar.lee.espiritu.thread.ProxyThread;
import jayar.lee.espiritu.thread.UDPThread;
import jayar.lee.espiritu.utils.SSLUtil;
import jayar.lee.espiritu.utils.util;
import jayar.lee.espiritu.view.StatisticGraphData;
import javax.net.ssl.*;
import android.annotation.*;
import androidx.annotation.RequiresApi;

public class InjectorService extends Service implements SettingsConstants,hLogStatus.StateListener,hLogStatus.ByteCountListener,Handler.Callback
{

	public static final String START_SERVICE = "ceven.tunnel.vpn.ph:startTunnel";
	public static final String STOP_SERVICE = "ceven.tunnel.vpn.ph:stopTunnel";
	public static final String RECONNECT_SERVICE = "ceven.tunnel.vpn.ph:reconnecTunnel";
	private final int NOTIFICATION_ID = 123;
	private final int NOTIFICATION_PRIORITY = NotificationManager.IMPORTANCE_LOW;
	private PowerManager.WakeLock wakeLock;
	private NotificationManager nm;
	private Notification.Builder mNotifyBuilder = null;
	public static boolean isRunning = false;
	private SharedPreferences mPref;
	private ConfigUtil mConfig;
	private final static int RECONNECT_TRIES = 5;
	private Thread mInjectThread;
	private UDPThread mUDPThread;
	private boolean mStopping = false;
	private HttpsURLConnection huc;
	private BackServer mBackServerThread;
	public SSLSocket mSSLSocket;
	private ServerSocket ss;
	private Socket client;
	public Socket server;
	private int repeatCount = 0;
	private CountDownLatch mTunnelThreadStopSignal;
	private InjectorService.InjectorListener InjectorListener;
	private static boolean mDisplayBytecount = false;
	private Handler mHandler;
	public interface InjectorListener
	{
		void startOpenVPN();
	}
	public void setInjectorListener(InjectorListener InjectorListener)
	{
		this.InjectorListener = InjectorListener;
	}
	@Override
	public IBinder onBind(Intent p1)
	{
		return new MyBinder();
	}
	public class MyBinder extends Binder
	{
		public InjectorService getService()
		{
			return InjectorService.this;
		}
	}

	private void setWakelock(){
		try{
			this.wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(1, "SocksIP::Tag");
			this.wakeLock.acquire();
		}
		catch (Exception e){
			android.util.Log.d("WAKELOCK", e.getMessage());
		}
	}

	private void unsetWakelock(){
		if (this.wakeLock != null && this.wakeLock.isHeld()){
			android.util.Log.e("WAKELOCK", "is disabled");
			this.wakeLock.release();
		}
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder();
		StrictMode.setThreadPolicy(builder.permitAll().build());
		mPref = HarliesApplication.getPrivateSharedPreferences();
		mConfig = ConfigUtil.getInstance(InjectorService.this);
		mHandler = new Handler(this);
		connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if(intent!=null){
			String action = intent.getAction();
			switch (action) {
				case START_SERVICE:
					hLogStatus.addStateListener(InjectorService.this);
					hLogStatus.addByteCountListener(InjectorService.this);
					hLogStatus.updateStateString(hLogStatus.VPN_STARTING, getString(R.string.state_starting));
					mConfig = ConfigUtil.getInstance(InjectorService.this);
					start_notification();
					initializeMsg();
					isRunning = true;
					mStopping = false;
					setWakelock();
					if (mInjectThread != null){
						mInjectThread.interrupt();
					}
					mInjectThread = new Thread(starTunnel);
					mInjectThread.start();
					break;
				case STOP_SERVICE:
					onDestroy();
					break;
				case RECONNECT_SERVICE:
					vpn_handler(false);
					network_reconnect();
					break;
			}
		}
		return Service.START_NOT_STICKY;
	}

	public synchronized void startUDPTunnel() {
		mUDPThread = new UDPThread(this);
		mUDPThread.start();
	}

	public void reconnUDPTunnel() {
		hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, getString(R.string.state_reconnecting));
		addLogInfo("<strong>" + "UDP Client: Now Reconnecting" + "</strong>");
		new Thread(() -> startUDPTunnel()).start();
	}

	@Override
	public void onDestroy()
	{
		if(isRunning)addLogInfo("<b>" + getString(R.string.state_disconnected) + "</b>");
		isRunning = false;
		mStopping = true;
		mReconnecting = false;
		stopSocket();
		if (mTunnelThreadStopSignal != null)mTunnelThreadStopSignal.countDown();
		StatisticGraphData.getStatisticData().getDataTransferStats().stop();
		unsetWakelock();
		endTunnelService();
		hLogStatus.removeStateListener(this);
		hLogStatus.removeByteCountListener(this);
		super.onDestroy();
	}

	private boolean connectSocket() throws Exception{
		try {
			String readLine;
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
			StringBuffer stringBuffer = new StringBuffer();
			while (true) {
				readLine = bufferedReader.readLine();
				if (readLine != null && readLine.length() > 0) {
					stringBuffer.append(readLine);
					stringBuffer.append("\r\n");
				} else {
					break;
				}
				if (stringBuffer.toString().equals("")) {
					log("Get Request", "Get request data failed, empty requestline");
					return false;
				}

				int tunnel_type = mConfig.getPayload_type();

				if (tunnel_type==PAYLOAD_TYPE_HTTP_PROXY) {
					String c2 = c(stringBuffer.toString());
					if (c2 == null) {
						return false;
					}
					String proxy = mConfig.getSecureString(PROXY_IP_KEY);
					int proxyPort = Integer.parseInt(mConfig.getSecureString(PROXY_PORTA_KEY));
					server = new Socket();
					server.connect(new InetSocketAddress(proxy, proxyPort));
					if (!c2.equals("")) {
						a(c2, server);
					}
					return true;

				} else if (tunnel_type==PAYLOAD_TYPE_SSL) {
					String[] split = stringBuffer.toString().split("\r\n");
					String str = split[0].split(" ")[1];
					String host = str.split(":")[0];
					int port = Integer.parseInt(str.split(":")[1]);
					connectSocket(host, port);
					connectSSL();
					send200Status(client.getOutputStream());
					return true;

				} else if (tunnel_type==PAYLOAD_TYPE_SSL_PAYLOAD) {
					String c3 = c(stringBuffer.toString());
					if (c3 == null) {
						return false;
					}
					String[] split = stringBuffer.toString().split("\r\n");
					String str = split[0].split(" ")[1];
					String host = str.split(":")[0];
					int port = Integer.parseInt(str.split(":")[1]);
					connectSocket(host, port);
					connectSSL();
					if (!c3.equals("")) {
						a(c3, mSSLSocket);
					}
					return true;

				} else if (tunnel_type==PAYLOAD_TYPE_SSL_PROXY) {
					String c4 = c(stringBuffer.toString());
					if (c4 == null) {
						return false;
					}
					String proxy = mConfig.getSecureString(PROXY_IP_KEY);
					int proxyPort = Integer.parseInt(mConfig.getSecureString(PROXY_PORTA_KEY));
					connectSocket(proxy, proxyPort);
					connectSSL();
					if (!c4.equals("")) {
						a(c4, mSSLSocket);
					}
					return true;
				}
			}
		} catch (Exception e) {
			addLogInfo("<b>Invalid Configuration</b>");
			network_reconnect();
		}
		return false;
	}

	private void connectSocket(String host, int port) throws Exception {
		int mTunnelType = mConfig.getPayload_type();
		server = new Socket();
		if (mTunnelType==PAYLOAD_TYPE_SSL | mTunnelType == PAYLOAD_TYPE_SSL_PAYLOAD || mTunnelType == PAYLOAD_TYPE_SSL_PROXY) {
			server.bind(new InetSocketAddress(0));
		}
		server.connect(new InetSocketAddress(host, port));
		doVpnProtect(server);
	}

	private void connectSSL() throws Exception{
		addLogInfo("Setting up SNI...");
		SSLSocketFactory factory = new SSLUtil(this);
		String mSni = (mConfig.getSecureString(SNI_HOST_KEY).startsWith("http"))?mConfig.getSecureString(SNI_HOST_KEY):"https://"+mConfig.getSecureString(SNI_HOST_KEY);
		URL url = new URL(mSni);
		mSni = url.getHost();
		if (url.getPort() > 0) {
			mSni = mSni + ":" + url.getPort();
		}
		if (!url.getPath().equals("/")) {
			mSni = mSni + url.getPath();
		}
		huc = (HttpsURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, mBackServerThread.getLocalSocketAddr()));
		this.huc.setHostnameVerifier((str, sSLSession) -> true);
		huc.setSSLSocketFactory(factory);
		huc.connect();
	}

	private void a(String str, Socket socket) throws Exception{
		int i = 0;
		Random g;
		OutputStream outputStream = socket.getOutputStream();
		if (str.contains("[random]")) {
			g = new Random();
			String[] split = str.split(Pattern.quote("[random]"));
			str = split[g.nextInt(split.length)];
		}
		if (str.contains("[repeat]")) {
			String[] split = str.split(Pattern.quote("[repeat]"));
			str = split[repeatCount];
			repeatCount++;
			if (repeatCount > split.length - 1) {
				repeatCount= 0;
			}
		}
		log("Server Client: Waiting for OVPN Response");
		log("OVPN Client: Configured Successfully");
		if (str.contains("[split_delay]")) {
			String[] split = str.split(Pattern.quote("[split_delay]"));
			int length = split.length;
			while (i < length) {
				String str2 = split[i];
				if (a(str2, socket, outputStream)) {
					outputStream.write(str2.getBytes());
					outputStream.flush();
					Thread.sleep(1500);
				}
				i++;
			}
		} else if (str.contains("[split_instant]")) {
			String[] split = str.split(Pattern.quote("[split_instant]"));
			int length = split.length;
			while (i < length) {
				String str2 = split[i];
				if (a(str2, socket, outputStream)) {
					outputStream.write(str2.getBytes());
					outputStream.flush();
					Thread.sleep(0);
				}
				i++;
			}
		} else if (str.contains("[instant_split]")) {
			String[] split = str.split(Pattern.quote("[instant_split]"));
			int length = split.length;
			while (i < length) {
				String str2 = split[i];
				if (a(str2, socket, outputStream)) {
					outputStream.write(str2.getBytes());
					outputStream.flush();
					Thread.sleep(0);
				}
				i++;
			}
		} else  if (str.contains("[delay_split]")) {
			String[] split = str.split(Pattern.quote("[delay_split]"));
			int length = split.length;
			while (i < length) {
				String str2 = split[i];
				if (a(str2, socket, outputStream)) {
					outputStream.write(str2.getBytes());
					outputStream.flush();
					Thread.sleep(1500);
				}
				i++;
			}
		} else if (a(str, socket, outputStream)) {
			outputStream.write(str.getBytes());
			outputStream.flush();
		}
	}
	private boolean a(String str, Socket socket, OutputStream outputStream) throws Exception
	{
		if (!str.contains("[split]")) {
			return true;
		}
		for (String str2 : str.split(Pattern.quote("[split]"))) {
			outputStream.write(str2.getBytes());
			outputStream.flush();
		}
		return false;
	}
	private String c(String str) {
		String str2 = null;
		if (str != null) {
			try {
				if (!str.equals("")) {
					String mPayload = mConfig.getSecureString(CUSTOM_PAYLOAD_KEY);
					String charSequence = str.split("\r\n")[0];
					String[] split = charSequence.split(" ");
					String[] split2 = split[1].split(":");
					String host = split2[0];
					String port = split2[1];
					str2 = d(mPayload.replace("[real_raw]", str).replace("[raw]", charSequence).replace("[method]", split[0]).replace("[host_port]", split[1]).replace("[host]", host).replace("[port]", port).replace("[protocol]", split[2]).replace("[cr]", "\r").replace("[lf]", "\n").replace("[crlf]", "\r\n").replace("[lfcr]", "\n\r").replace("[7tunnel]", this.mConfig.getSecureString(SERVER_KEY)).replace("[7tunnel_host]", this.mConfig.getSecureString(SERVER_KEY)).replace("\\r", "\r").replace("\\n", "\n"));
					return str2;
				}
			} catch (Exception e) {
				log("Payload Error!");
			}
		}
		log("Payload Error", "Payload is null or empty");
		return str2;
	}
	private String d(String str) {
		if (str.contains("[cr*")) {
			str = a(str, "[cr*", "\r");
		}
		if (str.contains("[lf*")) {
			str = a(str, "[lf*", "\n");
		}
		if (str.contains("[crlf*")) {
			str = a(str, "[crlf*", "\r\n");
		}
		return str.contains("[lfcr*") ? a(str, "[lfcr*", "\n\r") : str;
	}
	private String a(String str, String str2, String str3) {
		while (str.contains(str2)) {
			Matcher matcher = Pattern.compile("\\[.*?\\*(.*?[0-9])\\]").matcher(str);
			if (matcher.find()) {
				int intValue = Integer.valueOf(matcher.group(1)).intValue();
				CharSequence charSequence = "";
				for (int i = 0; i < intValue; i++) {
					charSequence = charSequence + str3;
				}
				str = str.replace(str2 + String.valueOf(intValue) + "]", charSequence);
			}
		}
		return str;
	}
	private void send200Status(OutputStream output) throws Exception
	{
		output.write("HTTP/1.0 200 Connection Established\r\n\r\n".getBytes());
		output.flush();
	}

	private void doVpnProtect(Socket socket) {
		new OpenVPNService().protect(socket);
	}
	public void log(String tag, String msg){
		log(String.format("%s: %s", tag, msg));
	}
	public void log(String msg){
		addLogInfo(msg);
	}

	Runnable starTunnel = new Runnable() {
		@Override
		public void run()
		{
			int tries = 0;
			mTunnelThreadStopSignal = new CountDownLatch(1);
			while (!mStopping) {
				try {
					if (!util.isNetworkAvailable(InjectorService.this)) {
						hLogStatus.updateStateString(hLogStatus.VPN_NO_NETWORK, getString(R.string.state_nonetwork));
						addLogInfo(getString(R.string.state_nonetwork));
						vpn_handler(false);
						try {
							Thread.sleep(5000);
						} catch(InterruptedException e2) {
							onDestroy();
							break;
						}
					}
					else {
						if (tries > 0){
							hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, getString(R.string.state_reconnecting));
							addLogInfo("<b>" + getString(R.string.state_reconnecting) + "</b>");
							if(tries==100){
								addLogInfo("<b>Connection timeout</b>");
								onDestroy();
								break;
							}
						}
						try {
							Thread.sleep(500);
						} catch(InterruptedException e2) {
							onDestroy();
							break;
						}
						if(mConfig.getNetworkType().equals("UDP")){
							hLogStatus.updateStateString(hLogStatus.VPN_CONNECTING, getString(R.string.state_connecting));
							addLogInfo("<strong>" + "UDP Client: Building Configuration" + "</strong>");
							startUDPTunnel();
							break;
						}
						startSSHproxy();
						break;
					}
				} catch(Exception e) {
					closeService();
					try {
						Thread.sleep(500);
					} catch(InterruptedException e2) {
						onDestroy();
						break;
					}
				}
				tries++;
			}
			if (!mStopping) {
				try {
					mTunnelThreadStopSignal.await();
				} catch(InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}
	};

	@Override
	public boolean handleMessage(Message p1){
		switch (p1.what) {
			case 1:
				InjectorListener.startOpenVPN();
				break;
		}
		return true;
	}

	public void startSSHproxy()
	{
		new Thread(() -> {
			int tunnel_type = mConfig.getPayload_type();
			String TunnelType = mConfig.getNetworkType();
			try {
				ss = new ServerSocket(8989);
				if(tunnel_type==PAYLOAD_TYPE_SSL|tunnel_type==PAYLOAD_TYPE_SSL_PAYLOAD|tunnel_type==PAYLOAD_TYPE_SSL_PROXY){
					if (mBackServerThread != null) {
						try {
							mBackServerThread.Stop();
						} catch (Exception e) {}
					}
					mBackServerThread = new BackServer();
					mBackServerThread.start();
				}
				mHandler.sendEmptyMessage((TunnelType.equals("UDP"))?0:1);
				while (isRunning) {
					client = ss.accept();
					if (client != null  && !client.isClosed() && connectSocket()) {
						client.setKeepAlive(true);
						if (mSSLSocket != null && mSSLSocket.isConnected()) {
							mSSLSocket.setKeepAlive(true);
							server.setKeepAlive(true);
							doVpnProtect(mSSLSocket);
							ProxyThread.connect(client, mSSLSocket, "16384", "32768", false);
						}  else if (server != null && server.isConnected()) {
							server.setKeepAlive(true);
							doVpnProtect(server);
							ProxyThread.connect(client, server, "16384", "32768", false);
						}
					}
				}
			} catch (Exception e) {
				network_reconnect();
			}
		}).start();
	}

	private boolean mReconnecting = false;
	public void network_reconnect (){
		new Thread(() -> {
			if(isRunning){
				mReconnect();
			}
		}).start();
	}

	private void mReconnect() {
		if (mStopping | !isRunning | mReconnecting) {
			return;
		}
		if (util.isNetworkAvailable(InjectorService.this))
			hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, getString(R.string.state_reconnecting));

		mReconnecting = true;

		if(mConfig.getNetworkType().contains("UDP")){
			closeService();
			vpn_handler(false);
		}

		try {
			Thread.sleep(1000);
		} catch(InterruptedException e) {
			mReconnecting = false;
			return;
		}

		for (int i = 0; i < RECONNECT_TRIES; i++) {
			if (mStopping) {
				mReconnecting = false;
				return;
			}
			int sleepTime = 5;

			vpn_handler(false);

			if (!util.isNetworkAvailable(InjectorService.this)) {
				hLogStatus.updateStateString(hLogStatus.VPN_PAUSE, getString(R.string.state_pause));
				addLogInfo(getString(R.string.state_pause));
			}
			else {
				sleepTime = 3;
				hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, getString(R.string.state_reconnecting));
				try {
					if(!mConfig.getNetworkType().contains("UDP")){
						startService(new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_RECONNECT));
						mReconnecting = false;
						return;
					}
					if(mConfig.getNetworkType().equals("UDP")){
						reconnUDPTunnel();
						mReconnecting = false;
						return;
					}
					startSSHproxy();
					mReconnecting = false;
					return;
				} catch(Exception e) {
					addLogInfo("<b>" + getString(R.string.state_disconnected) + "</b>");
				}
			}

			try {
				Thread.sleep(sleepTime*1000);
				i--;
			} catch(InterruptedException e2){
				mReconnecting = false;
				return;
			}
		}

		mReconnecting = false;

		onDestroy();
	}

	private void stopSocket(){
		synchronized (this){
			repeatCount = 0;
			vpn_handler(false);
			mReconnecting = false;
			hLogStatus.updateStateString(hLogStatus.VPN_DISCONNECTED, getString(R.string.state_disconnected));
			new Thread(() -> {
				closeService();
				if (mInjectThread != null){
					mInjectThread.interrupt();
				}
			}).start();
		}
	}

	private void closeService(){
		try {
			if (ss != null) {
				ss.close();
				ss = null;
			}
		} catch (Exception e) {}
		try {
			if (client != null) {
				client.close();
			}
		} catch (Exception e) {}
		try {
			if (server != null) {
				server.close();
				server = null;
			}
		} catch (Exception e) {}
		try {
			if (mSSLSocket != null) {
				mSSLSocket.close();
				mSSLSocket = null;
			}
		} catch (Exception e) {}
		try {
			if (huc != null) {
				huc.disconnect();
			}
		} catch (Exception e) {}
		try {
			if (mBackServerThread != null) {
				mBackServerThread.Stop();
			}
		} catch (Exception e) {}
		if (mUDPThread != null && mUDPThread.isAlive()) {
			mUDPThread.interrupt();
			mUDPThread = null;
		}
	}

	public void vpn_handler(final boolean on){
		if(mConfig.getNetworkType().equals("UDP")){
			try{
				Intent intent = new Intent(InjectorService.this, OpenVPNService.class);
				if (on){
					startService(intent.setAction(OpenVPNService.START_SSH_SERVICE));
				}
				else
				{
					if (OpenVPNService.isSSHRunning)startService(intent.setAction(OpenVPNService.STOP_SSH_SERVICE));
				}
			}catch (Exception e){
				addLogInfo("<font color = #d50000>Something wen't wrong in vpn service.");
			}
		}
	}

	@Override
	@SuppressLint("StringFormatMatches")
	public void updateByteCount(long in, long out, long diffIn, long diffOut){
		if (mDisplayBytecount) {
			String netstat = String.format(getString(R.string.statusline_bytecount,
					OpenVPNService.render_bandwidth(in, false),
					OpenVPNService.render_bandwidth(diffIn, true),
					OpenVPNService.render_bandwidth(out, false),
					OpenVPNService.render_bandwidth(diffOut, true)));
			update_notification_event(netstat,ConnectionStatus.LEVEL_CONNECTED);
		}
	}

	@Override
	public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level, int progress) {
		String stateMsg = getString(hLogStatus.getLocalizedState(hLogStatus.getLastState()));
		mDisplayBytecount = level.equals(ConnectionStatus.LEVEL_CONNECTED);
		update_notification_event(stateMsg,level);
	}

	public static final String NOTIFICATION_CHANNEL_BG_ID = "openvpn_bg";
	@SuppressLint("NotificationPermission")
	private void start_notification() {
		if (mNotifyBuilder == null) {
			int type = mConfig.getNetworkType().equals("UDP")?0:1;
			this.mNotifyBuilder = new Notification.Builder(InjectorService.this);
			if (Build.VERSION.SDK_INT >= 26) {
				NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_BG_ID,getString(R.string.channel_name_background), NOTIFICATION_PRIORITY);
				notificationChannel.setDescription(getString(R.string.channel_description_background));
				nm.createNotificationChannel(notificationChannel);
				mNotifyBuilder.setChannelId(NOTIFICATION_CHANNEL_BG_ID);
			}
			this.mNotifyBuilder.setContentIntent(OpenVPNService.getPendingIntent(this)).
					setSmallIcon(R.drawable.ic_cloud_off).
					setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher)).
					setContentTitle(mConfig.getServerName()+" • "+mConfig.getPayloadName()).
					setContentText("Status: "+getString(R.string.state_connecting)).
					setOnlyAlertOnce(true).
					setOngoing(true).
					setWhen(new Date().getTime());
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				addVpnActionsToNotification(mNotifyBuilder,type);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
				jbNotificationExtras(NOTIFICATION_PRIORITY, mNotifyBuilder);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
				lpNotificationExtras(mNotifyBuilder, Notification.CATEGORY_SERVICE);
			nm.notify(NOTIFICATION_ID, mNotifyBuilder.getNotification());
			startForeground(NOTIFICATION_ID, mNotifyBuilder.getNotification());
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
	@SuppressLint("WrongConstant")
	private void addVpnActionsToNotification(Notification.Builder nbuilder,int t) {
		final int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 268435456;
		switch (t) {
			case 0:
				PendingIntent reconnectSSHService = PendingIntent.getService(InjectorService.this, 0, new Intent(InjectorService.this, InjectorService.class).setAction(RECONNECT_SERVICE),flags);
				PendingIntent disconnectSSHService = PendingIntent.getService(InjectorService.this, 0, new Intent(InjectorService.this, InjectorService.class).setAction(STOP_SERVICE),flags);
				nbuilder.addAction(R.drawable.ic_recon, "Reconnect", reconnectSSHService);
				nbuilder.addAction(R.drawable.ic_cloud_off, "Disconnect", disconnectSSHService);
				break;
			case 1:
				PendingIntent reconnectOvpnService = PendingIntent.getService(InjectorService.this, 0, new Intent(InjectorService.this, OpenVPNService.class).setAction(OpenVPNService.ACTION_RECONNECT),flags);
				PendingIntent disconnectOvpnService = PendingIntent.getService(InjectorService.this, 0, new Intent(InjectorService.this, OpenVPNService.class).setAction(OpenVPNService.ACTION_DISCONNECT),flags);
				nbuilder.addAction(R.drawable.ic_recon, "Reconnect", reconnectOvpnService);
				nbuilder.addAction(R.drawable.ic_cloud_off, "Disconnect", disconnectOvpnService);
				break;
		}
	}
	@SuppressLint("NotificationPermission")
	private void update_notification_event(String str,ConnectionStatus status){
		int icon = getIconByConnectionStatus(status);
		if (this.mNotifyBuilder != null) {
			if(str.contains(getString(R.string.state_connected))){
				this.mNotifyBuilder.setTicker(getString(R.string.state_connected));
			}
			this.mNotifyBuilder.setSmallIcon(icon);
			this.mNotifyBuilder.setContentTitle(mConfig.getServerName()+" • "+mConfig.getPayloadName());
			this.mNotifyBuilder.setContentText((mDisplayBytecount)?str:"Status: "+str);
			nm.notify(NOTIFICATION_ID, this.mNotifyBuilder.getNotification());
			startForeground(NOTIFICATION_ID, this.mNotifyBuilder.getNotification());
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void lpNotificationExtras(Notification.Builder nbuilder, String category) {
		nbuilder.setCategory(category);
		nbuilder.setLocalOnly(true);
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void jbNotificationExtras(int priority,Notification.Builder nbuilder) {
		try {
			if (priority != 0) {
				Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
				setpriority.invoke(nbuilder, priority);
				Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
				setUsesChronometer.invoke(nbuilder, true);
			}
		} catch (NoSuchMethodException | IllegalArgumentException |
				 InvocationTargetException | IllegalAccessException e) {
			addLogInfo(e.getMessage());
		}
	}

	public void endTunnelService(){
		new Thread(() -> {
			stopForeground(true);
			nm.cancel(NOTIFICATION_ID);
			stopSelf();
			hLogStatus.removeStateListener(InjectorService.this);
			hLogStatus.removeByteCountListener(InjectorService.this);
		}).start();
	}

	private int getIconByConnectionStatus(ConnectionStatus level) {
		switch (level) {
			case LEVEL_CONNECTED:
				return R.drawable.ic_cloud_on;
			case LEVEL_AUTH_FAILED:
			case LEVEL_NONETWORK:
			case LEVEL_NOTCONNECTED:
			case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
			case LEVEL_CONNECTING_SERVER_REPLIED:
			case UNKNOWN_LEVEL:
			default:
				return R.drawable.ic_cloud_off;
		}
	}

	private ConnectivityManager connMgr;
	private static String lastStateMsg;
	protected void networkStateChange(boolean showStatusRepetido) {
		String netstatestring;
		try {
			NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
			if (networkInfo == null) {
				netstatestring = "not connected";
			} else {
				String subtype = networkInfo.getSubtypeName();
				if (subtype == null)
					subtype = "";
				String extrainfo = networkInfo.getExtraInfo();
				if (extrainfo == null)
					extrainfo = "";
				netstatestring = String.format("%2$s %4$s to %1$s %3$s", networkInfo.getTypeName(),networkInfo.getDetailedState(), extrainfo, subtype);
			}
		} catch (Exception e) {
			netstatestring = e.getMessage();
		}
		if (showStatusRepetido || !netstatestring.equals(lastStateMsg))
			addLogInfo(netstatestring);
		lastStateMsg = netstatestring;
	}

	private String getIpPublic() {
		final android.net.NetworkInfo network = connMgr.getActiveNetworkInfo();
		if (network != null && network.isConnectedOrConnecting()) {
			return util.getLocalIpAddress();
		}
		else {
			return "Indisponivel";
		}
	}

	private void initializeMsg(){
		addLogInfo(String.format("<b> %s </b>", getString(R.string.app_name)));
		networkStateChange(true);
		addLogInfo(String.format("Your IP Address: %s", getIpPublic()));
	}

	private void addLogInfo(String _msg){
		String hst = mConfig.getSecureString(SERVER_KEY);
		String prx = mConfig.getSecureString(PROXY_IP_KEY);
		String msg = _msg.trim().replace(hst,"******").replace(prx,"******");
		hLogStatus.logInfo(msg.trim().replace("java.io.IOException:",""));
	}
}