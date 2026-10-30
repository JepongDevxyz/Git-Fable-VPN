package jayar.lee.espiritu.activities;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import jayar.lee.espiritu.HarliesApplication;
import com.jepongdevxyz.fablevpn.R;

/**
 * Own-account user/pass screen, opened from the profile icon on Home
 * (top right) — her order 2026-10-31: "dito mo ilipat yung lagayan ng
 * userpass kung may own account sila dyan nila lalagay para maka connect".
 *
 * Writes the SAME keys the original Home fields used, in the SAME store
 * (HarliesApplication.getPrivateSharedPreferences()):
 *   _screenUsername_key / _screenPassword_key
 * OpenVPNClient's connect flow already reads those keys
 * (config.setUser / setUserPass), so accounts entered here are the ones
 * used to connect — only the placement moved off the Home body.
 */
public class AccountActivity extends Activity {

    private SharedPreferences prefs;
    private EditText userField, passField;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        prefs = HarliesApplication.getPrivateSharedPreferences();

        userField = findViewById(R.id.cc_acc_username);
        passField = findViewById(R.id.cc_acc_password);

        userField.setText(prefs.getString("_screenUsername_key", ""));
        passField.setText(prefs.getString("_screenPassword_key", ""));

        userField.addTextChangedListener(saver("_screenUsername_key"));
        passField.addTextChangedListener(saver("_screenPassword_key"));

        Switch show = findViewById(R.id.cc_acc_showpw);
        show.setOnCheckedChangeListener((b, checked) -> {
            int pos = passField.getSelectionStart();
            passField.setTransformationMethod(checked
                ? HideReturnsTransformationMethod.getInstance()
                : PasswordTransformationMethod.getInstance());
            passField.setSelection(pos < 0 ? passField.length() : pos);
        });

        findViewById(R.id.cc_acc_clear).setOnClickListener(v -> {
            userField.setText("");
            passField.setText("");
            brandedToast("Account cleared");
        });
        findViewById(R.id.cc_acc_back).setOnClickListener(v -> finish());
    }

    private TextWatcher saver(final String key) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                prefs.edit().putString(key, s.toString().trim()).apply();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private void brandedToast(String msg) {
        Toast t = Toast.makeText(this, msg + "\nPowered by Jepong Devxyz", Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 84);
        t.show();
    }
}
