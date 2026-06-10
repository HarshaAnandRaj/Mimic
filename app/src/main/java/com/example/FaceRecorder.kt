package com.example

import android.content.Context
import com.google.mlkit.vision.facemesh.FaceMesh
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

    fun startRecording() {
        frameCount = 0
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

    fun recordFrame(faceMesh: FaceMesh) {
        if (!isRecording) return

        val currentTime = System.currentTimeMillis()
        val timestamp = (currentTime - startTimeMillis) / 1000.0
        val timestampUtc = currentTime

        try {
            jsonWriter?.beginObject()
            jsonWriter?.name("frame")?.value(frameCount)
            jsonWriter?.name("timestamp")?.value(timestamp)
            jsonWriter?.name("timestamp_utc")?.value(timestampUtc)
            
            val rawPoints = faceMesh.allPoints
            // The raw face mesh output points have point.index values. Wait, some could be out of bounds if size is small.
            // But we know indices can be up to 477.
            val mappedPoints = Array<com.google.mlkit.vision.facemesh.FaceMeshPoint?>(478) { null }
            for (point in rawPoints) {
                if (point.index in 0..477) {
                    mappedPoints[point.index] = point
                }
            }
            
            // 1. ARKit Blendshapes
            val boundingBox = faceMesh.boundingBox
            val faceWidth = boundingBox.width().toFloat()
            val faceHeight = boundingBox.height().toFloat()
            
            val blendshapes = FaceMath.solveBlendshapes(mappedPoints, faceWidth, faceHeight)
            val gaze = FaceMath.solveEyeGaze(mappedPoints)
            
            // Add Visemes from Audio
            val blendedAudioShapes = blendshapes.toMutableMap()
            blendedAudioShapes.putAll(gaze)
            if (audioAnalyzer != null) {
                blendedAudioShapes["JawOpen"] = Math.max(blendedAudioShapes["JawOpen"] ?: 0f, audioAnalyzer!!.visemeA.value)
                blendedAudioShapes["MouthPucker"] = audioAnalyzer!!.visemeO.value
            }

            // Write Blendshapes
            jsonWriter?.name("blendshapes")
            jsonWriter?.beginObject()
            for ((key, value) in blendedAudioShapes) {
                jsonWriter?.name(key)?.value(value)
            }
            jsonWriter?.endObject()

            // 2. Head Pose
            val headPose = FaceMath.solveHeadPose(mappedPoints)
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
            
            for (point in rawPoints) {
                val pointIndex = point.index
                val position = point.position
                
                jsonWriter?.beginObject()
                jsonWriter?.name("id")?.value(pointIndex)
                jsonWriter?.name("x")?.value(position.x)
                jsonWriter?.name("y")?.value(position.y)
                jsonWriter?.name("z")?.value(position.z)
                jsonWriter?.name("visibility")?.value(1.0)
                jsonWriter?.endObject()
            }
            
            jsonWriter?.endArray()
            jsonWriter?.endObject()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        frameCount++
    }

    fun isRecording() = isRecording
    fun getBufferLength() = frameCount
}
