package jayar.lee.espiritu.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.List;

import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.protocols.ConfigCrypto;
import jayar.lee.espiritu.protocols.DeepLinkCodec;
import jayar.lee.espiritu.protocols.JepongVpsFactory;
import jayar.lee.espiritu.protocols.ServerProfile;

/**
 * Import screen — every control is real, all paths feed the ONE import
 * pipeline (ImportHelper) into the ONE profile store (ProfilePrefs):
 *  - Paste field + "Add profiles": share links, subscriptions, WireGuard
 *    .conf, .ovpn, GITFABLEVPN/1 share text, and GFVENC1 encrypted text
 *    (decrypted with the password field — wrong password imports nothing).
 *  - "Import from clipboard": fills the paste field from the clipboard.
 *  - Deep link: gitfablevpn://import?d=<base64url> (VIEW intent) decodes
 *    into the same pipeline; encrypted payloads ask for the password.
 *  - Subscription URL + Fetch: HttpURLConnection on a background thread;
 *    the URL persists (cc_sub_url) and, when the auto-update switch is ON
 *    (default OFF), ServersActivity refreshes from it on open.
 *  - Jepong VPS Quick-Add: fields map 1:1 onto JepongVpsFactory.Creds and
 *    buildAll() creates all 13 service profiles.
 * Nothing here depends on the old BuildConfig remote-config raw links.
 */
public class ImportActivity extends AppCompatActivity {

    private EditText pasteField, passField, urlField;
    // SwitchCompat: under AppCompatActivity the layout <Switch> inflates
    // as SwitchCompat (it does NOT extend android.widget.Switch).
    private SwitchCompat autoSwitch;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        pasteField = findViewById(R.id.cc_imp_text);
        passField = findViewById(R.id.cc_imp_pass);
        urlField = findViewById(R.id.cc_imp_url);
        autoSwitch = findViewById(R.id.cc_imp_autoupdate);

        urlField.setText(ProfilePrefs.subUrl());
        autoSwitch.setChecked(ProfilePrefs.subAutoEnabled());
        autoSwitch.setOnCheckedChangeListener((b, checked) -> {
            ProfilePrefs.setSubAuto(checked);
            ProfilePrefs.setSubUrl(urlField.getText().toString());
            brandedToast("Subscription auto-update " + (checked ? "ON" : "OFF"));
        });

        findViewById(R.id.cc_imp_back).setOnClickListener(v -> finish());

        findViewById(R.id.cc_imp_clipboard).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).coerceToText(this);
                    if (text != null && text.length() > 0) {
                        pasteField.setText(text.toString());
                        brandedToast("Clipboard pasted — tap Add profiles");
                        return;
                    }
                }
            }
            brandedToast("Clipboard is empty");
        });

        findViewById(R.id.cc_imp_add).setOnClickListener(v -> {
            String text = pasteField.getText().toString();
            if (ConfigCrypto.isEncrypted(text) && passField.getText().toString().isEmpty()) {
                brandedToast("This config is encrypted — enter its password first");
                passField.requestFocus();
                return;
            }
            int n = ImportHelper.importText(text, passField.getText().toString());
            if (n == 0) {
                brandedToast("No valid profiles found (check the text or password)");
                return;
            }
            brandedToast("Imported " + n + (n == 1 ? " profile" : " profiles"));
            finish();
        });

        findViewById(R.id.cc_imp_fetch).setOnClickListener(v -> {
            final String url = urlField.getText().toString().trim();
            if (url.isEmpty()) { brandedToast("Enter a subscription URL first"); return; }
            ProfilePrefs.setSubUrl(url);
            brandedToast("Fetching subscription...");
            new Thread(() -> {
                try {
                    String body = ImportHelper.fetchUrl(url);
                    final int n = ImportHelper.importText(body, "");
                    runOnUiThread(() -> {
                        if (n > 0) { brandedToast("Imported " + n + " subscription profiles"); finish(); }
                        else brandedToast("Subscription fetched but no valid profiles found");
                    });
                } catch (final Exception e) {
                    runOnUiThread(() -> brandedToast("Fetch failed: " + e.getMessage()));
                }
            }).start();
        });

        findViewById(R.id.cc_qa_add).setOnClickListener(v -> {
            JepongVpsFactory.Creds c = new JepongVpsFactory.Creds();
            c.domain = field(R.id.cc_qa_domain);
            if (c.domain.isEmpty()) {
                brandedToast("Enter your VPS domain first");
                return;
            }
            c.uuidVless = field(R.id.cc_qa_vless_uuid);
            c.uuidVmess = field(R.id.cc_qa_vmess_uuid);
            c.trojanPass = field(R.id.cc_qa_trojan_pass);
            c.ssPassword = field(R.id.cc_qa_ss_pass);
            c.hyPassword = field(R.id.cc_qa_hy2_pass);
            c.sshUser = field(R.id.cc_qa_ssh_user);
            c.sshPass = field(R.id.cc_qa_ssh_pass);
            c.nsDomain = field(R.id.cc_qa_ns);
            c.dnsttPubKey = field(R.id.cc_qa_pubkey);
            c.udpPsk = field(R.id.cc_qa_udp_psk);
            List<ServerProfile> all = JepongVpsFactory.buildAll(c);
            ProfilePrefs.addAll(all);
            if (ProfilePrefs.selectedName().isEmpty() && !all.isEmpty()) {
                ProfilePrefs.setSelectedName(all.get(0).name);
            }
            brandedToast("Added " + all.size() + " Jepong VPS profiles");
            finish();
        });

        handleDeepLink(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    /** gitfablevpn://import?d=<base64url> — decodes into the same import pipeline. */
    private void handleDeepLink(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null || !DeepLinkCodec.SCHEME.equals(data.getScheme())
            || !DeepLinkCodec.HOST.equals(data.getHost())) return;
        String payload = DeepLinkCodec.extractPayload(data.toString());
        if (payload.isEmpty()) { brandedToast("Import link had no data"); return; }
        pasteField.setText(payload);
        if (ConfigCrypto.isEncrypted(payload)) {
            brandedToast("Encrypted config received — enter its password, then tap Add profiles");
            passField.requestFocus();
            return;
        }
        int n = ImportHelper.importText(payload, "");
        if (n > 0) {
            brandedToast("Imported " + n + (n == 1 ? " profile" : " profiles") + " from link");
            finish();
        } else {
            brandedToast("Link received — review the text, then tap Add profiles");
        }
    }

    private String field(int id) {
        EditText e = findViewById(id);
        return e == null ? "" : e.getText().toString().trim();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    /** Bottom-center branded toast (her UI rule). */
    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(28));
        t.show();
    }
}
