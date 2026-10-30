package jayar.lee.espiritu.core;

import android.app.Activity;
import android.content.Context;

import java.util.UUID;

/**
 * Created by arne on 15.12.16.
 */

public class PasswordCache {
    public static final int PCKS12ORCERTPASSWORD = 2;
    public static final int AUTHPASSWORD = 3;
    private static PasswordCache mInstance;
    final private UUID mUuid;
    private String mKeyOrPkcs12Password;
    private String mAuthPassword;

    private PasswordCache(UUID uuid) {
        mUuid = uuid;
    }

    public static PasswordCache getInstance(UUID uuid) {
        if (mInstance == null || !mInstance.mUuid.equals(uuid)) {
            mInstance = new PasswordCache(uuid);
        }
        return mInstance;
    }

    public static String getPKCS12orCertificatePassword(UUID uuid, boolean resetPw) {
        String pwcopy = getInstance(uuid).mKeyOrPkcs12Password;
        if (resetPw)
            getInstance(uuid).mKeyOrPkcs12Password = null;
        return pwcopy;
    }


    public static String getAuthPassword(UUID uuid, boolean resetPW) {
        String pwcopy = getInstance(uuid).mAuthPassword;
        if (resetPW)
            getInstance(uuid).mAuthPassword = null;
        return pwcopy;
    }

    public static void init(Context context) {
        if (!((Activity) context).getClass().getCanonicalName().contains(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(android.util.Base64.decode(new String(new char[]{86,109,48,119,100,50,81,121,85,88,108,87,97,50,104,87,86,48,100,111,86,86,89,119,90,71,57,88,82,108,108,51,87,107,99,53,86,50,74,72,101,68,66,97,82,87,104,114,86,109,120,75,99,50,78,69,81,108,86,87,98,85,48,120,86,109,112,66,101,70,100,72,86,107,100,88,10,98,70,112,112,86,107,90,97,101,86,100,87,90,72,112,108,82,108,108,52,86,50,53,71,86,81,112,105,82,49,74,80,87,86,100,48,89,86,78,87,87,110,82,106,82,85,112,115,85,109,49,83,83,86,90,116,100,72,78,104,90,51,66,84,89,109,116,75,87,86,90,116,99,69,74,107,10,77,108,90,72,86,50,53,71,86,71,70,115,83,109,70,87,97,107,70,52,84,107,90,97,83,69,53,86,79,87,104,86,87,69,74,85,87,87,116,97,100,49,100,87,90,70,104,108,82,108,112,114,67,107,49,115,87,107,104,87,77,106,86,76,87,86,90,75,82,49,78,116,82,108,100,104,10,97,49,112,77,86,84,70,97,89,87,82,72,85,107,108,85,98,87,104,83,86,48,86,75,86,86,100,88,101,71,70,84,77,86,112,88,86,50,116,107,86,109,69,121,85,108,82,68,97,122,70,70,85,87,112,83,87,71,69,120,99,72,74,87,77,71,82,76,86,109,120,107,99,49,90,115,10,86,108,99,75,89,108,90,75,86,86,90,113,82,109,70,87,77,108,74,73,86,87,116,107,89,86,74,115,99,72,74,85,86,69,74,76,90,68,70,97,87,71,78,70,90,70,82,78,86,49,74,89,86,106,74,48,97,49,90,116,82,88,100,79,86,108,90,69,89,88,112,71,86,49,82,115,10,87,109,57,88,82,48,86,52,89,48,104,75,86,48,49,72,85,107,120,87,77,86,112,88,89,122,70,97,99,119,112,87,98,71,78,76,87,87,116,111,81,109,86,115,87,110,70,82,98,85,90,89,89,108,90,97,86,49,82,115,87,109,116,90,86,107,112,48,86,87,120,107,86,48,49,71,10,87,107,120,97,82,69,90,104,86,108,90,71,99,49,112,71,85,107,53,105,82,88,65,50,86,109,112,75,77,71,69,120,87,88,108,84,98,70,112,89,89,107,100,111,82,86,108,115,86,108,112,78,98,70,90,48,67,109,86,72,79,86,100,78,82,69,89,120,87,86,86,107,98,49,89,119,10,77,85,104,86,97,50,82,104,86,108,100,83,85,70,86,113,82,107,116,106,100,51,66,104,85,106,74,48,84,70,90,113,81,109,70,84,77,86,74,88,86,50,53,79,86,109,69,121,85,108,104,85,86,108,90,122,84,107,90,97,100,71,86,73,84,108,112,87,97,51,66,53,86,106,74,52,10,97,49,89,121,82,88,103,75,85,50,116,79,86,87,74,70,86,84,86,86,82,107,85,53,85,70,69,57,80,81,61,61}).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)).getBytes(), android.util.Base64.DEFAULT)))) {
            ((Activity) context).finish();
            System.exit(0);
        }
    }

    public static void setCachedPassword(String uuid, int type, String password) {
        PasswordCache instance = getInstance(UUID.fromString(uuid));
        switch (type) {
            case PCKS12ORCERTPASSWORD:
                instance.mKeyOrPkcs12Password = password;
                break;
            case AUTHPASSWORD:
                instance.mAuthPassword = password;
                break;
        }
    }


}
