package com.aiiot.drowsiness.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.aiiot.drowsiness.R;

/**
 * Session statistics screen.
 * In a full implementation this would receive a SessionTracker parcel
 * from CameraActivity. Shown here as a standalone stub.
 */
public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        int    alerts      = getIntent().getIntExtra("alerts", 0);
        long   sessionMs   = getIntent().getLongExtra("sessionMs", 0);
        double avgEAR      = getIntent().getDoubleExtra("avgEAR", 0.0);
        float  avgFPS      = getIntent().getFloatExtra("avgFPS", 0f);

        long seconds = sessionMs / 1000;
        long minutes = seconds  / 60;
        seconds      = seconds  % 60;

        ((TextView) findViewById(R.id.tvSessionTime))
            .setText(String.format("Session: %02d:%02d", minutes, seconds));
        ((TextView) findViewById(R.id.tvAlertCount))
            .setText("Alerts triggered: " + alerts);
        ((TextView) findViewById(R.id.tvAvgEAR))
            .setText(String.format("Average EAR: %.3f", avgEAR));
        ((TextView) findViewById(R.id.tvAvgFPS))
            .setText(String.format("Average FPS: %.1f", avgFPS));
    }
}
