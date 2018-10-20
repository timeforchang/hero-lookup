package com.starkmedia.starkmedia;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Camera camera;
    private CameraPreview cameraPreview;
    private Context myContext;
    private LinearLayout cameraPreviewContainer;
    private TextView byteString;
    public int cameraWidth;
    public int cameraHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myContext = this;

        synchronizedOpenCamera();

        camera.setDisplayOrientation(90);

        //set camera to continually auto-focus
        Camera.Parameters params = camera.getParameters();
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        camera.setParameters(params);

        byteString = findViewById(R.id.byteString);
        cameraPreviewContainer = findViewById(R.id.cPreview);
        cameraPreview = new CameraPreview(myContext, camera);
        cameraHeight = cameraPreview.getHeight();
        cameraWidth = cameraPreview.getWidth();
        cameraPreviewContainer.addView(cameraPreview);


        camera.startPreview();
    }

    private void safeOpenCamera() {
        try {
            releaseCamera();
            camera = Camera.open();
        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
        }
    }

    private void synchronizedOpenCamera() {
        if (cameraHandlerThread == null) {
            cameraHandlerThread = new CameraHandlerThread();
        }

        synchronized (cameraHandlerThread) {
            cameraHandlerThread.openCamera();
        }
    }

    private CameraHandlerThread cameraHandlerThread = null;
    private class CameraHandlerThread extends HandlerThread {
        Handler mHandler;

        CameraHandlerThread() {
            super("CameraHandlerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    safeOpenCamera();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            }
            catch (InterruptedException e) {
                Log.e(getString(R.string.app_name), "wait was interrupted");
            }
        }
    }

    public void onResume() {

        super.onResume();
        if(camera == null) {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            cameraPreview.refreshCamera(camera);
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

    int i = 0;
    final int SECOND_LAG = 10;
    public void setImageBytes(final byte[] data) {
        MainActivity.this.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (i > SECOND_LAG * 30) {
                    try {
                        // Instantiates a client
                        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                            Bitmap bitmap = Bitmap.createBitmap(cameraWidth, cameraHeight,
                                    Bitmap.Config.ARGB_8888);
                            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data));

                            bitmap = scaleBitmapDown(bitmap, 1200);

                            // Convert the bitmap to a JPEG
                            // Just in case it's a format that Android understands but Cloud Vision
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                            byte[] imageBytes = byteArrayOutputStream.toByteArray();


                            ByteString img = null;
                            // Build the image
                            Image image = Image.newBuilder().setContent(img).build();

                            // Create the request with the image and the specified feature: web detection
                            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                                    .addFeatures(Feature.newBuilder().setType(Feature.Type.WEB_DETECTION))
                                    .setImage(image)
                                    .build();

                            // Perform the request
                            BatchAnnotateImagesResponse response = client.batchAnnotateImages(Arrays.asList(request));

                            // Display the results
                            List<AnnotateImageResponse> responses = response.getResponsesList();
                            for (AnnotateImageResponse res : responses) {
                                if (res.hasError()) {
                                    System.out.println("yikes");
                                } else {
                                    WebDetection annotation = res.getWebDetection();
                                    String description = annotation.getWebEntities(0).getDescription();
                                    System.out.println(description);
                                    byteString.setText(description);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    i = 0;
                } else {
                    i++;
                }
            }

            private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

                int originalWidth = bitmap.getWidth();
                int originalHeight = bitmap.getHeight();
                int resizedWidth = maxDimension;
                int resizedHeight = maxDimension;

                if (originalHeight > originalWidth) {
                    resizedHeight = maxDimension;
                    resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
                } else if (originalWidth > originalHeight) {
                    resizedWidth = maxDimension;
                    resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
                } else if (originalHeight == originalWidth) {
                    resizedHeight = maxDimension;
                    resizedWidth = maxDimension;
                }
                return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
            }
        });
    }
}
