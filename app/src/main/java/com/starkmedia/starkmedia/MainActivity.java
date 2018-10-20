package com.starkmedia.starkmedia;

import android.content.Context;
import android.graphics.*;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
//import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.WebDetection;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;

import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String CLOUD_VISION_API_KEY = BuildConfig.API_KEY;
    private static final String MARVEL_API_KEY = BuildConfig.MARVEL_API_KEY;

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
        cameraHeight = camera.getParameters().getPreviewSize().height;
        cameraWidth = camera.getParameters().getPreviewSize().width;

        byteString = findViewById(R.id.byteString);
        cameraPreviewContainer = findViewById(R.id.cPreview);
        cameraPreview = new CameraPreview(myContext, camera);
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
                    byteString.setText("hello there");
                    try {
                        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                                .permitAll().build();
                        StrictMode.setThreadPolicy(policy);

                        Vision.Builder visionBuilder = new Vision.Builder(
                                new NetHttpTransport(),
                                new AndroidJsonFactory(),
                                null);

                        visionBuilder.setVisionRequestInitializer(
                                new VisionRequestInitializer(CLOUD_VISION_API_KEY));

                        Vision vision = visionBuilder.build();

                        YuvImage im = new YuvImage(data, ImageFormat.NV21, cameraWidth, cameraHeight, null);
                        int quality = 70;
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        Rect rect = new Rect(0, 0, cameraWidth, cameraHeight);
                        im.compressToJpeg(rect, quality, out);
                        byte[] imageBytes = out.toByteArray();


                        /*BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                        bitmapOptions.inSampleSize = 10;

                        Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(out.toByteArray()), null, bitmapOptions);

                        bitmap = scaleBitmapDown(bitmap, 1200);

                        // Convert the bitmap to a JPEG
                        // Just in case it's a format that Android understands but Cloud Vision
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();*/
                        // Build the image
                        Image image = new Image();

                        image.encodeContent(imageBytes);


                        // Create the request with the image and the specified feature: web detection
                        AnnotateImageRequest request = new AnnotateImageRequest();
                        request.setImage(image);
                        request.setFeatures(new ArrayList<Feature>() {{
                            Feature labelDetection = new Feature();
                            labelDetection.setType("WEB_DETECTION");
                            labelDetection.setMaxResults(10);
                            add(labelDetection);
                        }});

                        BatchAnnotateImagesRequest batchRequest =
                                new BatchAnnotateImagesRequest();

                        batchRequest.setRequests(Arrays.asList(request));

                        com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse batchResponse =
                                vision.images().annotate(batchRequest).execute();

                        // Display the results
                        List<com.google.api.services.vision.v1.model.AnnotateImageResponse> responses = batchResponse.getResponses();
                        for (com.google.api.services.vision.v1.model.AnnotateImageResponse res : responses) {
                            if (res.getError() != null) {
                                System.out.println("yikes");
                            } else {
                                WebDetection annotation = res.getWebDetection();
                                String description = annotation.getWebEntities().get(0).getDescription();
                                System.out.println(description);
                                byteString.setText(description);
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
