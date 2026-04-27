package com.aiiot.drowsiness.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import com.aiiot.drowsiness.utils.MatUtils;

/**
 * Transparent SurfaceView that draws OpenCV Mat overlays on top of the camera preview.
 */
public class OverlayView extends View {

    private static final Scalar GREEN  = new Scalar(0,   255, 0,   255);
    private static final Scalar RED    = new Scalar(255, 0,   0,   255);
    private static final Scalar YELLOW = new Scalar(255, 230, 0,   255);
    private static final Scalar WHITE  = new Scalar(255, 255, 255, 255);

    private volatile Bitmap currentBitmap;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Draws the drowsiness detection overlay: face box, eye landmarks, EAR text,
     * pitch reading, and a red highlight when drowsy.
     */
    public void drawDrowsinessOverlay(Mat frame, Rect faceRect,
                                       Point[][] eyes, double ear,
                                       boolean drowsy, float pitch) {
        Mat display = frame.clone();

        // Face bounding box
        Scalar boxColor = drowsy ? RED : GREEN;
        Imgproc.rectangle(display,
            new Point(faceRect.x, faceRect.y),
            new Point(faceRect.x + faceRect.width, faceRect.y + faceRect.height),
            boxColor, 2);

        // Eye landmark points and connections
        for (Point[] eye : eyes) {
            for (int i = 0; i < 6; i++) {
                Imgproc.circle(display, eye[i], 2, YELLOW, -1);
            }
            // Connect vertical pairs
            Imgproc.line(display, eye[1], eye[5], YELLOW, 1);
            Imgproc.line(display, eye[2], eye[4], YELLOW, 1);
            Imgproc.line(display, eye[0], eye[3], YELLOW, 1);
        }

        // HUD text
        MatUtils.putText(display,
            String.format("EAR: %.2f", ear),
            new Point(10, 30), WHITE);
        MatUtils.putText(display,
            String.format("Pitch: %.1f", pitch),
            new Point(10, 55), WHITE);

        if (drowsy) {
            MatUtils.putText(display, "! DROWSY !",
                new Point(display.cols() / 2.0 - 60, display.rows() - 20), RED);
        }

        postBitmap(display);
        display.release();
    }

    /** Draws any Mat directly (used by Edge and Motion modes). */
    public void drawMat(Mat mat) {
        postBitmap(mat);
    }

    /** Clears the overlay. */
    public void clear() {
        currentBitmap = null;
        postInvalidate();
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bmp = currentBitmap;
        if (bmp != null && !bmp.isRecycled()) {
            canvas.drawBitmap(bmp, null,
                new android.graphics.RectF(0, 0, getWidth(), getHeight()), null);
        }
    }

    private void postBitmap(Mat mat) {
        Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(),
            Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
        currentBitmap = bmp;
        postInvalidate();
    }
}
