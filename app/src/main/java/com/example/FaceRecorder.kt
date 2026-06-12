package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.util.Date

class FaceRecorder(private val context: Context) {

    private var isRecording = false
    private var startTimeMillis: Long = 0
    private var frameCount = 0
    
    private var currentFile: File? = null
    private var jsonWriter: android.util.JsonWriter? = null

    // Ultimate Upgrades
    private val vmcSender = VmcOscSender()
    var audioAnalyzer: AudioVisemeAnalyzer? = null
    
    private val processor = FaceBlendshapeProcessor()
    private val calibrationSamples = mutableListOf<Map<String, Float>>()
    private val CALIBRATION_FRAMES = 30
    private var isCalibrated = false

    fun startRecording(imageWidth: Int, imageHeight: Int, isFrontCamera: Boolean) {
        val prefs = context.getSharedPreferences("mocap_prefs", Context.MODE_PRIVATE)
        vmcSender.targetIp = prefs.getString("osc_ip", "192.168.1.100") ?: "192.168.1.100"
        
        frameCount = 0
        calibrationSamples.clear()
        isCalibrated = false
        audioAnalyzer?.start()
        
        try {
            val filename = "face_mocap_${Date().time}.json"
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (dir?.exists() == false) dir.mkdirs()
            currentFile = File(dir, filename)
            
            jsonWriter = android.util.JsonWriter(currentFile?.bufferedWriter())
            jsonWriter?.beginArray()
            
            // Metadata frame
            jsonWriter?.beginObject()
            jsonWriter?.name("type")?.value("metadata")
            jsonWriter?.name("tracking_mode")?.value("FACE")
            jsonWriter?.name("image_width")?.value(imageWidth)
            jsonWriter?.name("image_height")?.value(imageHeight)
            jsonWriter?.name("is_front_camera")?.value(isFrontCamera)
            jsonWriter?.endObject()
            
        } catch (e: Exception) {
            e.printStackTrace()
            currentFile = null
            jsonWriter = null
        }
        
        startTimeMillis = System.currentTimeMillis()
        isRecording = true
    }

    fun stopRecording(): File? {
        isRecording = false
        audioAnalyzer?.stop()
        
        if (frameCount == 0 || currentFile == null) {
            try {
                jsonWriter?.close()
                jsonWriter = null
                currentFile?.delete()
            } catch (ignored: Exception) {}
            return null
        }
        
        try {
            jsonWriter?.endArray()
            jsonWriter?.close()
            jsonWriter = null
            return currentFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun recordFrame(faceFrame: FaceTrackingFrame) {
        if (!isRecording) return

        val currentTime = System.currentTimeMillis()
        val timestamp = (currentTime - startTimeMillis) / 1000.0
        val timestampUtc = currentTime

        try {
            jsonWriter?.beginObject()
            jsonWriter?.name("frame")?.value(frameCount)
            jsonWriter?.name("timestamp")?.value(timestamp)
            jsonWriter?.name("timestamp_utc")?.value(timestampUtc)

            // Add Visemes from Audio
            val baseAudioShapes = faceFrame.blendshapes.toMutableMap()
            if (audioAnalyzer != null) {
                baseAudioShapes["JawOpen"] = Math.max(baseAudioShapes["JawOpen"] ?: 0f, audioAnalyzer!!.visemeA.value)
                baseAudioShapes["MouthPucker"] = Math.max(baseAudioShapes["MouthPucker"] ?: 0f, audioAnalyzer!!.visemeO.value)
            }
            
            // Neutral Calibration
            if (!isCalibrated) {
                calibrationSamples.add(baseAudioShapes)
                if (calibrationSamples.size >= CALIBRATION_FRAMES) {
                    processor.setNeutral(calibrationSamples)
                    isCalibrated = true
                }
            }
            
            // Process and Smooth Blendshapes (if calibrated it removes neutral, applies custom smoothing)
            val blendedAudioShapes = processor.process(baseAudioShapes, faceFrame.timestampMs).toMutableMap()
            
            // Conflict cleanup
            val jawOpen = blendedAudioShapes["JawOpen"] ?: 0f
            if (jawOpen > 0.5f) {
                blendedAudioShapes["MouthClose"] = 0f
                blendedAudioShapes["MouthRollIn"] = (blendedAudioShapes["MouthRollIn"] ?: 0f) * 0.2f
            }

            // Write Blendshapes
            jsonWriter?.name("blendshapes")
            jsonWriter?.beginObject()
            for ((key, value) in blendedAudioShapes) {
                jsonWriter?.name(key)?.value(value)
            }
            jsonWriter?.endObject()

            // 2. Head Pose from transform matrix if available, otherwise default
            // Matrix to quaternion logic could be added here, but for now we skip or write zeros/defaults
            // MediaPipe matrix is 4x4 flattened (16 floats)
            val headPose = FloatArray(7) { 0f }
            val m = faceFrame.transformMatrix
            if (m != null && m.size == 16) {
                headPose[0] = m[12] // tx
                headPose[1] = m[13] // ty
                headPose[2] = m[14] // tz
                
                // Rotation (extract approx quaternion from matrix)
                val tr = m[0] + m[5] + m[10]
                if (tr > 0f) {
                    val S = Math.sqrt(tr.toDouble() + 1.0).toFloat() * 2f
                    headPose[6] = 0.25f * S // w
                    headPose[3] = (m[6] - m[9]) / S // x
                    headPose[4] = (m[8] - m[2]) / S // y
                    headPose[5] = (m[1] - m[4]) / S // z
                } else if ((m[0] > m[5]) && (m[0] > m[10])) {
                    val S = Math.sqrt(1.0 + m[0] - m[5] - m[10]).toFloat() * 2f
                    headPose[6] = (m[6] - m[9]) / S // w
                    headPose[3] = 0.25f * S // x
                    headPose[4] = (m[1] + m[4]) / S // y
                    headPose[5] = (m[8] + m[2]) / S // z
                } else if (m[5] > m[10]) {
                    val S = Math.sqrt(1.0 + m[5] - m[0] - m[10]).toFloat() * 2f
                    headPose[6] = (m[8] - m[2]) / S // w
                    headPose[3] = (m[1] + m[4]) / S // x
                    headPose[4] = 0.25f * S // y
                    headPose[5] = (m[6] + m[9]) / S // z
                } else {
                    val S = Math.sqrt(1.0 + m[10] - m[0] - m[5]).toFloat() * 2f
                    headPose[6] = (m[1] - m[4]) / S // w
                    headPose[3] = (m[8] + m[2]) / S // x
                    headPose[4] = (m[6] + m[9]) / S // y
                    headPose[5] = 0.25f * S // z
                }
            }

            jsonWriter?.name("head_pose")
            jsonWriter?.beginArray()
            for (v in headPose) {
                jsonWriter?.value(v)
            }
            jsonWriter?.endArray()

            // 3. UDP Live Stream
            vmcSender.sendBlendshapes(blendedAudioShapes)
            vmcSender.sendHeadPose(headPose[0], headPose[1], headPose[2], headPose[3], headPose[4], headPose[5], headPose[6])
            
            jsonWriter?.name("landmarks")
            jsonWriter?.beginArray()
            
            for (point in faceFrame.landmarks) {
                jsonWriter?.beginObject()
                jsonWriter?.name("id")?.value(point.id)
                jsonWriter?.name("x")?.value(point.x)
                jsonWriter?.name("y")?.value(point.y)
                jsonWriter?.name("z")?.value(point.z)
                jsonWriter?.name("visibility")?.value(1.0)
                jsonWriter?.endObject()
            }
            
            jsonWriter?.endArray()
            jsonWriter?.endObject()
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            try { jsonWriter?.close() } catch (ignored: Exception) {}
            jsonWriter = null
        }
        
        frameCount++
    }

    fun isRecording() = isRecording
    fun getBufferLength() = frameCount

    fun close() {
        if (isRecording) stopRecording()
        audioAnalyzer?.stop()
        vmcSender.close()
    }
}
