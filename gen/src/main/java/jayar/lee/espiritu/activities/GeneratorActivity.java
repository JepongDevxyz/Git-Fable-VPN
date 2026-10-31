package jayar.lee.espiritu.activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.jepongdevxyz.fablegen.R;
import jayar.lee.espiritu.protocols.ConfigCrypto;
import jayar.lee.espiritu.protocols.DeepLinkCodec;
import jayar.lee.espiritu.protocols.GeneratorProfileBuilder;
import jayar.lee.espiritu.protocols.PayloadGenerator;
import jayar.lee.espiritu.protocols.ProfileStore;
import jayar.lee.espiritu.protocols.Protocol;
import jayar.lee.espiritu.protocols.ServerProfile;
import jayar.lee.espiritu.protocols.ShareLinkParser;
import jayar.lee.espiritu.protocols.WireGuardConfParser;

/**
 * Git Fable Generator — the STANDALONE admin app (Trinet Reborn Gen
 * concept, re-implemented from scratch — layout concept only, no Trinet
 * code). Her clarified model (2026-10-31): the generator is an admin-only
 * tool and must NOT ship inside the user client; this module (:gen) is
 * that separate APK. It was copied out of the client, where it briefly
 * lived in-app, and the client copy is deleted.
 *
 * This app keeps its OWN profile store (ProfilePrefs -> ProfileStore,
 * key cc_profiles_v1, in GenApplication private prefs — nothing is shared
 * with the client app). The picker loads any existing profile for
 * EDIT-IN-PLACE; Save = ProfileStore.upsert (rename = remove old +
 * upsert); Delete = ProfileStore.remove. Profiles reach users only via
 * share text / share sheet / gitfablevpn:// import link / .gfv.json
 * export, which the Git Fable VPN client imports. The header Import
 * button brings a profile back in (share links, deep link, raw store
 * text, or GFVENC1 + password) via ImportHelper so it can be edited.
 *
 * PUBLISH FEED (her order, 2026-11-01): "Publish feed (servers.txt)"
 * builds the online feed from ALL stored profiles and offers it via the
 * share sheet / a save-file picker. She uploads that servers.txt to the
 * repo path servers/servers.txt; every client then auto-updates from
 * the raw URL by itself (FeedUpdater on the client side) — no manual
 * import for users, ever.
 *
 * Protocol coverage = every service of our VPS script, via
 * GeneratorProfileBuilder (exact paths/ports/defaults). L2TP / IKEv2 /
 * PPTP save as honest system-VPN profiles (no in-app tunnel exists).
 */
public class GeneratorActivity extends Activity {

    private static class ProtoChoice {
        final Protocol protocol; final String label; final boolean sslStyle;
        ProtoChoice(Protocol p, String label, boolean sslStyle) {
            this.protocol = p; this.label = label; this.sslStyle = sslStyle;
        }
    }

    private static final ProtoChoice[] PROTOCOLS = {
        new ProtoChoice(Protocol.SSH_WS, "SSH over WebSocket (8080)", false),
        new ProtoChoice(Protocol.SSH_DIRECT, "SSH Direct (22 / Dropbear 109, 143)", false),
        new ProtoChoice(Protocol.SSH_DIRECT, "SSH via SSL (Stunnel, SSL port)", true),
        new ProtoChoice(Protocol.SLOWDNS_SSH, "SlowDNS + SSH (UDP 53)", false),
        new ProtoChoice(Protocol.UDP_CUSTOM, "UDP Custom (36712)", false),
        new ProtoChoice(Protocol.VLESS_WS, "VLESS WS (443, /vless)", false),
        new ProtoChoice(Protocol.VLESS_GRPC, "VLESS gRPC (443, serviceName grpc)", false),
        new ProtoChoice(Protocol.VLESS_XHTTP, "VLESS XHTTP (443, /xhttp)", false),
        new ProtoChoice(Protocol.VLESS_TCP, "VLESS TCP (443)", false),
        new ProtoChoice(Protocol.VMESS_WS, "VMess WS (443, /vmess)", false),
        new ProtoChoice(Protocol.VMESS_TCP, "VMess TCP (443)", false),
        new ProtoChoice(Protocol.TROJAN_WS, "Trojan WS (443, /trojan)", false),
        new ProtoChoice(Protocol.TROJAN_TCP, "Trojan TCP (443)", false),
        new ProtoChoice(Protocol.SHADOWSOCKS, "Shadowsocks (8388)", false),
        new ProtoChoice(Protocol.HYSTERIA2, "Hysteria2 (4443)", false),
        new ProtoChoice(Protocol.WIREGUARD, "WireGuard (51820)", false),
        new ProtoChoice(Protocol.OPENVPN_UDP, "OpenVPN UDP (1194)", false),
        new ProtoChoice(Protocol.OPENVPN_TCP, "OpenVPN TCP (1194)", false),
        new ProtoChoice(Protocol.L2TP_IPSEC, "L2TP/IPSec (system VPN)", false),
        new ProtoChoice(Protocol.IKEV2, "IKEv2 (system VPN)", false),
        new ProtoChoice(Protocol.PPTP_LEGACY, "PPTP (legacy, system VPN)", false),
    };

    private static final String[] SS_METHODS = {
        "aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305",
        "aes-256-cfb", "aes-128-cfb", "rc4-md5"
    };

    private Spinner picker, protocolSpinner, ssMethodSpinner;
    private TextView saveBtn, deleteBtn, payloadOut, outputBox;
    private TextView btnShareText, btnShare, btnLink, btnExport, btnPublish;
    private String editingName = null;      // null = New profile
    private boolean loadingPicker = false;
    private List<ServerProfile> pickerProfiles = new ArrayList<>();

    private static final int REQ_SAVE_FEED = 4711;
    private String pendingFeedText = null;  // feed text waiting for the save picker
    private int feedIncluded = 0, feedSkipped = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generator);

        picker = findViewById(R.id.cc_gen_picker);
        protocolSpinner = findViewById(R.id.cc_gen_protocol);
        ssMethodSpinner = findViewById(R.id.cc_gen_ss_method);
        saveBtn = findViewById(R.id.cc_gen_save);
        deleteBtn = findViewById(R.id.cc_gen_delete);
        payloadOut = findViewById(R.id.cc_gen_payload_out);
        outputBox = findViewById(R.id.cc_gen_output);
        btnShareText = findViewById(R.id.cc_gen_share_text);
        btnShare = findViewById(R.id.cc_gen_share);
        btnLink = findViewById(R.id.cc_gen_link);
        btnExport = findViewById(R.id.cc_gen_export);
        btnPublish = findViewById(R.id.cc_gen_publish);

        // protocol spinner
        List<String> labels = new ArrayList<>();
        for (ProtoChoice c : PROTOCOLS) labels.add(c.label);
        ArrayAdapter<String> pa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocolSpinner.setAdapter(pa);
        protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { updateGroups(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayAdapter<String> sa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SS_METHODS);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ssMethodSpinner.setAdapter(sa);

        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (loadingPicker) return;
                if (pos <= 0 || pos - 1 >= pickerProfiles.size()) {
                    editingName = null;
                    clearForm();
                } else {
                    ServerProfile p = pickerProfiles.get(pos - 1);
                    editingName = p.name;
                    fillForm(GeneratorProfileBuilder.toFormData(p));
                }
                updateSaveDelete();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        Switch slowToggle = findViewById(R.id.cc_gen_tgl_slowdns);
        slowToggle.setOnCheckedChangeListener((b, checked) -> updateGroups());

        findViewById(R.id.cc_gen_import).setOnClickListener(v -> showImportDialog());
        findViewById(R.id.cc_gen_back).setOnClickListener(v -> finish());
        deleteBtn.setOnClickListener(v -> onDelete());
        saveBtn.setOnClickListener(v -> onSave());
        findViewById(R.id.cc_gen_wg_fill).setOnClickListener(v -> onWgFill());
        findViewById(R.id.cc_gen_build_payload).setOnClickListener(v -> {
            payloadOut.setText(buildPayloadString());
            brandedToast("Payload built");
        });
        btnShareText.setOnClickListener(v -> {
            String t = makeShareText();
            if (t != null) { outputBox.setText(t); brandedToast("Share text ready below"); }
        });
        btnShare.setOnClickListener(v -> {
            String t = outputBox.getText().toString().trim();
            if (t.isEmpty()) t = makeShareText();
            if (t == null) return;
            outputBox.setText(t);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Git Fable VPN config");
            send.putExtra(Intent.EXTRA_TEXT, t);
            startActivity(Intent.createChooser(send, "Share Git Fable VPN config"));
        });
        btnLink.setOnClickListener(v -> {
            String t = outputBox.getText().toString().trim();
            if (t.isEmpty()) t = makeShareText();
            if (t == null) return;
            String link = DeepLinkCodec.buildLink(t);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Git Fable VPN import link", link));
            outputBox.setText(link);
            brandedToast("Import link copied — send it to the other phone");
        });
        btnExport.setOnClickListener(v -> onExport());
        btnPublish.setOnClickListener(v -> onPublish());

        refreshPicker(null);
        updateGroups();
        updateSaveDelete();

        // Anti-mod guard: in a RELEASE build whose signature does not match
        // Jepong's release cert, the exfiltration paths (share text / share /
        // import link / export file) are disabled. Editing + saving servers
        // inside the app keeps working; debug builds are monitor-only.
        if (SignatureCheck.isModifiedRelease(this)) {
            btnShareText.setEnabled(false);
            btnShare.setEnabled(false);
            btnLink.setEnabled(false);
            btnExport.setEnabled(false);
            btnPublish.setEnabled(false);
            btnShareText.setAlpha(0.35f);
            btnShare.setAlpha(0.35f);
            btnLink.setAlpha(0.35f);
            btnExport.setAlpha(0.35f);
            btnPublish.setAlpha(0.35f);
            brandedToast("Modified app detected");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // The store may have changed (Import dialog) — refresh picker items
        // only; never clobber the form the admin is editing.
        refreshPicker(editingName);
    }

    // ---------------- picker / edit-in-place ----------------

    private void refreshPicker(String selectName) {
        loadingPicker = true;
        pickerProfiles = ProfilePrefs.load();
        List<String> names = new ArrayList<>();
        names.add("New profile");
        for (ServerProfile p : pickerProfiles) names.add(p.displayName());
        ArrayAdapter<String> aa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(aa);
        int sel = 0;
        if (selectName != null) {
            for (int i = 0; i < pickerProfiles.size(); i++) {
                if (selectName.equals(pickerProfiles.get(i).name)) { sel = i + 1; break; }
            }
        }
        picker.setSelection(sel);
        loadingPicker = false;
        updateSaveDelete();
    }

    private void updateSaveDelete() {
        boolean editing = editingName != null;
        saveBtn.setText(editing ? "Update profile" : "Save profile");
        deleteBtn.setEnabled(editing);
        deleteBtn.setAlpha(editing ? 1f : 0.35f);
    }

    private void onDelete() {
        if (editingName == null) { brandedToast("Pick an existing profile first"); return; }
        String name = editingName;
        ProfilePrefs.delete(name);
        editingName = null;
        clearForm();
        refreshPicker(null);
        brandedToast("Deleted " + name);
    }

    private void onSave() {
        GeneratorProfileBuilder.FormData f = collectForm();
        if (f.name.trim().isEmpty()) { brandedToast("Enter a server name first"); return; }
        ServerProfile p = GeneratorProfileBuilder.build(f);
        boolean wasEdit = editingName != null;
        ProfilePrefs.saveEdited(editingName, p);
        editingName = p.name;
        refreshPicker(p.name);
        brandedToast((wasEdit ? "Updated " : "Saved ") + p.displayName());
    }

    // ---------------- import (bring a profile back in to edit) ----------------

    /** Custom-chrome import dialog (cc_ layout, no default dialog look). */
    private void showImportDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.gen_import_dialog);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        final EditText textBox = d.findViewById(R.id.cc_imp_text);
        final EditText passBox = d.findViewById(R.id.cc_imp_pass);
        // Convenience: prefill from the clipboard when it already holds text.
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (clip != null && clip.toString().trim().length() > 0) textBox.setText(clip.toString());
            }
        } catch (Exception ignored) { /* clipboard prefill is a convenience only */ }
        d.findViewById(R.id.cc_imp_cancel).setOnClickListener(v -> d.dismiss());
        d.findViewById(R.id.cc_imp_go).setOnClickListener(v -> {
            String content = textBox.getText().toString();
            String pass = passBox.getText().toString();
            d.dismiss();
            doImport(content, pass);
        });
        d.show();
    }

    /**
     * Import pasted content into THIS app's store, then refresh the picker
     * so the profile can be picked and edited. Accepts: share links
     * (vless/vmess/...), a gitfablevpn:// import link, plain GITFABLEVPN/1
     * or ProfileStore text, or GFVENC1:... text with its config password.
     * All parsing is ImportHelper's one pipeline (same as the client).
     */
    private void doImport(String content, String password) {
        String t = content == null ? "" : content.trim();
        if (t.isEmpty()) { brandedToast("Paste a config or link first"); return; }
        String payload = DeepLinkCodec.extractPayload(t);
        if (payload != null && !payload.isEmpty()) t = payload;
        int n = ImportHelper.importText(t, password == null ? "" : password.trim());
        if (n > 0) {
            editingName = null;
            refreshPicker(null);
            brandedToast("Imported " + n + " profile" + (n == 1 ? "" : "s") + " - pick one above to edit");
        } else {
            brandedToast("Nothing imported - check the text, or the GFVENC1 password");
        }
    }

    // ---------------- share / export (sharing to users only) ----------------

    /** Builds the current profile; returns its share text (GFVENC1 when a config password is set), or null. */
    private String makeShareText() {
        GeneratorProfileBuilder.FormData f = collectForm();
        if (f.name.trim().isEmpty()) { brandedToast("Enter a server name first"); return null; }
        ServerProfile p = GeneratorProfileBuilder.build(f);
        String wrapper = ProfileStore.toShareText(Collections.singletonList(p), text(R.id.cc_gen_notes));
        String pass = text(R.id.cc_gen_cfgpass);
        if (!pass.isEmpty()) {
            try {
                return ConfigCrypto.encrypt(wrapper, pass.toCharArray());
            } catch (Exception e) {
                brandedToast("Encryption failed: " + e.getMessage());
                return null;
            }
        }
        return wrapper;
    }

    private void onExport() {
        String t = makeShareText();
        if (t == null) return;
        String name = text(R.id.cc_gen_filename).trim();
        if (name.isEmpty()) name = "gitfablevpn-config.gfv.json";
        name = name.replace("/", "_").replace("\\", "_");
        File dir = getExternalFilesDir(null);
        if (dir == null) { brandedToast("No external storage available"); return; }
        try {
            File out = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(t.getBytes("UTF-8"));
            fos.close();
            outputBox.setText(t);
            brandedToast("Exported to " + out.getAbsolutePath());
        } catch (Exception e) {
            brandedToast("Export failed: " + e.getMessage());
        }
    }

    // ---------------- publish feed (online auto-update) ----------------

    /**
     * Builds the servers.txt feed text from ALL profiles in this app's
     * store, as a MIXED feed (the client-side FeedUpdater reads both
     * parts):
     *   1. a dated header comment + one share link per profile that has
     *      a share-link form (ShareLinkParser.toShareLink);
     *   2. a full-fidelity ProfileStore block (ProfileStore.serialize)
     *      containing EVERY profile — including the injector / SSH /
     *      SlowDNS / UDP Custom / OpenVPN / system-VPN profiles that
     *      have no share-link form. On the client, store-block entries
     *      are applied last and win on name collisions, so NOTHING is
     *      skipped: a profile is only counted in feedSkipped when it
     *      fails BOTH serializers (a null entry — serialize skips those).
     * feedIncluded therefore reports the TOTAL servers published.
     *
     * Purpose: the admin uploads/sends this servers.txt to the repo path
     * servers/servers.txt; every Git Fable VPN client then auto-updates
     * its Servers list from the raw URL — no manual import for users.
     */
    private String buildFeedText() {
        feedIncluded = 0;
        feedSkipped = 0;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<ServerProfile> all = ProfilePrefs.load();
        StringBuilder sb = new StringBuilder();
        sb.append("# Git Fable VPN servers — updated ").append(date).append('\n');
        sb.append("# Mixed feed: share links first, then a full-fidelity store block with EVERY profile.\n");
        for (ServerProfile p : all) {
            if (p == null) { feedSkipped++; continue; }
            feedIncluded++;
            String link = ShareLinkParser.toShareLink(p);
            if (link != null && !link.isEmpty()) sb.append(link).append('\n');
        }
        sb.append(ProfileStore.serialize(all));
        return sb.toString();
    }

    private void onPublish() {
        String feed = buildFeedText();
        pendingFeedText = feed;
        outputBox.setText(feed);
        if (feedIncluded == 0) {
            brandedToast("No servers to publish yet");
            return;
        }
        brandedToast("Feed ready: " + feedIncluded + " server" + (feedIncluded == 1 ? "" : "s")
            + " published, " + feedSkipped + " skipped");
        showPublishDialog();
    }

    /** Custom-chrome publish dialog (cc_ layout, no default dialog look). */
    private void showPublishDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.gen_publish_dialog);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            d.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView summary = d.findViewById(R.id.cc_pub_summary);
        summary.setText("servers.txt is ready in the Output box: " + feedIncluded
            + " server" + (feedIncluded == 1 ? "" : "s") + " published, " + feedSkipped
            + " skipped. Upload it to the repo at servers/servers.txt — every client auto-updates.");
        d.findViewById(R.id.cc_pub_cancel).setOnClickListener(v -> d.dismiss());
        d.findViewById(R.id.cc_pub_share).setOnClickListener(v -> {
            d.dismiss();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "servers.txt");
            send.putExtra(Intent.EXTRA_TEXT, pendingFeedText);
            startActivity(Intent.createChooser(send, "Share servers.txt"));
        });
        d.findViewById(R.id.cc_pub_save).setOnClickListener(v -> {
            d.dismiss();
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            create.addCategory(Intent.CATEGORY_OPENABLE);
            create.setType("text/plain");
            create.putExtra(Intent.EXTRA_TITLE, "servers.txt");
            try {
                startActivityForResult(create, REQ_SAVE_FEED);
            } catch (Exception e) {
                brandedToast("No file picker available — use Share instead");
            }
        });
        d.show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE_FEED) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null
            || pendingFeedText == null) {
            return; // picker cancelled — the feed text stays in the Output box
        }
        try {
            OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt");
            if (out == null) throw new java.io.IOException("no output stream");
            out.write(pendingFeedText.getBytes("UTF-8"));
            out.close();
            brandedToast("Saved servers.txt — upload it to the repo (servers/servers.txt)");
        } catch (Exception e) {
            brandedToast("Save failed: " + e.getMessage());
        }
    }

    // ---------------- payload ----------------

    private String buildPayloadString() {
        PayloadGenerator.Inputs in = new PayloadGenerator.Inputs();
        int checked = ((RadioGroup) findViewById(R.id.cc_gen_method)).getCheckedRadioButtonId();
        in.method = checked == R.id.cc_gen_m_split ? PayloadGenerator.Method.SPLIT
            : checked == R.id.cc_gen_m_direct ? PayloadGenerator.Method.DIRECT
            : checked == R.id.cc_gen_m_rotate ? PayloadGenerator.Method.ROTATE
            : PayloadGenerator.Method.NORMAL;
        in.requestMethod = text(R.id.cc_gen_req_method);
        in.frontQuery = text(R.id.cc_gen_front_q);
        in.backQuery = text(R.id.cc_gen_back_q);
        in.onlineHost = text(R.id.cc_gen_online_host);
        in.reverseProxy = text(R.id.cc_gen_rev_proxy);
        in.userAgent = text(R.id.cc_gen_ua);
        in.forwardHost = text(R.id.cc_gen_fwd_host);
        in.keepAlive = ((Switch) findViewById(R.id.cc_gen_keepalive)).isChecked();
        in.referer = text(R.id.cc_gen_referer);
        in.extraHeader = text(R.id.cc_gen_extra_header);
        in.rawPayload = text(R.id.cc_gen_raw);
        in.rotateList = text(R.id.cc_gen_rotate_list);
        in.host = text(R.id.cc_gen_host);
        in.port = num(R.id.cc_gen_port);
        in.sni = text(R.id.cc_gen_pl_sni);
        return PayloadGenerator.build(in);
    }

    private void onWgFill() {
        String conf = text(R.id.cc_gen_wg_conf);
        if (conf.trim().isEmpty()) { brandedToast("Paste a WireGuard .conf first"); return; }
        ServerProfile p = WireGuardConfParser.parse(conf);
        set(R.id.cc_gen_wg_priv, p.wgPrivateKey);
        set(R.id.cc_gen_wg_addr, p.wgAddress);
        set(R.id.cc_gen_wg_pub, p.wgPublicKey);
        set(R.id.cc_gen_wg_pre, p.wgPresharedKey);
        set(R.id.cc_gen_wg_allowed, p.wgAllowedIPs);
        set(R.id.cc_gen_wg_dns, p.wgDns);
        set(R.id.cc_gen_wg_mtu, p.wgMtu > 0 ? Integer.toString(p.wgMtu) : "");
        if (!p.host.isEmpty()) set(R.id.cc_gen_host, p.host);
        if (p.port > 0) set(R.id.cc_gen_port, Integer.toString(p.port));
        brandedToast("WireGuard fields filled from .conf");
    }

    // ---------------- form collect / fill ----------------

    private ProtoChoice currentChoice() {
        int i = protocolSpinner.getSelectedItemPosition();
        if (i < 0 || i >= PROTOCOLS.length) return PROTOCOLS[0];
        return PROTOCOLS[i];
    }

    private GeneratorProfileBuilder.FormData collectForm() {
        GeneratorProfileBuilder.FormData f = new GeneratorProfileBuilder.FormData();
        ProtoChoice choice = currentChoice();
        f.protocol = choice.protocol;
        f.sslStyle = choice.sslStyle;
        f.name = text(R.id.cc_gen_name);
        f.flag = text(R.id.cc_gen_flag);
        f.host = text(R.id.cc_gen_host);
        f.port = num(R.id.cc_gen_port);
        f.sslPort = num(R.id.cc_gen_ssl_port);
        f.proxyHost = text(R.id.cc_gen_proxy_host);
        f.proxyPort = num(R.id.cc_gen_proxy_port);
        f.info = text(R.id.cc_gen_info);
        f.username = text(R.id.cc_gen_user);
        f.password = text(R.id.cc_gen_pass);
        f.uuid = text(R.id.cc_gen_uuid);
        f.alterId = num(R.id.cc_gen_alterid);
        f.path = text(R.id.cc_gen_path);
        f.serviceName = text(R.id.cc_gen_service);
        f.sni = text(R.id.cc_gen_sni);
        int mi = ssMethodSpinner.getSelectedItemPosition();
        f.method = mi >= 0 && mi < SS_METHODS.length ? SS_METHODS[mi] : "aes-256-gcm";
        f.hyObfs = text(R.id.cc_gen_hy_obfs);
        f.hyObfsPassword = text(R.id.cc_gen_hy_obfs_pass);
        f.wgPrivateKey = text(R.id.cc_gen_wg_priv);
        f.wgAddress = text(R.id.cc_gen_wg_addr);
        f.wgPublicKey = text(R.id.cc_gen_wg_pub);
        f.wgPresharedKey = text(R.id.cc_gen_wg_pre);
        f.wgAllowedIPs = text(R.id.cc_gen_wg_allowed);
        f.wgDns = text(R.id.cc_gen_wg_dns);
        f.wgMtu = num(R.id.cc_gen_wg_mtu);
        f.ovpnConfig = text(R.id.cc_gen_ovpn);
        f.dnsNsDomain = text(R.id.cc_gen_ns);
        f.dnsPubKey = text(R.id.cc_gen_pubkey);
        f.psk = text(R.id.cc_gen_psk);
        f.networkCode = text(R.id.cc_gen_net_code);
        f.payloadName = text(R.id.cc_gen_payload_name);
        f.payloadDns = text(R.id.cc_gen_pl_dns);
        f.payloadInfo = text(R.id.cc_gen_pl_info);
        f.useSsl = sw(R.id.cc_gen_tgl_ssl);
        f.sslPayload = sw(R.id.cc_gen_tgl_ssl_payload);
        f.sslRp = sw(R.id.cc_gen_tgl_ssl_rp);
        f.useCustomProxy = sw(R.id.cc_gen_tgl_proxy);
        f.enableSlowDns = sw(R.id.cc_gen_tgl_slowdns);
        f.enableUdp = sw(R.id.cc_gen_tgl_udp);
        f.reverseProxy = text(R.id.cc_gen_rev_proxy);
        // payload (raw override wins inside PayloadGenerator)
        boolean sshFamily = f.protocol == Protocol.SSH_DIRECT || f.protocol == Protocol.SSH_WS
            || f.protocol == Protocol.SLOWDNS_SSH;
        f.payload = sshFamily ? buildPayloadString() : "";
        // custom proxy override from the payload section
        if (f.useCustomProxy) {
            String ip = text(R.id.cc_gen_pl_proxy_ip);
            int pp = num(R.id.cc_gen_pl_proxy_port);
            if (!ip.isEmpty()) f.proxyHost = ip;
            if (pp > 0) f.proxyPort = pp;
        }
        // SNI for the payload/SSL comes from the payload SNI field when the xray SNI is empty
        if (f.sni.isEmpty()) f.sni = text(R.id.cc_gen_pl_sni);
        return f;
    }

    private void fillForm(GeneratorProfileBuilder.FormData f) {
        // protocol spinner (match protocol + sslStyle)
        int idx = 0;
        for (int i = 0; i < PROTOCOLS.length; i++) {
            if (PROTOCOLS[i].protocol == f.protocol && PROTOCOLS[i].sslStyle == f.sslStyle) { idx = i; break; }
            if (PROTOCOLS[i].protocol == f.protocol && !PROTOCOLS[i].sslStyle) idx = i;
        }
        protocolSpinner.setSelection(idx);
        set(R.id.cc_gen_name, f.name);
        set(R.id.cc_gen_flag, f.flag);
        set(R.id.cc_gen_host, f.host);
        set(R.id.cc_gen_port, f.port > 0 ? Integer.toString(f.port) : "");
        set(R.id.cc_gen_ssl_port, f.sslPort > 0 ? Integer.toString(f.sslPort) : "");
        set(R.id.cc_gen_proxy_host, f.proxyHost);
        set(R.id.cc_gen_proxy_port, f.proxyPort > 0 ? Integer.toString(f.proxyPort) : "");
        set(R.id.cc_gen_info, f.info);
        set(R.id.cc_gen_user, f.username);
        set(R.id.cc_gen_pass, f.password);
        set(R.id.cc_gen_uuid, f.uuid);
        set(R.id.cc_gen_alterid, f.alterId > 0 ? Integer.toString(f.alterId) : "");
        set(R.id.cc_gen_path, f.path);
        set(R.id.cc_gen_service, f.serviceName);
        set(R.id.cc_gen_sni, f.sni);
        for (int i = 0; i < SS_METHODS.length; i++) {
            if (SS_METHODS[i].equals(f.method)) { ssMethodSpinner.setSelection(i); break; }
        }
        set(R.id.cc_gen_hy_obfs, f.hyObfs);
        set(R.id.cc_gen_hy_obfs_pass, f.hyObfsPassword);
        set(R.id.cc_gen_wg_priv, f.wgPrivateKey);
        set(R.id.cc_gen_wg_addr, f.wgAddress);
        set(R.id.cc_gen_wg_pub, f.wgPublicKey);
        set(R.id.cc_gen_wg_pre, f.wgPresharedKey);
        set(R.id.cc_gen_wg_allowed, f.wgAllowedIPs);
        set(R.id.cc_gen_wg_dns, f.wgDns);
        set(R.id.cc_gen_wg_mtu, f.wgMtu > 0 ? Integer.toString(f.wgMtu) : "");
        set(R.id.cc_gen_ovpn, f.ovpnConfig);
        set(R.id.cc_gen_ns, f.dnsNsDomain);
        set(R.id.cc_gen_pubkey, f.dnsPubKey);
        set(R.id.cc_gen_psk, f.psk);
        set(R.id.cc_gen_net_code, f.networkCode);
        set(R.id.cc_gen_payload_name, f.payloadName);
        set(R.id.cc_gen_pl_dns, f.payloadDns);
        set(R.id.cc_gen_pl_info, f.payloadInfo);
        set(R.id.cc_gen_pl_sni, f.sni);
        set(R.id.cc_gen_raw, f.payload);
        set(R.id.cc_gen_rev_proxy, f.reverseProxy);
        setSw(R.id.cc_gen_tgl_ssl, f.useSsl);
        setSw(R.id.cc_gen_tgl_ssl_payload, f.sslPayload);
        setSw(R.id.cc_gen_tgl_ssl_rp, f.sslRp);
        setSw(R.id.cc_gen_tgl_proxy, f.useCustomProxy);
        setSw(R.id.cc_gen_tgl_slowdns, f.enableSlowDns);
        setSw(R.id.cc_gen_tgl_udp, f.enableUdp);
        payloadOut.setText(f.payload);
        updateGroups();
    }

    private void clearForm() {
        int[] fields = {
            R.id.cc_gen_name, R.id.cc_gen_flag, R.id.cc_gen_host, R.id.cc_gen_port,
            R.id.cc_gen_ssl_port, R.id.cc_gen_proxy_host, R.id.cc_gen_proxy_port,
            R.id.cc_gen_info, R.id.cc_gen_user, R.id.cc_gen_pass, R.id.cc_gen_uuid,
            R.id.cc_gen_alterid, R.id.cc_gen_path, R.id.cc_gen_service, R.id.cc_gen_sni,
            R.id.cc_gen_hy_obfs, R.id.cc_gen_hy_obfs_pass, R.id.cc_gen_wg_priv,
            R.id.cc_gen_wg_addr, R.id.cc_gen_wg_pub, R.id.cc_gen_wg_pre,
            R.id.cc_gen_wg_allowed, R.id.cc_gen_wg_dns, R.id.cc_gen_wg_mtu,
            R.id.cc_gen_wg_conf, R.id.cc_gen_ovpn, R.id.cc_gen_ns, R.id.cc_gen_pubkey,
            R.id.cc_gen_psk, R.id.cc_gen_net_code, R.id.cc_gen_payload_name,
            R.id.cc_gen_pl_sni, R.id.cc_gen_pl_dns, R.id.cc_gen_pl_proxy_ip,
            R.id.cc_gen_pl_proxy_port, R.id.cc_gen_pl_info, R.id.cc_gen_online_host,
            R.id.cc_gen_front_q, R.id.cc_gen_back_q, R.id.cc_gen_rev_proxy,
            R.id.cc_gen_ua, R.id.cc_gen_fwd_host, R.id.cc_gen_referer,
            R.id.cc_gen_rotate_list, R.id.cc_gen_extra_header, R.id.cc_gen_raw
        };
        for (int id : fields) set(id, "");
        set(R.id.cc_gen_req_method, "GET");
        setSw(R.id.cc_gen_tgl_ssl, false);
        setSw(R.id.cc_gen_tgl_ssl_payload, false);
        setSw(R.id.cc_gen_tgl_ssl_rp, false);
        setSw(R.id.cc_gen_tgl_proxy, false);
        setSw(R.id.cc_gen_tgl_slowdns, false);
        setSw(R.id.cc_gen_tgl_udp, false);
        ((RadioGroup) findViewById(R.id.cc_gen_method)).check(R.id.cc_gen_m_normal);
        payloadOut.setText("");
        updateGroups();
    }

    private void updateGroups() {
        Protocol p = currentChoice().protocol;
        boolean sslStyle = currentChoice().sslStyle;
        show(R.id.cc_grp_uuid, p == Protocol.VLESS_WS || p == Protocol.VLESS_GRPC
            || p == Protocol.VLESS_XHTTP || p == Protocol.VLESS_TCP
            || p == Protocol.VMESS_WS || p == Protocol.VMESS_TCP);
        show(R.id.cc_grp_ss, p == Protocol.SHADOWSOCKS);
        show(R.id.cc_grp_hy, p == Protocol.HYSTERIA2);
        show(R.id.cc_grp_wg, p == Protocol.WIREGUARD);
        show(R.id.cc_grp_ovpn, p == Protocol.OPENVPN_UDP || p == Protocol.OPENVPN_TCP);
        show(R.id.cc_grp_slowdns, p == Protocol.SLOWDNS_SSH || sw(R.id.cc_gen_tgl_slowdns));
        show(R.id.cc_grp_psk, p == Protocol.UDP_CUSTOM || p == Protocol.L2TP_IPSEC);
        show(R.id.cc_grp_payload, p == Protocol.SSH_DIRECT || p == Protocol.SSH_WS
            || p == Protocol.SLOWDNS_SSH || sslStyle);
    }

    // ---------------- small helpers ----------------

    private void show(int id, boolean vis) {
        View v = findViewById(id);
        if (v != null) v.setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    private String text(int id) {
        EditText e = findViewById(id);
        return e == null ? "" : e.getText().toString().trim();
    }

    private void set(int id, String v) {
        EditText e = findViewById(id);
        if (e != null) e.setText(v == null ? "" : v);
    }

    private int num(int id) {
        try { return Integer.parseInt(text(id)); } catch (Exception e) { return 0; }
    }

    private boolean sw(int id) {
        Switch s = findViewById(id);
        return s != null && s.isChecked();
    }

    private void setSw(int id, boolean on) {
        Switch s = findViewById(id);
        if (s != null) s.setChecked(on);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    /** Bottom-center branded toast (her UI rule). */
    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(28));
        t.show();
    }
}
