package jayar.lee.espiritu.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import jayar.lee.espiritu.HarliesApplication;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.core.ConfigParser;
import jayar.lee.espiritu.core.VpnProfile;
import jayar.lee.espiritu.utils.FileUtils;
import jayar.lee.espiritu.utils.SecurePreferences;

public class ConfigUtil implements SettingsConstants
{
    private SecurePreferences mPrefsPrivate;
    private SharedPreferences.Editor secureEditor;
    private static ConfigUtil instance;
    private static SharedPreferences prefs;
    private static SharedPreferences.Editor editor;
    private static Context mContext;
    private static FileUtils mFileUtils;

    public String excludeIps(String str) {
        try {
            InetAddress addr = InetAddress.getByName(str);
            return addr.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
    public ConfigUtil(Context context)
    {
        mContext = context;
        mPrefsPrivate =  HarliesApplication.getSecurePreferences();
        secureEditor = mPrefsPrivate.edit();
        prefs = HarliesApplication.getPrivateSharedPreferences();
        editor = prefs.edit();
        mFileUtils = new FileUtils();
    }

    public static String _a(){
        SharedPreferences pref = HarliesApplication.getPrivateSharedPreferences();
        return pref.getString(HarliesApplication.PREFS_GERAL,"");
    }
    public String getSecureString(String key){
        if(key.equals(DIRECT_UDP_CONFIG_KEY)){
            return mFileUtils._d2(mPrefsPrivate.getString(DIRECT_UDP_CONFIG_KEY, ""));
        }
        if(key.equals(SERVER_KEY)){
            return mFileUtils._d2(mPrefsPrivate.getString(SERVER_KEY, ""));
        }
        if(key.equals(USERNAME_KEY)){
            return mFileUtils._d2(mPrefsPrivate.getString(USERNAME_KEY, ""));
        }
        if(key.equals(PASSWORD_KEY)){
            return mFileUtils._d2(mPrefsPrivate.getString(PASSWORD_KEY, ""));
        }
        if(key.equals(CUSTOM_PAYLOAD_KEY)){
            return mFileUtils._d2(mPrefsPrivate.getString(CUSTOM_PAYLOAD_KEY, ""));
        }
        if(key.equals(SNI_HOST_KEY)){
            return mFileUtils._d2(mPrefsPrivate.getString(SNI_HOST_KEY, ""));
        }
        if(key.equals(PROXY_IP_KEY)){
            return mFileUtils._d2(mPrefsPrivate.getString(PROXY_IP_KEY, ""));
        }
        return mPrefsPrivate.getString(key, "");
    }

    public static ConfigUtil getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigUtil(context);
        }
        mContext = context;
        return instance;
    }

    private String getRemote(){
        if (isQueryMode()){
            if (mFileUtils._d2(mPrefsPrivate.getString(FrontQuery, "")).isEmpty()) {
                return "\nremote "+mFileUtils._d2(mPrefsPrivate.getString(SERVER_KEY, ""))+"@"+mFileUtils._d2(mPrefsPrivate.getString(BackQuery, ""))+" "+"80"+"\n";
            } else {
                return "\nremote "+mFileUtils._d2(mPrefsPrivate.getString(FrontQuery, ""))+"@"+mFileUtils._d2(mPrefsPrivate.getString(SERVER_KEY, ""))+" "+"80"+"\n";
            }
        }
        return "\nremote "+mFileUtils._d2(mPrefsPrivate.getString(SERVER_KEY, ""))+" "+mPrefsPrivate.getString(SERVER_PORT_KEY, "")+"\n";
    }

    public String getOvpnConfig()
    {
        String configure = "";
        try{
            configure += getOvpnCert().contains("remote ")?getOvpnCert().replace("remote ","##remote "):getOvpnCert();
            configure += getRemote();
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new StringReader(configure));
            VpnProfile vp = cp.convertProfile();
            vp.mName = "HarliesDevX";
            if (vp.checkProfile(mContext) != R.string.no_error_found)
            {
                throw new RemoteException(mContext.getString(vp.checkProfile(mContext)));
            }
            vp.mProfileCreator = mContext.getPackageName();
            vp.mUsername = getSecureString(USERNAME_KEY);
            vp.mPassword = getSecureString(PASSWORD_KEY);
            vp.mUseCustomConfig = true;
            vp.mCustomConfigOptions = new StringBuffer().append(vp.mCustomConfigOptions).append("http-proxy 127.0.0.1 8989").toString();
            return vp.getConfigFile(mContext, true);
        }catch(Exception e){
            return null;
        }
    }
    public void setPayloadName(String nm){
        editor.putString("OVPN_PAYLOAD_NAME", nm).apply();
    }
    public String getPayloadName(){
        return prefs.getString("OVPN_PAYLOAD_NAME","");
    }
    public void setServerName(String proto){
        editor.putString("OVPN_SERVER_NAME", proto).apply();
    }
    public String getServerName(){
        return prefs.getString("OVPN_SERVER_NAME","");
    }

    public void setUDPConfig(String obfs){
        secureEditor.putString(DIRECT_UDP_CONFIG_KEY, obfs).apply();
    }
    public void setOvpnCert(String ca) {
        editor.putString("OVPN_CERT_KEY", ca).apply();
    }
    public String getOvpnCert(){
        String ca = FileUtils.showJson(prefs.getString("OVPN_CERT_KEY",""));
        if(ca.contains("/idU6xeM87YqAb2SHftTwWp4zrL21t8tN/")){
            return ca.replace("/idU6xeM87YqAb2SHftTwWp4zrL21t8tN/","");
        }
        return ca;
    }

    public void setSni(String sni){
        secureEditor.putString(SNI_HOST_KEY, sni).apply();
    }
    public void setPayload(String payload){
        secureEditor.putString(CUSTOM_PAYLOAD_KEY, payload).apply();
    }
    public void setServerHost(String ip){
        secureEditor.putString(SERVER_KEY, ip).apply();
    }
    public void setServerPort(String port){
        secureEditor.putString(SERVER_PORT_KEY, port).apply();
    }
    public void setProxyHost(String proxy){
        secureEditor.putString(PROXY_IP_KEY, proxy).apply();
    }
    public void setProxyPort(String proxyPort){
        secureEditor.putString(PROXY_PORTA_KEY, proxyPort).apply();
    }
    public void setUser(String str){
        secureEditor.putString(USERNAME_KEY,str).apply();
    }
    public void setUserPass(String str){
        secureEditor.putString(PASSWORD_KEY,str).apply();
    }

    public String getNetworkType(){
        return prefs.getString(SERVER_TYPE, "UDP");
    }
    public void setNetworkType(String str){
        editor.putString(SERVER_TYPE,str).apply();
    }

    public void setPaylod_type(int type)
    {
        editor.putInt(PAYLOAD_TYPE_KEY, type).apply();
    }
    public int getPayload_type(){
        return prefs.getInt(PAYLOAD_TYPE_KEY,0);
    }

    public boolean isQueryMode()
    {
        return prefs.getBoolean("isQueryMode", false);
    }
    public void setIsQueryMode(boolean enable)
    {
        editor.putBoolean("isQueryMode", enable).apply();
    }
    public void setFrontQuery(String query)
    {
        secureEditor.putString(FrontQuery, query).apply();
    }
    public void setBackQuery(String query)
    {
        secureEditor.putString(BackQuery, query).apply();
    }
}
