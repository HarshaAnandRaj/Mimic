# Mimic - Intelligent Mock Android Mocap Engine 🕺📱

Mimic is an Android-native motion capture application that records human body movement in real-time. Operating under severe hardware, optical, and thermal bottlenecks, the app is designed as an **Intelligent Mock Engine**. It enforces spatial and mathematical guardrails over probabilistic AI tracking data to output clean animation streams, primarily aimed at VTubers and indie animators.

## 🟢 Currently Implemented Features

*   **Real-time Pose Detection:** Leverages Google's ML Kit Pose Detection to capture 33 3D body landmarks at roughly 30 FPS in metric world space.
*   **"Ghost Mode" (Zero-Video Capture):** A privacy-first mode that completely unbinds the camera preview. Only math is rendered; zero pixels are shown or recorded.
*   **Air-Gapped Architecture:** All processing runs entirely on-device. The app does not even request the Android `INTERNET` permission.
*   **Sandboxed Storage:** Raw motion data is continuously flushed to the app's internal cache storage, protecting it from other apps.
*   **Gravity-Aligned World Space:** Uses the hardware accelerometer to detect the phone's physical tilt and injects gravity vectors into the metadata, ensuring characters stand flat on virtual floors.
*   **Universal Timecode Injection:** Injects absolute UTC Epoch Timestamps into every frame for perfect sync with external facial mocap (e.g., iPhone ARKit).
*   **Heuristic Foot-Contact Flags:** Calculates ankle velocity and vertical position to inject tiny boolean flags into the data, acting as triggers for IK lock in Blender.
*   **Dynamic Velocity Clamping:** A biomechanical constraint that caps impossible frame-by-frame joint accelerations to smooth out motion blur spikes.
*   **Long-Distance Telemetry UX:** Emits hardware audio beeps during calibration states, accompanied by a high-visibility neon screen flash indicator for clear 10-foot visual feedback.
*   **Bone Length Isolation (Distance Cage):** Locks limb proportions based on the T-pose calibration to prevent perspective distortion shrinking/stretching by enforcing physical distance limits along the calculated vectors.
*   **Anatomical Hinge Clamping:** Hard biological rule enforcement via dot-product analysis to prevent impossible backward bends in hinge joints like elbows and knees.
*   **Absolute Floor Penetration Guard:** Dynamically locks vertical axis thresholds based on ground floor ankle states during calibration to forbid any tracking coordinate from sinking "underground".
*   **Kalman Filter Temporal Smoothing:** An aerospace-grade predictive algorithm that out-performs simple EMA by balancing measurement confidence and projected velocity to eliminate jitter without inducing tracker lag.
*   **Occlusion Fallback (Dead-Reckoning):** A safety algorithm triggered by drops in tracking confidence that intuitively interpolates hidden joints (like occluded wrists) to natural resting positions instead of allowing them to clip inside the chest cavity.

## 🔜 Yet to be Added (Planned Features)

While the core tracking and privacy architecture is complete, the following advanced engine features are actively under development:

*   **Subject Locking (ROI Photobomb Rejector):** Bounding box isolation to prevent the tracker from snapping to background individuals during a session.
*   **Probabilistic Preflight & Quality Swapping:** Dynamically shifting the camera resolution between 1080p and 480p based on thermal and memory survival indexing.
*   **Flat Binary `.raw` Streams:** Transitioning from the current JSON scratch files to a zero-object-allocation sequential binary scratch stream to bypass the Java Garbage Collector entirely.
*   **Local UDP Streaming (VMC):** Real-time Wi-Fi streaming to desktop software (Blender / VSeeFace) with optional AES-128 PIN encryption.
*   **Native BVH Export:** Currently, the app exports structured JSON payload. A local builder to compile this JSON into standard Biovision Hierarchy (`.bvh`) skeleton files on-device is planned.

## Privacy & Safety 🔒

Mimic is built with **Aggressive Transparency UX**.
Privacy is not an afterthought; it is our core feature for digital creators. You can view the Zero-Cloud Privacy Policy directly in the HUD. The app guarantees that your video feed is never transmitted, and motion data remains safely sandboxed until you explicitly choose to export it using the Android Storage Access Framework.

## Architecture & Tech Stack 🛠️

Read the complete system design philosophy in the [Technical System Overview](ARCHITECTURE.md).

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose natively with Material 3 styling.
*   **Machine Learning:** Google ML Kit Pose Detection API.
*   **Camera API:** CameraX for seamless camera lifecycle management and image analysis.
*   **Hardware Sensors:** SensorManager (Gravity) & AudioManager/CameraControl (Telemetry).

## Setup & Build Instructions 🚀

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Sync Gradle dependencies.
4.  Run on an Android device (an emulator is not recommended as it requires an active camera feed for motion capture).

## Usage Guide 📖

1.  Launch Mimic and accept the Privacy & Safety guidelines.
2.  Grant Camera permissions.
3.  Position yourself in the camera view (recommend placing the phone on a stable surface in portrait orientation).
4.  Tap **Start MoCap** to begin tracking your movements. The HUD displays recording duration and frame count.
5.  Tap **Stop MoCap** to end the session.
6.  Navigate to the **Library** to playback your motion, rename files, or share them as `.json` or `.bvh`.
