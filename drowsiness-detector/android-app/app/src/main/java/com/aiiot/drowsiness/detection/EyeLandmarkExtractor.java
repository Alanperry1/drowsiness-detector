package com.aiiot.drowsiness.detection;

import android.content.Context;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Detects eye regions within a face ROI and generates 6-point
 * landmarks per eye using proportional geometry.
 *
 * Landmark layout (p0..p5):
 *   p0 = inner corner   p3 = outer corner
 *   p1 = upper-left     p2 = upper-right
 *   p5 = lower-left     p4 = lower-right
 */
public class EyeLandmarkExtractor {

    private static final Size MIN_EYE = new Size(20, 20);

    // Proportional positions inside the detected eye bounding box
    private static final double[][] TEMPLATE = {
        {0.00, 0.50},   // p0 inner corner
        {0.33, 0.15},   // p1 upper-left
        {0.67, 0.15},   // p2 upper-right
        {1.00, 0.50},   // p3 outer corner
        {0.67, 0.85},   // p4 lower-right
        {0.33, 0.85}    // p5 lower-left
    };

    private final CascadeClassifier eyeCascade;

    public EyeLandmarkExtractor(Context context) throws IOException {
        eyeCascade = FaceDetector.loadCascade(context, "haarcascade_eye.xml", "eye");
        if (eyeCascade.empty()) {
            throw new IOException("Eye cascade failed to load.");
        }
    }

    /**
     * Returns [leftEye[6], rightEye[6]] landmark arrays, or null if fewer than
     * two eyes are detected.
     *
     * @param grayFrame equalised grayscale frame
     * @param faceRect  bounding box of the detected face
     */
    public Point[][] extractEyeLandmarks(Mat grayFrame, Rect faceRect) {
        // Restrict search to upper 55% of the face (avoids nose/mouth false positives)
        int eyeRegionHeight = (int)(faceRect.height * 0.55);
        Rect eyeSearchRegion = new Rect(
            faceRect.x, faceRect.y,
            faceRect.width, eyeRegionHeight
        );

        // Clamp to frame bounds
        eyeSearchRegion.x      = Math.max(0, eyeSearchRegion.x);
        eyeSearchRegion.y      = Math.max(0, eyeSearchRegion.y);
        eyeSearchRegion.width  = Math.min(grayFrame.cols() - eyeSearchRegion.x,
                                           eyeSearchRegion.width);
        eyeSearchRegion.height = Math.min(grayFrame.rows() - eyeSearchRegion.y,
                                           eyeSearchRegion.height);

        Mat faceROI = new Mat(grayFrame, eyeSearchRegion);
        MatOfRect eyesMatrix = new MatOfRect();
        eyeCascade.detectMultiScale(faceROI, eyesMatrix, 1.1, 5, 0,
            MIN_EYE, new Size());

        List<Rect> eyes = eyesMatrix.toList();
        faceROI.release();

        if (eyes.size() < 2) return null;

        // Sort by X position: left eye (smaller X) first
        eyes.sort(Comparator.comparingInt(r -> r.x));
        Rect leftRect  = eyes.get(0);
        Rect rightRect = eyes.get(eyes.size() - 1);

        return new Point[][]{
            buildLandmarks(leftRect,  faceRect),
            buildLandmarks(rightRect, faceRect)
        };
    }

    private Point[] buildLandmarks(Rect eyeRect, Rect faceRect) {
        Point[] pts = new Point[6];
        for (int i = 0; i < 6; i++) {
            pts[i] = new Point(
                faceRect.x + eyeRect.x + TEMPLATE[i][0] * eyeRect.width,
                faceRect.y + eyeRect.y + TEMPLATE[i][1] * eyeRect.height
            );
        }
        return pts;
    }
}
