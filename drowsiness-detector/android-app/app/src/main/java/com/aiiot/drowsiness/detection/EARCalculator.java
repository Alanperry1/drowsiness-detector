package com.aiiot.drowsiness.detection;

import org.opencv.core.Point;

/**
 * Computes the Eye Aspect Ratio (EAR) and tracks consecutive closed-eye frames
 * to determine drowsiness state.
 *
 * Formula (Soukupova & Cech, 2016):
 *   EAR = (||p1-p5|| + ||p2-p4||) / (2 * ||p0-p3||)
 */
public class EARCalculator {

    public static final double EAR_THRESHOLD = 0.25;
    public static final int    CONSEC_FRAMES = 3;

    private int     closedFrameCount = 0;
    private boolean drowsy           = false;

    /**
     * Computes EAR for a single eye given its 6 landmark points.
     */
    public double computeEAR(Point[] eye) {
        double vertical1   = euclidean(eye[1], eye[5]);
        double vertical2   = euclidean(eye[2], eye[4]);
        double horizontal  = euclidean(eye[0], eye[3]);
        if (horizontal < 1e-6) return 0.0;
        return (vertical1 + vertical2) / (2.0 * horizontal);
    }

    /**
     * Updates the drowsiness state machine.
     *
     * @return true on the frame when drowsiness is first detected
     */
    public boolean update(Point[] leftEye, Point[] rightEye) {
        double avgEAR = (computeEAR(leftEye) + computeEAR(rightEye)) / 2.0;

        if (avgEAR < EAR_THRESHOLD) {
            closedFrameCount++;
            if (closedFrameCount >= CONSEC_FRAMES && !drowsy) {
                drowsy = true;
                return true;
            }
        } else {
            closedFrameCount = 0;
            drowsy = false;
        }
        return false;
    }

    public boolean isDrowsy()            { return drowsy; }
    public int     getClosedFrameCount() { return closedFrameCount; }

    public void reset() {
        closedFrameCount = 0;
        drowsy = false;
    }

    private static double euclidean(Point a, Point b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
