package com.aiiot.drowsiness.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Reads the gyroscope and integrates angular velocity into a pitch angle
 * using a complementary filter with EMA noise smoothing.
 *
 * Complementary filter:
 *   pitch(t) = alpha * (pitch(t-1) + omega * dt)
 *   where omega is EMA-smoothed gyroscope reading
 */
public class HeadPoseEstimator implements SensorEventListener {

    public static final float PITCH_THRESHOLD = 20.0f; // degrees

    private static final float ALPHA_FILTER = 0.96f;   // complementary
    private static final float ALPHA_EMA    = 0.10f;   // EMA noise reduction

    private final SensorManager sensorManager;
    private       Sensor        gyroscope;
    private       Sensor        accelerometer;

    private volatile float pitchAngle  = 0.0f;
    private          float smoothOmega = 0.0f;
    private          long  lastTimestamp = 0;

    // Accelerometer-based gravity pitch for baseline calibration
    private volatile float gravityPitch = 0.0f;

    public HeadPoseEstimator(Context context) {
        sensorManager = (SensorManager) context
            .getSystemService(Context.SENSOR_SERVICE);
    }

    public void start() {
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope,
                SensorManager.SENSOR_DELAY_GAME);
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            handleGyroscope(event);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometer(event);
        }
    }

    private void handleGyroscope(SensorEvent event) {
        if (lastTimestamp == 0) {
            lastTimestamp = event.timestamp;
            return;
        }

        float rawOmega = event.values[0];       // pitch axis (rad/s)
        float dt = (event.timestamp - lastTimestamp) * 1e-9f;
        lastTimestamp = event.timestamp;

        // EMA smoothing to suppress high-frequency noise
        smoothOmega = ALPHA_EMA * rawOmega + (1.0f - ALPHA_EMA) * smoothOmega;

        // Complementary filter integration
        pitchAngle = ALPHA_FILTER * (pitchAngle + smoothOmega * dt);
    }

    private void handleAccelerometer(SensorEvent event) {
        // Use gravity to compute steady-state pitch baseline
        float ax = event.values[0];
        float ay = event.values[1];
        float az = event.values[2];
        gravityPitch = (float) Math.toDegrees(Math.atan2(ax,
            Math.sqrt(ay * ay + az * az)));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* unused */ }

    /** Returns current head pitch in degrees (positive = head drooping forward). */
    public float getPitch() {
        return pitchAngle;
    }

    /** Returns gravity-corrected pitch baseline from accelerometer. */
    public float getGravityPitch() {
        return gravityPitch;
    }

    public boolean isHeadDropping() {
        return pitchAngle > PITCH_THRESHOLD;
    }
}
