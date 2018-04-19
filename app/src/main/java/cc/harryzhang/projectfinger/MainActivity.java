package cc.harryzhang.projectfinger;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void openCamera(View view) {
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }

    public void openImageView(View view) {
        Intent intent = new Intent(this, ViewImageActivity.class);
        startActivity(intent);
    }

    public void openPassword(View view) {
        Intent intent = new Intent(this, PasswordActivity.class);
        startActivity(intent);
    }
}
