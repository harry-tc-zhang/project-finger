package cc.harryzhang.projectfinger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import static android.content.ContentValues.TAG;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

/**
 * Created by ztc on 1/26/18.
 * References:
 * Working camera using Android API: https://developer.android.com/guide/topics/media/camera.html
 */

public class CameraActivity extends Activity{
    private Camera mCamera;
    private CameraPreview mPreview;
    private int cameraID;
    private Camera.CameraInfo mCameraInfo;

    private String mTaskProgress;

    private CVOps mCVOps;

    private String password;
    private String enteredPassword;
    private String passwordProgress;

    private Button captureButton;

    /** A safe way to get an instance of the Camera object. */
    public Camera getCameraInstance(){
        Camera c = null;
        try {
            int numCameras = Camera.getNumberOfCameras();
            for(int i = 0; i < numCameras; i ++) {
                Camera.CameraInfo camInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(i, camInfo);
                if(camInfo.orientation == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraID = i;
                    break;
                }
            }
            c = Camera.open(cameraID); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Create an instance of Camera
        mCamera = getCameraInstance();

        mCameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraID, mCameraInfo);
        mCamera.setDisplayOrientation(90);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_view);
        preview.addView(mPreview);

        captureButton = (Button) findViewById(R.id.btn_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // get an image from the camera
                        mCamera.takePicture(null, null, mPicture);
                    }
                }
        );

        mCVOps = new CVOps(getApplicationContext(), false);

        SharedPreferences prefs = getSharedPreferences(getString(R.string.prefs_file), MODE_PRIVATE);
        password = prefs.getString(getString(R.string.prefs_key_password), "05.");
        Toast.makeText(getApplicationContext(), password, Toast.LENGTH_SHORT).show();

        enteredPassword = "";

        passwordProgress = "_";

        captureButton.setText("Enter: " + passwordProgress);
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions: ");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

            HashMap<String, String> analysisResult = mCVOps.runPipeline(pictureFile.getAbsolutePath());
            if(analysisResult != null) {
                enteredPassword += analysisResult.get("value");
                if(analysisResult.get("position").equals("corner")) {
                    enteredPassword += ".";
                } else if(analysisResult.get("position").equals("edge")) {
                    enteredPassword += "/";
                }
                String checkPassword = password.substring(0, enteredPassword.length());
                Toast.makeText(getApplicationContext(), enteredPassword + ", " + checkPassword, Toast.LENGTH_SHORT).show();
                if(enteredPassword.equals(checkPassword)) {
                    if(checkPassword.equals(password)) {
                        Toast.makeText(getApplicationContext(), "UNLOCKED!", Toast.LENGTH_LONG).show();
                        CameraActivity.this.finish();
                    } else {
                        passwordProgress = "*" + passwordProgress;
                        Toast.makeText(getApplicationContext(), passwordProgress, Toast.LENGTH_SHORT).show();
                        CameraActivity.this.captureButton.setText("Enter: " + passwordProgress);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), "Wrong password!", Toast.LENGTH_SHORT).show();
                    enteredPassword = "";
                    passwordProgress = "_";
                    CameraActivity.this.captureButton.setText("Enter: " + passwordProgress);
                }
            }


            mCamera.startPreview();
        }
    };

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    /** Create a file Uri for saving an image or video */
    private Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), getResources().getString(R.string.folder_name));
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }
}
