package com.aiiot.drowsiness;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DrowsinessApp";
    private static final int REQUEST_CAMERA = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV init failed");
            Toast.makeText(this, "OpenCV failed to load. Check SDK setup.",
                Toast.LENGTH_LONG).show();
            return;
        }
        Log.i(TAG, "OpenCV " + org.opencv.core.Core.VERSION + " loaded");

        if (hasCameraPermission()) {
            launchCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void launchCamera() {
        startActivity(new Intent(this, CameraActivity.class));
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
        }
    }
}
