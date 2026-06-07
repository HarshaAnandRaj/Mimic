# Technical System Overview: Intelligent Mock Android Mocap Engine

This document outlines the architecture, data pipeline, and structural constraints for a free, native Android motion capture application. Operating under severe hardware, optical, and thermal bottlenecks, the app is designed not as an absolute 3D measurement utility, but as an **Intelligent Mock Engine**. It enforces strict spatial, anatomical, and mathematical guardrails over probabilistic AI tracking data to output clean, production-ready animation streams.

---

## 1. End-to-End System Architecture

The on-device mobile pipeline runs entirely decoupled across distinct processing layers to maximize execution speed and maintain UI responsiveness:

```
[ CameraX Input Pipeline ]
       │  (Continuous 720p / 30 FPS Stream)
       ▼
[ Preflight Capability Profiler ] ──► Calculates Survival Index (S) to set Video quality
       │
       ▼
[ MediaPipe Tasks-Vision Engine ] ──► Extracts 3D Pose World Landmarks (Metric Meters)
       │
       ▼
[ Deterministic State Machine ]  ──► Controls lifecycle (Uninitialized ➔ Calibration ➔ Tracking)
       │
       ▼
[ Biomechanical Constraint Layer ] 
       │  • Rule 1: Distance Cage (Proportion Length Lock)
       │  • Rule 2: Axis Box (Anatomical Angular Clamping)
       │  • Rule 3: Floor Penetration Guard (Gravity Anchor)
       │  • Rule 4: Dynamic Velocity Cage (Motion Blur Shutter Ceiling)
       │  • Rule 5: Kalman Tracking (Predictive State Estimation)
       │
       ▼
[ Memory Bounded Cache Stream ]  ──► Reusable FloatArray ➔ Raw Sequential Binary Local Disk Cache

```

---

## 2. Depth Estimation Mechanism & Structural Mapping

Because standard Android devices lack hardware-based infrared depth mapping, the system relies on a two-tier coordinate tracking architecture provided by the computer vision framework.

### Coordinate Space Separation

The engine intercepts two distinct data outputs generated from the incoming camera frames:

1. **Screen-Space Landmarks (`PoseLandmarks`):** 2D normalized coordinates tracking where the joints sit across the flat pixel frame. These are strictly used to render the live wireframe preview on the device screen.
2. **Metric World Landmarks (`PoseWorldLandmarks`):** A completely separate 3D mathematical space tracking coordinates in actual **metric meters**.

### The Depth Inference Engine

The framework estimates depth ($Z$) using a fully convolutional neural network model trained on a dual-input dataset (combining synthetic 3D models with real-world stereoscopic video).

* **The Spatial Origin:** The engine automatically designates the midpoint between the actor's left and right hip joints as the mathematical root origin coordinate $(0.0, 0.0, 0.0)$ of the world.
* **The Scale Invariant Metric:** Every tracking point output represents its distance from that hip root in real meters. If an actor steps forward or backward relative to the lens, their screen-space pixels shift dramatically, but their metric world coordinates stay stable, centered around their pelvis. This provides the app with clean spatial translations out of a single camera lens.

---

## 3. The State-Driven Tracking Lifecycle

The application operates as a deterministic finite state machine to minimize compute overhead and protect memory buffers:

* **Uninitialized State:** The app runs a lightweight person detector. It ignores complex joint mapping entirely until a high-confidence bounding-box match registers a human presence in the view.
* **Calibration Wait State:** Once a human is detected, the UI instructs the user to hold a stable T-pose. The engine monitors joint vectors. When alignment parameters are met, it takes a geometric snapshot of the actor's body proportions (measuring explicit bone lengths in meters) and permanently locks those scale values into memory.
* **Tracking State:** The core engine activates full pose tracking, applying continuous mathematical filters and anatomical limits to incoming positional data.
* **Recovery Freeze State:** If an actor spins around or steps partially out of frame, the global tracker confidence score drops below a safe threshold. The state machine freezes the skeleton in its last known good frame, ignoring chaotic sensor tracking spikes until confidence climbs back to a stable baseline.

---

## 4. The Gauntlet of Constraints (The Invisible Cages)

To turn unstable, probabilistic AI tracking points into natural human motion, the data passes through strict geometric and mathematical rules before it is compiled into an animation file:

### Rule 1: Bone Length Isolation (The Distance Cage)

During calibration, specific bone lengths (e.g., Shoulder-to-Elbow, Hip-to-Knee) are calculated and cached. During live tracking, perspective distortion often causes the raw AI positions to stretch or shrink. The application calculates the directional vector of the limb, discards the raw tracked distance, and forces the child joint position to sit exactly at the locked calibration length along that vector. The skeleton is physically prevented from stretching.

### Rule 2: Rotational Clamping (The Axis Box)

Human joints move within set biological boundaries. The engine calculates local rotations and vectors. For hinge joints (elbows and knees), the application applies hard limits (e.g., preventing a knee from bending forward or an elbow backward). A dot-product projection algorithm detects if the tracked shin or forearm points opposite its anatomical plane and projects it back, resulting in a clean, biologically plausible pose.

### Rule 3: Floor Penetration Guard

The application uses the hardware gravity sensor to define an absolute floor plane based on the anchor positions (ankles/heels) captured during the T-pose calibration. If chaotic AI tracking guesses a foot has sunk below this plane, the vertical axis is hard-clamped, preventing the character from ever sinking underground.

### Rule 4: Dynamic Velocity Clamping (The Blur Ceiling)

Human joints have a hard physical acceleration limit. If a blurred frame causes a tracking coordinate to jump wildly (implying an anatomically impossible speed), the velocity gate intercepts the value, truncates the displacement to the maximum allowable human limit, and smoothly glides the bone along its restricted trajectory.

### Rule 5: Kalman Filtering (Predictive Smoothing)

Instead of a trailing Exponential Moving Average (EMA) that introduces lag, the app uses an aerospace-grade Kalman Filter per joint axis. This predicts the next position based on physical velocity, dynamically weighing its own prediction against the ML Kit confidence output tracking. When tracking fails briefly, the engine safely relies on velocity prediction (Dead Reckoning) to glide joints organically until visual confidence is restored.

---

## 5. Memory Management & Video Fallback Pipelines

To survive on low-tier or thermally throttled Android devices without getting terminated by the operating system's Low Memory Killer (LMK), the app treats memory as a bounded, predictable streaming pipeline.

### The Bounded Streaming Pipeline

* **Zero Object Allocations:** The application bypasses the Java Garbage Collector during recording by entirely avoiding object instantiations inside the tracking loop. Raw landmark floats are poured directly into a single, flat, reusable primitive `FloatArray`.
* **Sequential Binary Scratch Files:** Instead of caching thousands of frames in the device RAM, data is continuously flushed to local app cache storage as a raw, uncompressed binary `.raw` file stream in 64KB chunks.

### Probabilistic Preflight & Quality Swapping

Because Android locks the video encoding parameters (`MediaCodec`) the moment recording begins, the resolution cannot be altered mid-capture. The app uses a **Preflight Profiler** to evaluate system memory, available storage, and current thermal metrics to calculate a **Survival Index ($S$)**:

* **Optimal Path ($S \ge 0.85$):** Launches the session with high-resolution **1080p Full HD** reference video alongside full tracking topology.
* **Resource Conservation Path ($S < 0.50$):** Forces the camera framework to initialize video capture at an ultra-low **480p SD** profile. This drastically minimizes memory bus and encoder pressure, ensuring the phone stays cool and the core skeletal float stream and real-time confidence metrics run safely without dropping frames.
* **Mid-Session Emergency Soft Drop:** If thermal throttling spikes unexpectedly mid-recording, the system executes a soft stop on the video encoder to write out the partial reference file and unbinds the video use case entirely. The hardware codec memory is instantly reclaimed, while the lightweight tracking loop continues logging skeletal vectors to disk uninterrupted.
