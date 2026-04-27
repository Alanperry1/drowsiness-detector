package com.aiiot.drowsiness.detection;

import android.content.Context;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Wraps OpenCV's Haar cascade classifier for frontal face detection.
 * The cascade XML is loaded from app assets at runtime.
 */
public class FaceDetector {

    private static final double SCALE_FACTOR  = 1.1;
    private static final int    MIN_NEIGHBORS = 4;
    private static final Size   MIN_SIZE      = new Size(80, 80);

    private final CascadeClassifier classifier;

    public FaceDetector(Context context) throws IOException {
        classifier = loadCascade(context, "haarcascade_frontalface_alt2.xml", "face");
        if (classifier.empty()) {
            throw new IOException("Face cascade failed to load.");
        }
    }

    /**
     * Detects faces in a grayscale, histogram-equalised Mat.
     *
     * @param grayFrame preprocessed grayscale frame
     * @return list of detected face rectangles, sorted largest-first
     */
    public List<Rect> detect(Mat grayFrame) {
        MatOfRect output = new MatOfRect();
        classifier.detectMultiScale(
            grayFrame,
            output,
            SCALE_FACTOR,
            MIN_NEIGHBORS,
            0,
            MIN_SIZE,
            new Size()
        );
        List<Rect> faces = output.toList();
        // Sort by area descending so faces.get(0) is always the largest (closest) face
        faces.sort((a, b) -> Integer.compare(b.width * b.height, a.width * a.height));
        return faces;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public static CascadeClassifier loadCascade(Context ctx, String assetName,
                                                 String tag) throws IOException {
        InputStream is = ctx.getAssets().open(assetName);
        File dir  = ctx.getDir(tag + "_cascade", Context.MODE_PRIVATE);
        File file = new File(dir, assetName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
        }
        is.close();
        return new CascadeClassifier(file.getAbsolutePath());
    }
}
