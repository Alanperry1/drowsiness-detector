package com.aiiot.drowsiness.utils;

import androidx.camera.core.ImageProxy;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Static helpers for Mat conversion and common pipeline operations.
 */
public final class MatUtils {

    // Reused across frames to reduce GC pressure
    private static Mat prevGray = new Mat();
    private static MatOfPoint2f prevPts = new MatOfPoint2f();

    private MatUtils() {}

    // ── Conversion ───────────────────────────────────────────────────────────

    /**
     * Converts a CameraX ImageProxy (YUV_420_888) to an RGBA OpenCV Mat.
     */
    public static Mat imageProxyToMat(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuf  = planes[0].getBuffer();
        ByteBuffer uBuf  = planes[1].getBuffer();
        ByteBuffer vBuf  = planes[2].getBuffer();

        int ySize = yBuf.remaining();
        int uSize = uBuf.remaining();
        int vSize = vBuf.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuf.get(nv21, 0, ySize);
        vBuf.get(nv21, ySize, vSize);
        uBuf.get(nv21, ySize + vSize, uSize);

        Mat yuv = new Mat(image.getHeight() + image.getHeight() / 2,
            image.getWidth(), CvType.CV_8UC1);
        yuv.put(0, 0, nv21);

        Mat rgba = new Mat();
        Imgproc.cvtColor(yuv, rgba, Imgproc.COLOR_YUV2RGBA_NV21,
            image.getWidth(), image.getHeight());
        yuv.release();
        return rgba;
    }

    // ── Preprocessing ────────────────────────────────────────────────────────

    /** Grayscale + histogram equalisation for face/eye detection. */
    public static Mat toGrayEqualized(Mat rgba) {
        Mat gray = new Mat();
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.equalizeHist(gray, gray);
        return gray;
    }

    // ── Edge Mode ────────────────────────────────────────────────────────────

    /**
     * Computes Canny edges + contour overlay on the input frame.
     * Returns an RGBA Mat suitable for display.
     */
    public static Mat computeEdges(Mat rgba) {
        Mat gray = new Mat();
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);

        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 100, 200);

        // Extract and draw contours
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(edges, contours, new Mat(),
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat output = rgba.clone();
        Imgproc.drawContours(output, contours, -1, new Scalar(0, 255, 0, 255), 1);

        // Overlay Canny in red channel for visibility
        Mat edgesRgba = new Mat();
        Imgproc.cvtColor(edges, edgesRgba, Imgproc.COLOR_GRAY2RGBA);
        Core.addWeighted(output, 0.7, edgesRgba, 0.3, 0, output);

        gray.release();
        edges.release();
        edgesRgba.release();
        return output;
    }

    // ── Motion Mode ──────────────────────────────────────────────────────────

    /**
     * Computes Lucas-Kanade optical flow vectors overlaid on the input frame.
     * Falls back to frame differencing on the first call.
     */
    public static Mat computeMotion(Mat rgba) {
        Mat gray = new Mat();
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);

        Mat output = rgba.clone();

        if (prevGray.empty()) {
            gray.copyTo(prevGray);
            gray.release();
            return output;
        }

        // Frame differencing overlay
        Mat diff = new Mat();
        Core.absdiff(prevGray, gray, diff);
        Imgproc.threshold(diff, diff, 25, 255, Imgproc.THRESH_BINARY);
        Mat diffRgba = new Mat();
        Imgproc.cvtColor(diff, diffRgba, Imgproc.COLOR_GRAY2RGBA);
        Core.addWeighted(output, 0.6, diffRgba, 0.4, 0, output);

        // Lucas-Kanade optical flow
        MatOfPoint2f nextPts = new MatOfPoint2f();
        MatOfByte    status  = new MatOfByte();
        MatOfFloat   err     = new MatOfFloat();

        if (!prevPts.empty()) {
            Video.calcOpticalFlowPyrLK(prevGray, gray, prevPts, nextPts, status, err);
            byte[]  st   = status.toArray();
            Point[] prev = prevPts.toArray();
            Point[] next = nextPts.toArray();

            for (int i = 0; i < st.length; i++) {
                if (st[i] == 1) {
                    Imgproc.arrowedLine(output, prev[i], next[i],
                        new Scalar(255, 100, 0, 255), 1);
                    Imgproc.circle(output, next[i], 3,
                        new Scalar(0, 255, 255, 255), -1);
                }
            }
        }

        // Refresh feature points every frame
        MatOfPoint goodFeaturesTmp = new MatOfPoint();
        Imgproc.goodFeaturesToTrack(gray, goodFeaturesTmp, 150, 0.01, 7);
        prevPts = new MatOfPoint2f(goodFeaturesTmp.toArray());
        gray.copyTo(prevGray);

        diff.release();
        diffRgba.release();
        gray.release();
        return output;
    }

    /** Draw a text string on a Mat (convenience wrapper). */
    public static void putText(Mat mat, String text, Point origin, Scalar color) {
        Imgproc.putText(mat, text, origin,
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);
    }
}
