# Mimic - Intelligent Mock Android Mocap Engine 🕺📱

Mimic is an Android-native motion capture application that records human body movement in real-time. Operating under severe hardware, optical, and thermal bottlenecks, the app is designed as an **Intelligent Mock Engine**. It enforces spatial and mathematical guardrails over probabilistic AI tracking data to output clean animation streams, primarily aimed at Indie film makers and indie animators.

## 🟢 Currently Implemented Features

*   **Real-time Pose Detection:** Leverages Google's ML Kit Pose Detection to capture 33 3D body landmarks at roughly 30 FPS in metric world space.
*   **Real-time Face Tracking:** Uses ML Kit Face Mesh detection to track 468 3D points, complete with an **ARKit 52 Blendshapes** mathematical solver for direct compatibility with Unreal Engine and Indie film maker software.
*   **Audio-Driven Lip Sync (Visemes):** Runs an integrated audio analyzer to generate phonetic visemes, blending mic data with optical data for flawless lip-syncing even when the mouth is occluded.
*   **6-DoF Head Pose & Eye Gaze Vectors:** Calculates precise absolute real-world head rotation (Pitch, Yaw, Roll) and tracks pupil alignment for natural "darting" eye movements and accurate avatars.
*   **Local UDP Streaming (VMC OSC):** Broadcasts real-time motion control data over Wi-Fi to desktop clients like VTube Studio and Blender, bypassing the need for file exports.
*   **High-Performance Zero-Allocation Engine:** Designed to eliminate Garbage Collection (GC) stutters by utilizing pre-allocated arrays in the "Hot Loop" and streamlining JSON creation with `android.util.JsonWriter`.
*   **Active Thermal Throttling:** Monitors the NPU and CPU temperature, programmatically dropping frame rates to save the session and hardware from overheating crashes.
*   **"Ghost Mode" (Hardware Dimming & Zero-Video):** A privacy-first tool that unbinds the camera preview and dynamically drops hardware screen brightness to 1%, allowing hours of battery-friendly recording while rendering zero pixels.
*   **Gravity-Aligned World Space:** Uses the hardware accelerometer to detect the phone's physical tilt and injects gravity vectors into the metadata, ensuring characters stand flat on virtual floors.
*   **Universal Timecode Injection:** Injects absolute UTC Epoch Timestamps into every frame for perfect sync with external workflows.
*   **Heuristic Foot-Contact Flags:** Calculates ankle velocity and vertical position to inject tiny boolean flags into the data, acting as triggers for IK lock in Blender.
*   **Dynamic Velocity Clamping:** A biomechanical constraint that caps impossible frame-by-frame joint accelerations to smooth out motion blur spikes.
*   **Long-Distance Telemetry UX:** Emits hardware audio beeps during calibration states, accompanied by a high-visibility neon screen flash indicator for clear 10-foot visual feedback.
*   **Bone Length Isolation (Distance Cage):** Locks limb proportions based on the T-pose calibration to prevent perspective distortion shrinking/stretching.
*   **Anatomical Hinge Clamping:** Hard biological rule enforcement via dot-product analysis to prevent impossible backward bends in hinge joints like elbows and knees.
*   **Absolute Floor Penetration Guard:** Dynamically locks vertical axis thresholds based on ground floor ankle states during calibration to forbid any tracking coordinate from sinking "underground".
*   **Kalman Filter Temporal Smoothing:** An aerospace-grade predictive algorithm that out-performs simple EMA by balancing measurement confidence and projected velocity to eliminate jitter without inducing tracker lag.
*   **FilterRlAgent (Reinforcement Learning):** Dynamically adjusts the One-Euro filter's beta and cutoff using an onboard Q-Learning algorithm. It optimizes tracking by increasing responsiveness during high-speed actions (like a punch) and aggressively smoothing during slow, steady movements.
*   **Accidental App Exit Protection:** Intelligent BackHandler traps accidental swipe-backs during a live capture session, stopping the session and safely saving the data instead of abruptly crashing.
*   **Zero-Storage Leak Engine:** Automatically hunts for orphaned `.bvh` or `.csv` files linked to a raw capture `.json` and removes them seamlessly during the user deletion process, keeping the device's storage clean.
*   **FileProvider Cache Export:** Advanced permission configurations allow dynamic generation and direct secure sharing of converted `.bvh` and animation data directly from the private cache, seamlessly integrating into external apps without filesystem permissions blocking the transfer.
*   **Toggleable Gesture Controls:** Configurable settings to toggle remote motion triggers (like the Cross-Arm "Stop" gesture) allowing actors to T-Pose or perform naturally without prematurely ending their sessions.
*   **Native BVH Export:** Currently, the app exports structured JSON payload. A local builder compiles this JSON into a standard Biovision Hierarchy (`.bvh`) skeleton file on-device cleanly through the Export Menu.

## 🔜 Yet to be Added (Planned Features)

While the core tracking and architecture is incredibly robust, the following features are actively under development:

*   **Subject Locking (ROI Photobomb Rejector):** Bounding box isolation to prevent the tracker from snapping to background individuals during a session.
*   **Probabilistic Preflight & Quality Swapping:** Dynamically shifting the camera resolution between 1080p and 480p based on thermal and memory survival indexing.

## Privacy & Safety 🔒

Mimic is built with **Aggressive Transparency UX**.
Privacy is not an afterthought; it is our core feature for digital creators. You can view the Zero-Cloud Privacy Policy directly in the HUD. The app guarantees that your video feed is never transmitted. The added `INTERNET` and `RECORD_AUDIO` permissions are used explicitly for local-network UDP transmission (VMC OSC) and local lip-sync viseme extraction. Data remains safely sandboxed until you explicitly choose to export or broadcast it.

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
