package com.starkmedia.starkmedia;

import android.content.Context;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    private Camera camera;
    private CameraPreview mPreview;
    private Context myContext;
    private LinearLayout cameraPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myContext = this;

        try {
            releaseCamera();
            camera = Camera.open();
            camera.setDisplayOrientation(90);

            //set camera to continually auto-focus
            Camera.Parameters params = camera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(params);

        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
        }

        cameraPreview = findViewById(R.id.cPreview);
        mPreview = new CameraPreview(myContext, camera);
        cameraPreview.addView(mPreview);

        camera.startPreview();
    }

    public void onResume() {

        super.onResume();
        if(camera == null) {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            mPreview.refreshCamera(camera);
            Log.d("nu", "null");
        }else {
            Log.d("nu","no null");
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        //when on Pause, release camera in order to be used from other applications
        releaseCamera();
    }


    private void releaseCamera() {
        // stop and release camera
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }
}
