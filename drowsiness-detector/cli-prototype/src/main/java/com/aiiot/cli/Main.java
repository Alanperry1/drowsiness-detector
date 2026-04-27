package com.aiiot.cli;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.io.*;
import java.util.*;

/**
 * Command-line prototype for the drowsiness detection pipeline.
 *
 * Modes:
 *   --mode edges    Run Canny edge detection + contour extraction on --input image
 *   --mode flow     Run optical flow between --input and --input2
 *   --mode sensors  Parse sensor CSV from --input and apply EMA smoothing
 *
 * Usage:
 *   java -jar cli.jar --mode edges   --input samples/face.jpg
 *   java -jar cli.jar --mode flow    --input samples/f1.jpg --input2 samples/f2.jpg
 *   java -jar cli.jar --mode sensors --input samples/sensor_log.csv
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("[CLI] OpenCV " + Core.VERSION + " loaded");

        Map<String, String> opts = parseArgs(args);
        String mode  = opts.getOrDefault("mode", "edges");
        String input = opts.get("input");

        if (input == null) {
            System.err.println("Error: --input is required");
            System.exit(1);
        }

        switch (mode) {
            case "edges":   runEdges(input);                          break;
            case "flow":    runFlow(input, opts.get("input2"));       break;
            case "sensors": runSensors(input);                        break;
            default:
                System.err.println("Unknown mode: " + mode);
                System.exit(1);
        }
    }

    // ── Edge Detection + Contour Extraction ──────────────────────────────────

    private static void runEdges(String inputPath) {
        Mat src = Imgcodecs.imread(inputPath);
        if (src.empty()) {
            System.err.println("Cannot read image: " + inputPath);
            return;
        }

        // Stage 1: Preprocessing
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);

        // Stage 2: Canny edge detection
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 100, 200);

        // Stage 3: Contour extraction
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        System.out.printf("[CLI] Mode: edges | Input: %s%n", inputPath);
        System.out.printf("[CLI] Contours found: %d%n", contours.size());

        // Sobel magnitude map
        Mat sobelX = new Mat(), sobelY = new Mat(), sobelMag = new Mat();
        Imgproc.Sobel(gray, sobelX, CvType.CV_64F, 1, 0);
        Imgproc.Sobel(gray, sobelY, CvType.CV_64F, 0, 1);
        Core.magnitude(sobelX, sobelY, sobelMag);
        Core.normalize(sobelMag, sobelMag, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);

        // Draw contours on a copy of the source image
        Mat output = src.clone();
        Imgproc.drawContours(output, contours, -1, new Scalar(0, 255, 0), 2);

        Imgcodecs.imwrite("output_edges.jpg",    edges);
        Imgcodecs.imwrite("output_contours.jpg", output);
        Imgcodecs.imwrite("output_sobel.jpg",    sobelMag);

        System.out.println("[CLI] Output written -> output_edges.jpg, " +
            "output_contours.jpg, output_sobel.jpg");
    }

    // ── Optical Flow ─────────────────────────────────────────────────────────

    private static void runFlow(String path1, String path2) {
        if (path2 == null) {
            System.err.println("--mode flow requires --input2");
            return;
        }

        Mat frame1 = Imgcodecs.imread(path1);
        Mat frame2 = Imgcodecs.imread(path2);
        if (frame1.empty() || frame2.empty()) {
            System.err.println("Cannot read input images.");
            return;
        }

        Mat gray1 = new Mat(), gray2 = new Mat();
        Imgproc.cvtColor(frame1, gray1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(frame2, gray2, Imgproc.COLOR_BGR2GRAY);

        // Stage 4: Frame differencing
        Mat diff = new Mat();
        Core.absdiff(gray1, gray2, diff);
        Imgproc.threshold(diff, diff, 25, 255, Imgproc.THRESH_BINARY);
        double motionScore = Core.countNonZero(diff)
            / (double)(diff.rows() * diff.cols());

        // Stage 5: Lucas-Kanade optical flow
        MatOfPoint    corners   = new MatOfPoint();
        MatOfPoint2f  prevPts   = new MatOfPoint2f();
        MatOfPoint2f  nextPts   = new MatOfPoint2f();
        MatOfByte     status    = new MatOfByte();
        MatOfFloat    err       = new MatOfFloat();

        Imgproc.goodFeaturesToTrack(gray1, corners, 150, 0.01, 7);
        prevPts.fromArray(corners.toArray());

        Video.calcOpticalFlowPyrLK(gray1, gray2, prevPts, nextPts, status, err);

        byte[]  st   = status.toArray();
        Point[] prev = prevPts.toArray();
        Point[] next = nextPts.toArray();
        int     good = 0;

        Mat flowOutput = frame2.clone();
        for (int i = 0; i < st.length; i++) {
            if (st[i] == 1) {
                good++;
                Imgproc.arrowedLine(flowOutput, prev[i], next[i],
                    new Scalar(255, 100, 0), 1, Imgproc.LINE_AA, 0, 0.3);
                Imgproc.circle(flowOutput, next[i], 3,
                    new Scalar(0, 255, 255), -1);
            }
        }

        System.out.printf("[CLI] Mode: flow%n");
        System.out.printf("[CLI] Motion score: %.4f%n", motionScore);
        System.out.printf("[CLI] Good flow vectors: %d / %d%n", good, st.length);

        Imgcodecs.imwrite("output_diff.jpg", diff);
        Imgcodecs.imwrite("output_flow.jpg", flowOutput);
        System.out.println("[CLI] Output written -> output_diff.jpg, output_flow.jpg");
    }

    // ── Sensor EMA Logging ───────────────────────────────────────────────────

    private static void runSensors(String csvPath) throws IOException {
        final double ALPHA = 0.10;

        System.out.printf("[CLI] Mode: sensors | Input: %s%n", csvPath);
        System.out.printf("%-12s %-10s %-10s %-10s %-10s %-10s%n",
            "timestamp", "rawGyroX", "smoothGyroX", "rawAccX", "smoothAccX", "pitch");

        BufferedReader reader = new BufferedReader(new FileReader(csvPath));
        reader.readLine(); // skip CSV header

        double smoothGyroX = 0.0;
        double smoothAccX  = 0.0;
        double pitchAngle  = 0.0;
        double prevTime    = -1;
        int    count       = 0;
        String line;

        while ((line = reader.readLine()) != null) {
            String[] cols = line.trim().split(",");
            if (cols.length < 7) continue;

            double t      = Double.parseDouble(cols[0]);
            double accelX = Double.parseDouble(cols[1]);
            double gyroX  = Double.parseDouble(cols[4]);

            // EMA smoothing
            smoothGyroX = ALPHA * gyroX  + (1 - ALPHA) * smoothGyroX;
            smoothAccX  = ALPHA * accelX + (1 - ALPHA) * smoothAccX;

            // Complementary filter pitch integration
            if (prevTime >= 0) {
                double dt = t - prevTime;
                pitchAngle = 0.96 * (pitchAngle + smoothGyroX * dt);
            }
            prevTime = t;

            System.out.printf("%-12.3f %-10.3f %-10.3f %-10.3f %-10.3f %-10.2f%n",
                t, gyroX, smoothGyroX, accelX, smoothAccX, pitchAngle);
            count++;
        }
        reader.close();
        System.out.printf("[CLI] Sensor log processed: %d samples, alpha=%.2f%n",
            count, ALPHA);
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                map.put(args[i].substring(2), args[i + 1]);
            }
        }
        return map;
    }
}
