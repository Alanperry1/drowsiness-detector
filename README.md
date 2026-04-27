# Drowsiness & Fatigue Detector


---

## Project Structure

```
drowsiness-detector/
├── android-app/              ← Full Android application
│   └── app/src/main/
│       ├── java/com/aiiot/drowsiness/
│       │   ├── MainActivity.java
│       │   ├── CameraActivity.java       ← Main detection loop
│       │   ├── detection/
│       │   │   ├── FaceDetector.java     ← Haar cascade face detection
│       │   │   ├── EyeLandmarkExtractor.java
│       │   │   └── EARCalculator.java    ← Eye Aspect Ratio + state machine
│       │   ├── sensor/
│       │   │   └── HeadPoseEstimator.java ← Gyroscope + complementary filter
│       │   ├── alert/
│       │   │   └── AlertManager.java     ← Audio + haptic + visual alerts
│       │   ├── ui/
│       │   │   ├── OverlayView.java      ← OpenCV canvas overlay
│       │   │   └── DashboardActivity.java
│       │   └── utils/
│       │       ├── MatUtils.java         ← Mat helpers, edge & motion modes
│       │       └── SessionTracker.java
│       └── res/layout/
│           ├── activity_camera.xml
│           └── activity_dashboard.xml
└── cli-prototype/            ← Command-line validation tool
    └── src/main/java/com/aiiot/cli/
        └── Main.java
```

---

## Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 11+ |
| Android SDK | API 24 min / API 34 target |
| OpenCV Android SDK | 4.9.0 |

---

## Android App Setup

### 1. Import OpenCV Module

1. Download the [OpenCV Android SDK 4.9.0](https://opencv.org/releases/)
2. In Android Studio: **File → New → Import Module**
3. Select the `sdk/` folder inside the downloaded OpenCV package
4. Name the module `opencv`
5. In `app/build.gradle`, confirm `implementation project(':opencv')` is present

### 2. Add Haar Cascade XMLs to Assets

Place these two files in `app/src/main/assets/`:
- `haarcascade_frontalface_alt2.xml`
- `haarcascade_eye.xml`

Download from: https://github.com/opencv/opencv/tree/master/data/haarcascades

### 3. Add Alarm Sound

Place an alarm audio file at:
```
app/src/main/res/raw/alarm_beep.mp3
```

### 4. Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio Run button (Shift+F10)
```

---

## CLI Prototype Setup

```bash
cd cli-prototype

# Place opencv-490.jar in:
mkdir libs
cp /path/to/opencv-4.9.0/build/bin/opencv-490.jar libs/

# On Linux/macOS, set native library path:
export LD_LIBRARY_PATH=/path/to/opencv-4.9.0/build/lib:$LD_LIBRARY_PATH

# Build fat JAR
./gradlew jar

# Run edge detection + contour extraction
java -jar build/libs/cli-prototype-1.0.jar \
  --mode edges --input samples/face.jpg

# Run optical flow between two frames
java -jar build/libs/cli-prototype-1.0.jar \
  --mode flow --input samples/frame1.jpg --input2 samples/frame2.jpg

# Process sensor CSV with EMA smoothing
java -jar build/libs/cli-prototype-1.0.jar \
  --mode sensors --input samples/sensor_log.csv
```

### Sensor CSV Format

```
timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z
0.000,0.12,-0.03,9.78,0.012,0.003,-0.001
0.020,0.11,-0.02,9.80,0.034,0.005,-0.002
...
```

---

## Processing Modes (In-App)

| Mode | Description |
|---|---|
| **DROWSINESS** | Default. Face + eye detection, EAR computation, alerts |
| **EDGES** | Canny edge detection + contour overlay |
| **MOTION** | Frame differencing + Lucas-Kanade optical flow vectors |

Toggle between modes using the **Switch Mode** button in the app.

---

## Algorithm Summary

**Eye Aspect Ratio (EAR):**
```
EAR = (||p1-p5|| + ||p2-p4||) / (2 * ||p0-p3||)
Alert when EAR < 0.25 for 3+ consecutive frames
```

**Head Pitch (Complementary Filter):**
```
smoothOmega = 0.10 * rawOmega + 0.90 * smoothOmega   (EMA)
pitch(t)    = 0.96 * (pitch(t-1) + smoothOmega * dt)
Alert when pitch > 20 degrees
```

---

## Permissions Required

- `CAMERA` — front-facing camera feed
- `VIBRATE` — haptic alerts

No `INTERNET`, no `WRITE_EXTERNAL_STORAGE`, no biometric data stored.

---

## License

MIT License — AIoT Lab, 2026
