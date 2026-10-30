package jayar.lee.espiritu.activities;

import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.io.InputStream;
import android.view.LayoutInflater;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.LineChart;
import com.jepongdevxyz.fablevpn.BuildConfig;
import jayar.lee.espiritu.HarliesApplication;
import jayar.lee.espiritu.adapter.NetworksSpinnerAdapter;
import jayar.lee.espiritu.adapter.ServerSpinnerAdapter;
import jayar.lee.espiritu.config.ConfigDataBase;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.config.SettingsConstants;
import jayar.lee.espiritu.core.ConfigParser;
import jayar.lee.espiritu.core.VpnProfile;
import jayar.lee.espiritu.logger.ConnectionStatus;
import jayar.lee.espiritu.logger.HarlieLogs;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.service.InjectorService;
import jayar.lee.espiritu.service.OpenVPNService;
import jayar.lee.espiritu.service.OpenVPNService.ConnectionStats;
import com.jepongdevxyz.fablevpn.R;
import net.openvpn.openvpn.PasswordUtil;
import net.openvpn.openvpn.PrefUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import jayar.lee.espiritu.thread.checkUpdate;
import jayar.lee.espiritu.utils.FileUtils;
import jayar.lee.espiritu.utils.util;
import jayar.lee.espiritu.view.CircleProgressBar;
import jayar.lee.espiritu.view.GraphHelper;
import jayar.lee.espiritu.view.StatisticGraphData;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.text.TextWatcher;

public class OpenVPNClient extends OpenVPNClientBase implements hLogStatus.StateListener, SettingsConstants,hLogStatus.ByteCountListener,OnClickListener{

    private FinishOnConnect delayed_finish_on_connect = FinishOnConnect.DISABLED;
    private Button btn_connector;
    private Spinner ServerSpin,NetworkSpin;
    private ConfigDataBase ServerData,NetworksData;
    private TextView duration_view,byteIn_view,byteOut_view,status_view,Config_vers,ac_xp;
    private GraphHelper mDataGraph;
    private LineChart mDataChart;
    private static final int START_BIND_CALLED = 1;
    private static final int REQUEST_IMPORT_FILE = 2;
    private util mUtils;
    private FileUtils mFileUtils;
    private Handler mHandler;
    private AlertDialog cBuiler;
    private CircleProgressBar circleProgressBar;
    private static boolean isConnected = false;
    private String TAG = "OpenVPNClient";
    private FinishOnConnect finish_on_connect = FinishOnConnect.DISABLED;
    private PrefUtil prefs;
    private EditText xUser, xPass;
    private PasswordUtil pwds;
    private Handler stats_timer_handler = new Handler();
    private Runnable stats_timer_task = new Runnable() {
        public void run() {
            if(hLogStatus.isTunnelActive()){
                duration_view.setText(_upDateBytes.isConnected() ? _upDateBytes.elapsedTimeToDisplay(_upDateBytes.getElapsedTime()) : "00h:00m:00s");
            }
            OpenVPNClient.this.show_stats();
            OpenVPNClient.this.schedule_stats();
        }
    };
    private void cancel_stats() {
        this.stats_timer_handler.removeCallbacks(this.stats_timer_task);
    }
    private void schedule_stats() {
        cancel_stats();
        this.stats_timer_handler.postDelayed(this.stats_timer_task, 1000);
    }
    public void show_stats() {
        if(isConnected){
            mDataGraph.start();
            if(!mConfig.getNetworkType().equals("UDP")){
                try{
                    ConnectionStats stats = get_connection_stats();
                    hLogStatus.updateByteCount(stats.bytes_in,stats.bytes_out);
                }catch (Exception e){}
            }
            else if(mConfig.getNetworkType().equals("UDP")){
                hLogStatus.updateByteCount(_upDateBytes.getBytesReceived(),_upDateBytes.getBytesSent());
            }
        }
        if(!isConnected)
            hLogStatus.updateByteCount(_upDateBytes.getBytesReceived(),_upDateBytes.getBytesSent());
    }

    private void doUpdateLayout(){
        boolean isRunning = hLogStatus.isTunnelActive();
        ServerSpin.setEnabled(!isRunning);
        NetworkSpin.setEnabled(!isRunning);
        mDataChart.setVisibility(isRunning?View.VISIBLE:View.GONE);
        xUser.setEnabled(!isRunning);
        xPass.setEnabled(!isRunning);
        findViewById(R.id.ac_ly).setVisibility(mPref.getBoolean("isAutoLogIn",false)?View.GONE:View.VISIBLE);
        if(isConnected==false){
            btn_connector.setBackgroundResource(R.drawable.connect_button);
            circleProgressBar.setColor(Color.RED);
            status_view.setTextColor(Color.RED);
        }
    }

    @Override
    public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level, int progress) {
        mHandler.post(() -> {
            isConnected = state.equals(resString(R.string.state_connected))?true:false;
            status_view.setText(state);
            if(hLogStatus.isTunnelActive()&&!isConnected){
                btn_connector.setBackgroundResource(R.drawable.connect_button);
                circleProgressBar.setColor(Color.RED);
                status_view.setTextColor(Color.RED);
            }
            circleProgressBar.setProgressWithAnimation(hLogStatus.isTunnelActive()?progress:0);
            doUpdateLayout();
        });
        switch (state) {
            case hLogStatus.VPN_CONNECTED:
                isConnected = true;
                btn_connector.setBackgroundResource(R.drawable.disconnect_button);
                circleProgressBar.setColor(Color.GREEN);
                status_view.setTextColor(Color.GREEN);
                
                showExpireDate();
                break;
            case hLogStatus.VPN_RECONNECTING:
                //if(mPref.getBoolean(IS_RANDOM_SERVER,false))reLoad_Configs();
                break;
        }
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        byteIn_view.setText(OpenVPNService.render_bandwidth(in,false));
        byteOut_view.setText(OpenVPNService.render_bandwidth(out,false));
    }

    

    private enum FinishOnConnect {
        DISABLED,
        ENABLED,
        ENABLED_ACROSS_ONSTART,
        PENDING
    }

    private void loadIds(){
        btn_connector = findViewById(R.id.btn_connect);
        circleProgressBar = findViewById(R.id.circle_progress);
        duration_view = findViewById(R.id.duration);
        byteIn_view = findViewById(R.id.bytes_in);
        byteOut_view = findViewById(R.id.bytes_out);
        status_view = findViewById(R.id.status);
        Config_vers = findViewById(R.id.config_version);
        ServerSpin = findViewById(R.id.profile);
        NetworkSpin = findViewById(R.id.networks);
        mDataChart = findViewById(R.id.chart);
        mDataGraph = GraphHelper.getHelper().with(this).color(getResources().getColor(R.color.colorPrimary)).chart(mDataChart);
        cBuiler = new AlertDialog.Builder(this).create();
        xUser = findViewById(R.id.x_username);
        xPass = findViewById(R.id.x_password);
        ac_xp = findViewById(R.id.ac_xp);
        ac_xp.setTextSize(TypedValue.COMPLEX_UNIT_DIP,9);
        xUser.setText(mPref.getString("_screenUsername_key", ""));
        xUser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String u = xUser.getText().toString().trim();
                mEditor.putString("_screenUsername_key",u).apply();
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        xPass.setText(mPref.getString("_screenPassword_key", ""));
        xPass.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String u = xPass.getText().toString().trim();
                mEditor.putString("_screenPassword_key",u).apply();
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home);
        mUtils = new util(this);
        mFileUtils = new FileUtils();
        mHandler = new Handler();
        this.prefs = new PrefUtil(PreferenceManager.getDefaultSharedPreferences(this));
        this.pwds = new PasswordUtil(PreferenceManager.getDefaultSharedPreferences(this));
        mPref = HarliesApplication.getPrivateSharedPreferences();
        mEditor = mPref.edit();
        ServerData = new ConfigDataBase(this,"ServerData");
        NetworksData = new ConfigDataBase(this,"NetworksData");
        init_default_preferences(this.prefs);
        LoadDefaultConfig();
        loadDefaultProfiles();
        loadIds();
        doBindService();
        if (adapter() != null) ServerSpin.setAdapter(adapter());
        if (adapterP() != null) NetworkSpin.setAdapter(adapterP());
        ServerSpin.setSelection(mPref.getInt(SERVER_POSITION,0));
        ServerSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mEditor.putInt(SERVER_POSITION,position).apply();
                reLoad_Configs();
                findViewById(R.id.ac_ly).setVisibility(mPref.getBoolean("isAutoLogIn",false)?View.GONE:View.VISIBLE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        NetworkSpin.setSelection(mPref.getInt(NETWORK_POSITION,0));
        NetworkSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mEditor.putInt(NETWORK_POSITION,position).apply();
                reLoad_Configs();
                findViewById(R.id.ac_ly).setVisibility(mPref.getBoolean("isAutoLogIn",false)?View.GONE:View.VISIBLE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        btn_connector.setOnClickListener(this);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run(){
                Log.i("hello", "world");
                runOnUiThread(new Runnable() {
                    public void run(){
                        try{
                            if(mUtils.isNetworkAvailable(OpenVPNClient.this)){
                                if (isCheckUpdateIsRunning==false){
                                    autoUpdate();
                                }
                                
                            }
                        }catch (Exception e){
                            hLogStatus.logDebug(e.getMessage());
                        }
                    }
                });
            }
        }, 0, 120, TimeUnit.SECONDS);
    }

    private boolean isCheckUpdateIsRunning = false;
    private void autoUpdate(){
        isCheckUpdateIsRunning = true;
        new checkUpdate(OpenVPNClient.this, BuildConfig.XPTUNNELCONFIGURL, new checkUpdate.Listener() {
            @Override
            public void onError(String config) {
                isCheckUpdateIsRunning = false;
                //Toast.makeText(OpenVPNClient.this, config, Toast.LENGTH_LONG).show();
            }
            @Override
            public void onCompleted(final String config)
            {
                isCheckUpdateIsRunning = false;
                String mData = FileUtils.showJson(config);
                try{
                    JSONObject obj = new JSONObject(mData);
                    if (obj.getDouble("Version") <= Double.valueOf(mPref.getString(CONFIG_VERSION,"0"))){
                        Config_vers.setText(obj.getString("Version"));
                        if (isShowExpireDateRunning==false)showExpireDate();
                    }else{
                        JSONArray jarr = new JSONArray();
                        if (obj.getJSONArray("HTTPNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                            jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                        }
                        if (obj.getJSONArray("SSLNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                            jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                        }
                        mEditor.putString(CONFIG_VERSION ,obj.getString("Version")).apply();
                        mEditor.putString("_ReleaseNotes", obj.getString("ReleaseNotes")).apply();
                        mEditor.putString("_contactSupport",obj.getString("contactSupport")).apply();
                        mEditor.putString(OpenVPN_CERT,obj.getString("Ovpn_Cert")).apply();
                        Config_vers.setText(obj.getString("Version"));
                        ServerData.updateData("1",obj.getJSONArray("Servers").toString());
                        NetworksData.updateData("1",""+jarr);
                        mEditor.putInt(SERVER_POSITION,0).apply();
                        mEditor.putInt(NETWORK_POSITION,0).apply();
                        if (adapter() != null) ServerSpin.setAdapter(adapter());
                        if (adapterP() != null) NetworkSpin.setAdapter(adapterP());
                        ServerSpin.setSelection(0);
                        NetworkSpin.setSelection(0);
                        reLoad_Configs();
                        //stopTunnelService();
                        doUpdateLayout();
                        showDialog("Release Note",obj.getString("ReleaseNotes"));
                        if (isShowExpireDateRunning==false)showExpireDate();
                    }
                }catch (Exception e){
                    isCheckUpdateIsRunning = false;
                    //Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }).start(false);
    }

    public void mUpdate(View v){
        isCheckUpdateIsRunning = true;
        new checkUpdate(OpenVPNClient.this, BuildConfig.XPTUNNELCONFIGURL, new checkUpdate.Listener() {
            @Override
            public void onError(String config) {
                isCheckUpdateIsRunning = false;
                Toast.makeText(OpenVPNClient.this, config, Toast.LENGTH_LONG).show();
            }
            @Override
            public void onCompleted(final String config)
            {
                isCheckUpdateIsRunning = false;
                String mData = FileUtils.showJson(config);
                try{
                    JSONObject obj = new JSONObject(mData);
                    if (obj.getDouble("Version") <= Double.valueOf(mPref.getString(CONFIG_VERSION,"0"))){
                        showDialog("Your config is up to date",obj.getString("ReleaseNotes"));
                    }else{
                        JSONArray jarr = new JSONArray();
                        if (obj.getJSONArray("HTTPNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                            jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                        }
                        if (obj.getJSONArray("SSLNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                            jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                        }
                        mEditor.putString(CONFIG_VERSION ,obj.getString("Version")).apply();
                        mEditor.putString("_ReleaseNotes", obj.getString("ReleaseNotes")).apply();
                        mEditor.putString("_contactSupport",obj.getString("contactSupport")).apply();
                        mEditor.putString(OpenVPN_CERT,obj.getString("Ovpn_Cert")).apply();
                        Config_vers.setText(obj.getString("Version"));
                        ServerData.updateData("1",obj.getJSONArray("Servers").toString());
                        NetworksData.updateData("1",""+jarr);
                        mEditor.putInt(SERVER_POSITION,0).apply();
                        mEditor.putInt(NETWORK_POSITION,0).apply();
                        if (adapter() != null) ServerSpin.setAdapter(adapter());
                        if (adapterP() != null) NetworkSpin.setAdapter(adapterP());
                        ServerSpin.setSelection(0);
                        NetworkSpin.setSelection(0);
                        reLoad_Configs();
                        //stopTunnelService();
                        showDialog("Release Note",obj.getString("ReleaseNotes"));
                        doUpdateLayout();
                    }
                }catch (Exception e){
                    isCheckUpdateIsRunning = false;
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }).start(true);
    }

    private void showExpireDate(){
        if(!mUtils.isNetworkAvailable(OpenVPNClient.this)){
            return;
        }
        try{
            isShowExpireDateRunning = true;
            String full_api = ""+"";
            String user = mConfig.getSecureString(USERNAME_KEY);
            String pass = mConfig.getSecureString(PASSWORD_KEY);
            if (user.isEmpty() || pass.isEmpty()) {
                return;
            }
            ac_xp.setText("Checking account...");
            ac_xp.setTextColor(Color.WHITE);
            String model = Build.MODEL;
            String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            StringRequest req = new StringRequest(String.format(full_api, user, pass, id, model), response -> {
                try {
                    isShowExpireDateRunning = false;
                    JSONObject js = new JSONObject(response);
                    if (!js.getString("status").equals("success")) {
                        stopTunnelService();
                        mEditor.putString("_AccountXp",resString(R.string.state_auth_failed)).apply();
                        ac_xp.setText(resString(R.string.state_auth_failed));
                        ac_xp.setTextColor(Color.RED);
                        return;
                    }
                    JSONObject mData = js.getJSONObject("data");
                    if (mData.getString("device_match").equals("false")) {
                        stopTunnelService();
                        mEditor.putString("_AccountXp","This account using other device!").apply();
                        ac_xp.setText("This account using other device!");
                        ac_xp.setTextColor(Color.RED);
                        return;
                    }
                    mEditor.putString("_AccountXp",getDaysLeft(mData.getString("expiry"))).apply();
                    ac_xp.setText(getDaysLeft(mData.getString("expiry")));
                    ac_xp.setTextColor(Color.GREEN);
                } catch (Exception e) {
                    isShowExpireDateRunning = false;
                    mEditor.putString("_AccountXp","Expiry date failed to load!").apply();
                    ac_xp.setText("Expiry date failed to load!");
                }
            }, error -> {
                mEditor.putString("_AccountXp","Expiry date failed to load!").apply();
                ac_xp.setText("Expiry date failed to load!");
                ac_xp.setTextColor(Color.RED);
            });
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            requestQueue.add(req);
        }catch (Exception e){
            mEditor.putString("_AccountXp","Expiry date failed to load!").apply();
            ac_xp.setText("Expiry date failed to load!");
            ac_xp.setTextColor(Color.RED);
        }
    }
    private static boolean isShowExpireDateRunning = false;
    private String getDaysLeft(String thatDate) {
        if (thatDate.contains(" "))thatDate = thatDate.split(" ")[0];
        String[] split = thatDate.split("-");
        Calendar instance = Calendar.getInstance();
        instance.set(Integer.valueOf(split[0]).intValue(), Integer.valueOf(split[1]).intValue() - 1, Integer.valueOf(split[2]).intValue());
        return String.format("Account valid for %s Days", new Object[]{(instance.getTimeInMillis() - Calendar.getInstance().getTimeInMillis()) / ((long) 86400000)});
    }

    public void mImport(View v){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_FILE);
    }
    
    public void mIphunt(View v){
        View inflate = LayoutInflater.from(this).inflate(R.layout.notif2, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
		alertDialogBuilder.setView(inflate); 
		TextView title = inflate.findViewById(R.id.notiftext1);
		final TextView ms = inflate.findViewById(R.id.confimsg);
		final TextView ok = inflate.findViewById(R.id.appButton1);
		TextView cancel = inflate.findViewById(R.id.appButton2);
		title.setText("GTM IP Hunter"); 
		ms.setText("To connect to GTM No Load No Blocking, Make sure that you are now in the Magic IP. Click the button to check your IP!");
		ok.setText("Check Now");
		cancel.setText("Close");
		final AlertDialog alert = alertDialogBuilder.create(); 
		alert.setCanceledOnTouchOutside(false);
		alert.getWindow().setGravity(Gravity.CENTER); 
		alert.show();
		ok.setOnClickListener(new View.OnClickListener() { 
				@Override
				public void onClick(View p1){
					ms.setText("Please wait while we are checking your IP...");
					ok.setEnabled(false);
					ok.setText("Checking...");
					new Handler().postDelayed(new Runnable(){

							@Override
							public void run() {
								try {
									int l = 0;
									URL whatismyip = new URL("http://noloadbalance.globe.com.ph");
									String magic = "✅ Congrats!! You are now connected to MAGIC IP.";
									String fail = "🚫 Disconnected. Please Airplane Mode On/Off and Try Again.";
									try{		
										Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("104.16.213.74", 80));
										HttpURLConnection connection = (HttpURLConnection) whatismyip.openConnection(proxy);
										connection.setRequestMethod("GET");
										connection.connect();
										connection.getContentLength();
										connection.setConnectTimeout(3000);
										InputStream in = connection.getInputStream();
										byte[] buffer = new byte[4096];
										int countBytesRead;
										while((countBytesRead = in.read(buffer)) != -1) {
											l += countBytesRead;
										}
										in.markSupported();
										if (l == 333){
											ms.setText(magic);
											ok.setText("Check Again");
											ok.setEnabled(true);
											return;
										}
										if (connection.getResponseCode() == 200){
											ms.setText(magic);
											ok.setText("Check Again");
											ok.setEnabled(true);
											return;
										}
										in.close();
										ms.setText(fail);;
										ok.setText("Check Again");
										ok.setEnabled(true);
									} catch (IOException e) {
										ok.setText("Check Again");
										ok.setEnabled(true);
										ms.setText(fail);
									}

								}catch (MalformedURLException e) {}}
						}, 1000);

				}

			});

		cancel.setOnClickListener(new View.OnClickListener(){

				@Override
				public void onClick(View p1) {

					alert.dismiss();
				}
			});
		alert.show();
     

    }
    
    public void mLogs(View v){
        startActivityForResult(new Intent(OpenVPNClient.this, HarlieLogs.class), 0);
    }

    public void mTelegram(View v){
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/jeeeeeeepong?mibextid=kFxxJD"));
            startActivity(Intent.createChooser(intent, "launch Facebook"));
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Please Download the Facebook App", Toast.LENGTH_LONG).show();
        }
    }


    private void showDialog(String t,String m){
        if (cBuiler.isShowing())cBuiler.dismiss();
        cBuiler = new AlertDialog.Builder(this).create();
        cBuiler.setTitle(t);
        cBuiler.setMessage(m);
        cBuiler.setIcon(R.drawable.ic_launcher);
        cBuiler.setButton(DialogInterface.BUTTON_POSITIVE, "Close", (dialog, which) -> dialog.dismiss());
        cBuiler.show();
    }

    public boolean LoadDefaultConfig(){
        boolean showFirstTime = mPref.getBoolean("connect_first_time", true);
        if (new Boolean(showFirstTime).booleanValue())
        {
            String data = mFileUtils.showJson(BuildConfig.XPTUNNELCONFIG);
            try {
                JSONObject obj = new JSONObject(data);
                JSONArray jarr = new JSONArray();
                for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                    jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                }
                for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                    jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                }
                mEditor.putString(CONFIG_VERSION ,obj.getString("Version")).apply();
                mEditor.putString("_ReleaseNotes", obj.getString("ReleaseNotes")).apply();
                mEditor.putString("_contactSupport",obj.getString("contactSupport")).apply();
                mEditor.putString(OpenVPN_CERT,obj.getString("Ovpn_Cert")).apply();
                ServerData.insertData(obj.getJSONArray("Servers").toString());
                NetworksData.insertData(""+jarr);
                reLoad_Configs();
                mEditor.putBoolean("connect_first_time",false).apply();
                return true;
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        return false;
    }

    private void loadDefaultProfiles() {
        boolean isFinished = false;
        try{
            String server_name = "HarliesDevX";
            String encoded_name = String.format("%s.ovpn", URLEncoder.encode(server_name, "UTF-8"));
            String config = FileUtils.readFromRaw(this,R.raw.ovpn_cert);
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new StringReader(config));
            VpnProfile vp = cp.convertProfile();// 解析.ovpn
            vp.mName = server_name;
            if (vp.checkProfile(this) != R.string.no_error_found)
            {
                throw new RemoteException(getString(vp.checkProfile(this)));
            }
            vp.mProfileCreator = getPackageName();
            vp.mUsername = "HarliesDevX";
            vp.mPassword = "HarliesDevX";
            vp.mUseCustomConfig = true;
            vp.mCustomConfigOptions = new StringBuffer().append(vp.mCustomConfigOptions).append("http-proxy 127.0.0.1 8989").toString();
            String _config = vp.getConfigFile(this, true);
            File dir = new File(getFilesDir(), encoded_name);
            OutputStream out = new FileOutputStream(dir);
            out.write(_config.getBytes());
            out.flush();
            out.close();
            isFinished = true;
        }catch(Exception e){
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (isFinished) {
            startService(new Intent(this, OpenVPNService.class).setAction(OpenVPNService.ACTION_ADD_PROFILE));
        }
    }

    private ServerSpinnerAdapter adapter() {
        ArrayList<JSONObject> s=new ArrayList();
        ServerSpinnerAdapter ad=new ServerSpinnerAdapter(this, s);
        ad.setPath("servers");
        try {
            JSONArray jarr = new JSONArray(ServerData.getData());
            for (int i=0;i < jarr.length();i++) {
                s.add(jarr.getJSONObject(i));
            }
            return ad;
        } catch (Exception e) {
            return null;
        }
    }

    private NetworksSpinnerAdapter adapterP() {
        ArrayList<JSONObject> p=new ArrayList<JSONObject>();
        NetworksSpinnerAdapter ad=new NetworksSpinnerAdapter(this, p);
        ad.setPath("Networks");
        try {
            JSONArray jarr = new JSONArray(NetworksData.getData());
            for (int i=0;i < jarr.length();i++) {
                p.add(jarr.getJSONObject(i));
            }
            return ad;
        } catch (Exception e) {
            return null;
        }
    }

    protected JSONArray ServerArray(){
        try {
            JSONArray jarr = new JSONArray(ServerData.getData());
            //jarr = JsonUtils.sort(jarr, JsonUtils.getComparator(this,"Name",1));
            return jarr;
        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return null;
    }
    protected JSONArray NetworkArray(){
        try {
            JSONArray jarr = new JSONArray(NetworksData.getData());
            //jarr = JsonUtils.sort(jarr, JsonUtils.getComparator(this,"Flag",1));
            return jarr;
        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return null;
    }

    private String getNetworkType(JSONObject js) throws JSONException {
        if (js.has("proto_spin")){
            if (js.getInt("proto_spin")==0){
                return "HTTP";
            }else if (js.getInt("proto_spin")==1){
                return "UDP";
            }
        }else if (js.has("sslmethod")){
            if (js.getInt("sslmethod")==0){
                return "SSL";
            }else if (js.getInt("sslmethod")==1){
                return "SSL+PAYLOAD";
            }else if (js.getInt("sslmethod")==2){
                return "SSL+PAYLOAD+WS";
            }
        }
        return "";
    }

    private String getServerType(JSONObject sjs, JSONObject pjs) throws JSONException {
        if (pjs.getString("server_type").equals("cf")){
            return sjs.getString("ServerIP");
        }else if (pjs.getString("server_type").equals("ws")){
            return sjs.getString("ServerCloudFront");
        }else if (pjs.getString("server_type").equals("http")){
            return sjs.getString("ServerHTTP");
        }
        return sjs.getString("ServerHTTP");
    }

    private boolean reLoad_Configs(){
        try {
            ConfigUtil config = ConfigUtil.getInstance(this);
            JSONArray jarr1 = ServerArray();
            JSONArray jarr2 = NetworkArray();
            if(jarr1.length()==0||jarr2.length()==0){
                return false;
            }
            JSONObject s_js = jarr1.getJSONObject(mPref.getInt(SERVER_POSITION,0));
            JSONObject p_js = jarr2.getJSONObject(mPref.getInt(NETWORK_POSITION,0));
            String p_type = getNetworkType(p_js);
            String mHost = getServerType(s_js,p_js);
            String SquidProxy = p_js.getBoolean("UseDefProxy")?mHost:p_js.getString("SquidProxy");
            config.setServerName(s_js.getString("Name"));
            config.setPayloadName(p_js.getString("Name"));
            config.setOvpnCert(mPref.getString(OpenVPN_CERT,""));
            config.setProxyHost(mFileUtils._e2("127.0.0.1"));
            config.setProxyPort("8989");
            config.setNetworkType(p_type);
            config.setIsQueryMode(false);
            if(p_type.equals("UDP")){
                config.setPaylod_type(PAYLOAD_TYPE_UDP);
                config.setServerHost(mHost);
                config.setUDPConfig(p_js.getString("NetworkPayload"));
                mEditor.putBoolean("isAutoLogIn", s_js.getBoolean("AutoLogIn")).apply();
                if (s_js.getBoolean("AutoLogIn")) {
                    config.setUser(s_js.getString("Username"));
                    config.setUserPass(s_js.getString("Password"));
                } else {
                    config.setUser(mFileUtils._e2(mPref.getString("_screenUsername_key", "")));
                    config.setUserPass(mFileUtils._e2(mPref.getString("_screenPassword_key", "")));
                }
                findViewById(R.id.ac_ly).setVisibility(s_js.getBoolean("AutoLogIn")?View.GONE:View.VISIBLE);
                return true;
            }
            else if(p_type.equals("HTTP")){
                String front_query = p_js.getString("NetworkFrontQuery");
                String back_query = p_js.getString("NetworkBackQuery");
                config.setPaylod_type(PAYLOAD_TYPE_HTTP_PROXY);
                config.setPayload(p_js.getString("NetworkPayload"));
                config.setServerHost(mHost);
                config.setServerPort(s_js.getString("TcpPort"));
                config.setProxyHost(SquidProxy);
                config.setProxyPort(p_js.getString("SquidPort"));
                if (front_query.isEmpty() && back_query.isEmpty()) {
                    config.setIsQueryMode(false);
                } else if(!front_query.isEmpty()) {
                    config.setIsQueryMode(true);
                    config.setFrontQuery(front_query);
                    config.setBackQuery("");
                } else if (!back_query.isEmpty()) {
                    config.setIsQueryMode(true);
                    config.setBackQuery(back_query);
                    config.setFrontQuery("");
                }
            }
            else if(p_type.equals("SSL")){
                String sslPort = p_js.getString("SSLPort").isEmpty()? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                config.setPaylod_type(PAYLOAD_TYPE_SSL);
                config.setSni(p_js.getString("SSLSNI"));
                config.setServerHost(mHost);
                config.setServerPort(sslPort);
            }
            else if(p_type.equals("SSL+PAYLOAD")){
                String sslPort = p_js.getString("SSLPort").isEmpty()? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                config.setPaylod_type(PAYLOAD_TYPE_SSL_PAYLOAD);
                config.setSni(p_js.getString("SSLSNI"));
                config.setPayload(p_js.getString("SSLPayload"));
                config.setServerHost(mHost);
                config.setServerPort(sslPort);
            }
            else if(p_type.equals("SSL+PAYLOAD+WS")){
                String sslPort = p_js.getString("SSLPort").isEmpty()? s_js.getString("SSLPort") : p_js.getString("SSLPort");
                config.setPaylod_type(PAYLOAD_TYPE_SSL_PROXY);
                config.setSni(p_js.getString("SSLSNI"));
                config.setPayload(p_js.getString("SSLPayload"));
                config.setServerHost(mHost);
                config.setServerPort(sslPort);
                config.setProxyHost(SquidProxy);
                config.setProxyPort(p_js.getString("SquidPort"));
            }
            if(s_js.getBoolean("AutoLogIn")){
                config.setUser(s_js.getString("Username"));
                config.setUserPass(s_js.getString("Password"));
            }else{
                config.setUser(mFileUtils._e2(mPref.getString("_screenUsername_key","")));
                config.setUserPass(mFileUtils._e2(mPref.getString("_screenPassword_key","")));
            }
            findViewById(R.id.ac_ly).setVisibility(s_js.getBoolean("AutoLogIn")?View.GONE:View.VISIBLE);
            mEditor.putBoolean("isAutoLogIn",s_js.getBoolean("AutoLogIn")).apply();
            return true;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private boolean checkConfiguration(){
        if(!reLoad_Configs()){
            Toast.makeText(getApplicationContext(), "error: Config load error!", Toast.LENGTH_LONG).show();
            return false;
        }
        else if (!mUtils.isNetworkAvailable(this)) {
            Toast.makeText(getApplicationContext(), "Please connect to the internet", Toast.LENGTH_LONG).show();
            return false;
        }
        else if (mUtils.isSniffer(this)) {
            addlogInfo("<b>Another running VPN application has been detected, stop it before</b>");
            Toast.makeText(getApplicationContext(), "Another running VPN application has been detected, stop it before", Toast.LENGTH_LONG).show();
            circleProgressBar.setProgressWithAnimation(0);
            submitDisconnectIntent(mConfig.getNetworkType().equals("UDP")?0:1);
            return false;
        }
        else if (mPref.getBoolean("isAutoLogIn", false)==true) {
            if (mConfig.getSecureString(USERNAME_KEY).isEmpty() || mConfig.getSecureString(PASSWORD_KEY).isEmpty()) {
                Toast.makeText(getApplicationContext(), "Server Account is empty!", Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
        else if (mConfig.getSecureString(USERNAME_KEY).isEmpty() || mConfig.getSecureString(PASSWORD_KEY).isEmpty()) {
            Toast.makeText(getApplicationContext(), "Server Account is empty!", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void startOrStopTunnel() {
        if (hLogStatus.isTunnelActive()){
            stopTunnelService();
            cancel_stats();
        }
        else{
            mEditor.putInt("loadOnce",0).apply();
            if(checkConfiguration()){
                start_connect();
            }
        }
    }

    public void stopTunnelService(){
        if (hLogStatus.isTunnelActive()){
            circleProgressBar.setProgressWithAnimation(0);
            circleProgressBar.setColor(Color.RED);
            btn_connector.setBackgroundResource(R.drawable.connect_button);
            status_view.setTextColor(Color.RED);
            submitDisconnectIntent(mConfig.getNetworkType().equals("UDP")?0:1);
        }
    }

    private void stop_service() {
        hLogStatus.removeStateListener(this);
        hLogStatus.removeByteCountListener(this);
    }

    private void stop() {
        stop_service();
        doUnbindService();
    }

    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hLogStatus.addStateListener(this);
        hLogStatus.addByteCountListener(this);
        if(hLogStatus.isTunnelActive())schedule_stats();
        Config_vers.setText(mPref.getString(CONFIG_VERSION,"1.1"));
        ac_xp.setText(mPref.getString("_AccountXp","Account valid for 0 Days"));
        doUpdateLayout();
        autoUpdate();
        findViewById(R.id.ac_ly).setVisibility(mPref.getBoolean("isAutoLogIn",false)?View.GONE:View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel_stats();
        stop();
    }

    public void onClick(View v) {
        this.finish_on_connect = FinishOnConnect.DISABLED;
        int viewid = v.getId();
        if (viewid == R.id.btn_connect) {
            startOrStopTunnel();
        }
    }

    @Override
    public void startOpenVPN()
    {
        resolve_epki_alias_then_connect();
        super.startOpenVPN();
    }

    private void start_connect() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            try {
                startActivityForResult(intent, START_BIND_CALLED);
                return;
            } catch (ActivityNotFoundException e) {
                // showSnack(getResources().getDrawable(R.drawable.ic_error),"CLI: requesting VPN actor rights failed", e.getMessage());
                return;
            }
        }
        schedule_stats();
        StatisticGraphData.getStatisticData().getDataTransferStats().startConnected();
        startTunnelService();
    }

    private void startTunnelService() {
        Intent mIntent = new Intent(OpenVPNClient.this, InjectorService.class).setAction(InjectorService.START_SERVICE);
        if (mIntent != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(mIntent);
            else
                startService(mIntent);
        }
    }

    private void resolve_epki_alias_then_connect() {
        resolveExternalPkiAlias(alias -> OpenVPNClient.this.do_connect(alias));
    }

    private void do_connect(String epki_alias) {
        prefs.set_string("n_username",mConfig.getSecureString(USERNAME_KEY));
        String app_name = "net.openvpn.connect.android";
        String proxy_name = null;
        String server = null;
        String pk_password = null;
        String response = null;
        boolean is_auth_pwd_save = false;
        String profile_name = "HarliesDevX";
        String vpn_proto = prefs.get_string("vpn_proto");
        String conn_timeout = "60";
        String compression_mode = prefs.get_string("compression_mode");
        submitConnectIntent(profile_name, server, vpn_proto, conn_timeout, mConfig.getSecureString(USERNAME_KEY), mConfig.getSecureString(PASSWORD_KEY), is_auth_pwd_save, pk_password, response, epki_alias, compression_mode, proxy_name, null, null, true, get_gui_version(app_name));
    }

    protected void onActivityResult(int request, int result, Intent data) {
        switch (request) {
            case START_BIND_CALLED:
                if (result == RESULT_OK) {
                    start_connect();
                    return;
                }
                return;
            case REQUEST_IMPORT_FILE:
                if (result == RESULT_OK) {
                    Uri uri = data.getData();
                    String mData = FileUtils.showJson(mFileUtils.readTextFile(OpenVPNClient.this,uri));
                    try{
                        JSONObject obj = new JSONObject(mData);
                        if (obj.getDouble("Version") <= Double.valueOf(mPref.getString(CONFIG_VERSION,"0"))){
                            Toast.makeText(getApplicationContext(), "Your config is up to date", Toast.LENGTH_LONG).show();
                        }else{
                            JSONArray jarr = new JSONArray();
                            for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                                jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                            }
                            for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                                jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                            }
                            mEditor.putString(CONFIG_VERSION ,obj.getString("Version")).apply();
                            mEditor.putString("_ReleaseNotes", obj.getString("ReleaseNotes")).apply();
                            mEditor.putString("_contactSupport",obj.getString("contactSupport")).apply();
                            mEditor.putString(OpenVPN_CERT,obj.getString("Ovpn_Cert")).apply();
                            ServerData.updateData("1",obj.getJSONArray("Servers").toString());
                            NetworksData.updateData("1",""+jarr);
                            ServerSpin.setSelection(0);
                            NetworkSpin.setSelection(0);
                            mEditor.putInt(SERVER_POSITION,0).apply();
                            mEditor.putInt(NETWORK_POSITION,0).apply();
                            reLoad_Configs();
                            if (adapter() != null) ServerSpin.setAdapter(adapter());
                            if (adapterP() != null) NetworkSpin.setAdapter(adapterP());
                            stopTunnelService();
                            Config_vers.setText(mPref.getString(CONFIG_VERSION,"1.1"));
                        }
                    }catch (Exception e){
                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    stopTunnelService();
                    return;
                }
                recreate();
                return;
            default:
                super.onActivityResult(request, result, data);
                return;
        }
    }

}