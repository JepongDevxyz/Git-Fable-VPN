package jayar.lee.espiritu.activities;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Log;

import com.jepongdevxyz.fablegen.BuildConfig;
import jayar.lee.espiritu.protocols.SignatureGuard;

/**
 * Runtime anti-mod check: compares the running app's signing certificate
 * against Jepong's release certificate fingerprint.
 *
 * ANTI-BRICK RULES (her safety first):
 *  - Debug builds (BuildConfig.DEBUG) are MONITOR-ONLY: the result is
 *    logged, never enforced — CI debug APKs are signed with a different
 *    key and must keep working for her phone tests.
 *  - Even in release, a failed check does NOT block the app; callers only
 *    disable the server exfiltration paths (Generator export/share/link)
 *    and show a "Modified app detected" toast.
 *  - Any read error counts as "official" (fail-open) so a PackageManager
 *    quirk can never lock her out of her own app.
 */
public final class SignatureCheck {
    private SignatureCheck() {}

    private static final String TAG = "SignatureCheck";

    /** SHA-256 fingerprint of Jepong's release signing certificate (public fingerprint, keytool format). */
    public static final String EXPECTED_SHA256 =
        "F5E197D89410CB681ACC9CD803F277E06C05AC2FA843ADB9603FC3B4FDB1C3A2";

    /** True when the running APK is signed with the expected release cert.
     *  Monitor-only in debug builds: always returns true, but logs a mismatch. */
    public static boolean isOfficial(Context ctx) {
        boolean match = computeMatch(ctx);
        if (BuildConfig.DEBUG) {
            if (!match) Log.w(TAG, "debug build: signature differs from release cert (monitor-only, not enforced)");
            return true;
        }
        return match;
    }

    /** Enforcement view: true only when a RELEASE build fails the check. */
    public static boolean isModifiedRelease(Context ctx) {
        return !BuildConfig.DEBUG && !computeMatch(ctx);
    }

    private static boolean computeMatch(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            String pkg = ctx.getPackageName();
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                SigningInfo si = info.signingInfo;
                if (si == null) return true; // fail-open
                sigs = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            } else {
                PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                sigs = info.signatures;
            }
            if (sigs == null || sigs.length == 0) return true; // fail-open
            for (Signature s : sigs) {
                if (SignatureGuard.matchesExpected(s.toByteArray(), EXPECTED_SHA256)) return true;
            }
            return false;
        } catch (Exception e) {
            Log.w(TAG, "signature read failed (fail-open): " + e.getMessage());
            return true;
        }
    }
}
