package com.aiiot.drowsiness.alert;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import com.aiiot.drowsiness.R;

/**
 * Orchestrates all three alert channels: audio alarm, haptic vibration,
 * and visual state (managed by the calling activity via listener).
 *
 * Safe to call trigger() and dismiss() from any thread.
 */
public class AlertManager {

    public interface AlertListener {
        void onAlertStarted();
        void onAlertDismissed();
    }

    // Vibration pattern: [delay, buzz, pause, buzz] in ms
    private static final long[] VIB_PATTERN = {0L, 500L, 200L, 500L};

    private final Handler      mainHandler;
    private final MediaPlayer  alarmPlayer;
    private final Vibrator     vibrator;
    private       AlertListener listener;
    private volatile boolean   isAlerting = false;

    public AlertManager(Context context) {
        mainHandler = new Handler(Looper.getMainLooper());
        vibrator    = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        alarmPlayer = MediaPlayer.create(context, R.raw.alarm_beep);
        if (alarmPlayer != null) alarmPlayer.setLooping(true);
    }

    public void setListener(AlertListener listener) {
        this.listener = listener;
    }

    /** Triggers all alert channels. Idempotent -- safe to call every frame. */
    public void trigger() {
        if (isAlerting) return;
        isAlerting = true;

        mainHandler.post(() -> {
            if (alarmPlayer != null && !alarmPlayer.isPlaying()) {
                alarmPlayer.start();
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(VIB_PATTERN, 0));
                } else {
                    vibrator.vibrate(VIB_PATTERN, 0);
                }
            }

            if (listener != null) listener.onAlertStarted();
        });
    }

    /** Dismisses all alerts. */
    public void dismiss() {
        if (!isAlerting) return;
        isAlerting = false;

        mainHandler.post(() -> {
            if (alarmPlayer != null && alarmPlayer.isPlaying()) {
                alarmPlayer.pause();
                alarmPlayer.seekTo(0);
            }
            if (vibrator != null) vibrator.cancel();
            if (listener != null) listener.onAlertDismissed();
        });
    }

    /** Must be called in Activity.onDestroy() to release MediaPlayer resources. */
    public void release() {
        dismiss();
        if (alarmPlayer != null) {
            alarmPlayer.stop();
            alarmPlayer.release();
        }
    }

    public boolean isAlerting() {
        return isAlerting;
    }
}
