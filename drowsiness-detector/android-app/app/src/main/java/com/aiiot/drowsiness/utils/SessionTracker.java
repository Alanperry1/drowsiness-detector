package com.aiiot.drowsiness.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks per-session statistics: alert count, session duration, EAR history.
 * Thread-safe via volatile / synchronized methods.
 */
public class SessionTracker {

    private final long        startTimeMs;
    private volatile int      alertCount   = 0;
    private final List<Double> earHistory  = new ArrayList<>();
    private final List<Long>   alertTimes  = new ArrayList<>();
    private volatile long      endTimeMs   = -1;

    public SessionTracker() {
        startTimeMs = System.currentTimeMillis();
    }

    public synchronized void recordAlert() {
        alertCount++;
        alertTimes.add(System.currentTimeMillis() - startTimeMs);
    }

    public synchronized void recordEAR(double ear) {
        earHistory.add(ear);
    }

    public void end() {
        endTimeMs = System.currentTimeMillis();
    }

    public int     getAlertCount()    { return alertCount; }
    public long    getSessionMs()     {
        return (endTimeMs > 0 ? endTimeMs : System.currentTimeMillis()) - startTimeMs;
    }

    public synchronized double getAverageEAR() {
        return earHistory.stream().mapToDouble(Double::doubleValue)
            .average().orElse(0.0);
    }

    public synchronized List<Long> getAlertTimes() {
        return new ArrayList<>(alertTimes);
    }
}
