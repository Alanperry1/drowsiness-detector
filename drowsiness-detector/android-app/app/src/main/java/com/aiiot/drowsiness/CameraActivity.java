package com.aiiot.drowsiness;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.aiiot.drowsiness.alert.AlertManager;
import com.aiiot.drowsiness.detection.EARCalculator;
import com.aiiot.drowsiness.detection.EyeLandmarkExtractor;
import com.aiiot.drowsiness.detection.FaceDetector;
import com.aiiot.drowsiness.sensor.HeadPoseEstimator;
import com.aiiot.drowsiness.ui.OverlayView;
import com.aiiot.drowsiness.utils.MatUtils;
import com.aiiot.drowsiness.utils.SessionTracker;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity
        implements ImageAnalysis.Analyzer {

    private static final String TAG = "CameraActivity";

    public enum ProcessingMode { DROWSINESS, EDGES, MOTION }
    private ProcessingMode currentMode = ProcessingMode.DROWSINESS;

    private FaceDetector        faceDetector;
    private EyeLandmarkExtractor landmarkExtractor;
    private EARCalculator        earCalculator;
    private HeadPoseEstimator    headPoseEstimator;
    private AlertManager         alertManager;
    private SessionTracker       sessionTracker;
    private OverlayView          overlayView;
    private ExecutorService      cameraExecutor;

    private TextView tvMode, tvEAR, tvFPS, tvAlerts;
    private Button   btnMode, btnDashboard;

    private long lastFrameTime = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        overlayView   = findViewById(R.id.overlayView);
        tvMode        = findViewById(R.id.tvMode);
        tvEAR         = findViewById(R.id.tvEAR);
        tvFPS         = findViewById(R.id.tvFPS);
        tvAlerts      = findViewById(R.id.tvAlerts);
        btnMode       = findViewById(R.id.btnMode);
        btnDashboard  = findViewById(R.id.btnDashboard);

        alertManager  = new AlertManager(this);
        sessionTracker = new SessionTracker();
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            faceDetector      = new FaceDetector(this);
            landmarkExtractor = new EyeLandmarkExtractor(this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load cascade classifiers", e);
            Toast.makeText(this, "Failed to load face detector.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        earCalculator    = new EARCalculator();
        headPoseEstimator = new HeadPoseEstimator(this);
        headPoseEstimator.start();

        btnMode.setOnClickListener(v -> cycleMode());
        btnDashboard.setOnClickListener(v ->
            startActivity(new Intent(this, com.aiiot.drowsiness.ui.DashboardActivity.class)));

        startCamera();
    }

    private void cycleMode() {
        switch (currentMode) {
            case DROWSINESS: currentMode = ProcessingMode.EDGES;   break;
            case EDGES:      currentMode = ProcessingMode.MOTION;  break;
            case MOTION:     currentMode = ProcessingMode.DROWSINESS; break;
        }
        runOnUiThread(() -> tvMode.setText(currentMode.name()));
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
            ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                CameraSelector selector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                analysis.setAnalyzer(cameraExecutor, this);

                provider.unbindAll();
                provider.bindToLifecycle(this, selector, analysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        long now = System.currentTimeMillis();
        double fps = 1000.0 / Math.max(1, now - lastFrameTime);
        lastFrameTime = now;

        Mat frame = MatUtils.imageProxyToMat(image);

        switch (currentMode) {
            case DROWSINESS: processDrowsiness(frame, fps); break;
            case EDGES:      processEdges(frame, fps);      break;
            case MOTION:     processMotion(frame, fps);     break;
        }

        frame.release();
        image.close();
    }

    private void processDrowsiness(Mat frame, double fps) {
        Mat gray = MatUtils.toGrayEqualized(frame);
        List<Rect> faces = faceDetector.detect(gray);

        if (faces.isEmpty()) {
            alertManager.dismiss();
            overlayView.clear();
            gray.release();
            updateHUD("--", fps, sessionTracker.getAlertCount());
            return;
        }

        org.opencv.core.Point[][] eyes =
            landmarkExtractor.extractEyeLandmarks(gray, faces.get(0));

        if (eyes != null) {
            double ear = (earCalculator.computeEAR(eyes[0])
                        + earCalculator.computeEAR(eyes[1])) / 2.0;
            boolean drowsy = earCalculator.update(eyes[0], eyes[1]);
            float   pitch  = headPoseEstimator.getPitch();

            if (drowsy || pitch > HeadPoseEstimator.PITCH_THRESHOLD) {
                alertManager.trigger();
                sessionTracker.recordAlert();
            } else if (!earCalculator.isDrowsy()) {
                alertManager.dismiss();
            }

            overlayView.drawDrowsinessOverlay(frame, faces.get(0), eyes, ear,
                earCalculator.isDrowsy(), pitch);
            updateHUD(String.format("%.2f", ear), fps, sessionTracker.getAlertCount());
        }

        gray.release();
    }

    private void processEdges(Mat frame, double fps) {
        Mat edges = MatUtils.computeEdges(frame);
        overlayView.drawMat(edges);
        edges.release();
        updateHUD("EDGES", fps, sessionTracker.getAlertCount());
    }

    private void processMotion(Mat frame, double fps) {
        Mat flow = MatUtils.computeMotion(frame);
        overlayView.drawMat(flow);
        flow.release();
        updateHUD("MOTION", fps, sessionTracker.getAlertCount());
    }

    private void updateHUD(String earStr, double fps, int alerts) {
        runOnUiThread(() -> {
            tvEAR.setText("EAR: " + earStr);
            tvFPS.setText(String.format("FPS: %.0f", fps));
            tvAlerts.setText("Alerts: " + alerts);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alertManager.release();
        headPoseEstimator.stop();
        cameraExecutor.shutdown();
        sessionTracker.end();
    }
}
