package cc.harryzhang.projectfinger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created by ztc on 2/13/18.
 */

public class PasswordActivity extends Activity {
    private String password;
    Button setButton;
    EditText passwordField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_password);

        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefs_file), MODE_PRIVATE);
        password = prefs.getString(getString(R.string.prefs_key_password), "05.");

        setButton = (Button) findViewById(R.id.btn_password);
        passwordField = (EditText) findViewById(R.id.text_password);

        passwordField.setText(password);

        setButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String newPassword = passwordField.getText().toString();
                        boolean pwValid = true;
                        for(int i = 0; i < newPassword.length(); i ++) {
                            int cASCII = (int) newPassword.charAt(i);
                            if(!((cASCII > 45) && (cASCII < 54))) {
                                pwValid = false;
                                Toast.makeText(getApplicationContext(), "Password is not valid", Toast.LENGTH_SHORT).show();
                                break;
                            }
                        }
                        if(pwValid) {
                            Toast.makeText(getApplicationContext(), "New password: " + newPassword, Toast.LENGTH_LONG).show();
                            password = newPassword;
                            SharedPreferences prefs = getSharedPreferences(getString(R.string.prefs_file), MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(getString(R.string.prefs_key_password), newPassword);
                            editor.commit();
                            PasswordActivity.this.finish();
                        }
                    }
                }
        );
    }
}
