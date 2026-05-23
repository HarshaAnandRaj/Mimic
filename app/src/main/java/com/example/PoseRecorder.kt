package com.example

import android.content.Context
import android.os.Environment
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.util.Date
import kotlin.math.abs

class PoseRecorder(private val context: Context) {
    private var isRecording = false
    private var frameCount = 0
    private var startTimeMillis: Long = 0

    private var previousLandmarks = mutableMapOf<Int, FloatArray>()
    
    private var tempFile: File? = null
    private var writer: java.io.BufferedWriter? = null

    private var deviceGravity: FloatArray? = null
    
    fun setDeviceGravity(gravity: FloatArray) {
        deviceGravity = gravity.clone()
    }

    fun startRecording() {
        frameCount = 0
        previousLandmarks.clear()
        startTimeMillis = System.currentTimeMillis()
        isRecording = true
        
        val cacheDir = context.cacheDir
        tempFile = File(cacheDir, "temp_mocap_recording.json").apply { 
            if (exists()) delete()
            createNewFile()
        }
        writer = tempFile?.bufferedWriter()
        writer?.write("[\n") // Start of array
        
        // Write metadata frame
        val metaObj = JSONObject()
        metaObj.put("type", "metadata")
        val gravityArray = JSONArray()
        deviceGravity?.let {
            gravityArray.put(it[0])
            gravityArray.put(it[1])
            gravityArray.put(it[2])
        }
        metaObj.put("device_gravity_vector", gravityArray)
        writer?.write(metaObj.toString() + ",\n")
    }

    fun stopRecording(): File? {
        isRecording = false
        if (frameCount == 0) {
            writer?.close()
            return null
        }
        
        try {
            writer?.write("\n]") // End of array
            writer?.flush()
            writer?.close()
            
            val filename = "mocap_session_${Date().time}.json"
            val cacheDir = context.cacheDir
            val finalFile = File(cacheDir, filename)
            
            tempFile?.copyTo(finalFile, overwrite = true)
            tempFile?.delete()
            return finalFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun recordFrame(pose: Pose) {
        if (!isRecording || writer == null) return

        val timestamp = (System.currentTimeMillis() - startTimeMillis) / 1000.0
        val timestampUtc = System.currentTimeMillis()
        val frameObj = JSONObject()
        frameObj.put("frame", frameCount++)
        frameObj.put("timestamp", timestamp)
        frameObj.put("timestamp_utc", timestampUtc)

        val landmarksArray = JSONArray()
        pose.allPoseLandmarks.forEach { landmark ->
            val landmarkObj = JSONObject()
            val id = landmark.landmarkType
            landmarkObj.put("id", id)
            
            var posX = landmark.position.x
            var posY = landmark.position.y
            var posZ = 0.0f
            
            try {
                // Try to use position3D which provides metric scale coordinates
                val p3d = landmark.javaClass.getMethod("getPosition3D").invoke(landmark)
                posZ = p3d.javaClass.getMethod("getZ").invoke(p3d) as Float
                posX = p3d.javaClass.getMethod("getX").invoke(p3d) as Float
                posY = p3d.javaClass.getMethod("getY").invoke(p3d) as Float
            } catch (e: Exception) {
                // Fallback
            }
            
            val confidence = landmark.inFrameLikelihood
            
            // Confidence-aware EMA Filter (Temporal Smoothing)
            val alpha = (confidence * 0.7f).coerceIn(0.1f, 1.0f) 
            
            val prev = previousLandmarks[id]
            
            if (id == 31 || id == 32) {
                var planted = 0
                if (prev != null && confidence > 0.4f) {
                    val dy = kotlin.math.abs(posY - prev[1])
                    if (dy < 0.015f) { // Very little vertical movement
                        planted = 1
                    }
                }
                landmarkObj.put("planted", planted)
            }
            
            if (prev != null) {
                // Dynamic Velocity Clamping
                val MAX_VELOCITY = 0.15f // max movement in frames. Since we're in metric/pixels, this will need tuning if metric, but let's apply max limits
                var dx = posX - prev[0]
                var dy = posY - prev[1]
                var dz = posZ - prev[2]
                
                if (abs(dx) > MAX_VELOCITY) posX = prev[0] + (if(dx>0) MAX_VELOCITY else -MAX_VELOCITY)
                if (abs(dy) > MAX_VELOCITY) posY = prev[1] + (if(dy>0) MAX_VELOCITY else -MAX_VELOCITY)
                if (abs(dz) > MAX_VELOCITY) posZ = prev[2] + (if(dz>0) MAX_VELOCITY else -MAX_VELOCITY)
                
                posX = alpha * posX + (1 - alpha) * prev[0]
                posY = alpha * posY + (1 - alpha) * prev[1]
                posZ = alpha * posZ + (1 - alpha) * prev[2]
            }
            
            previousLandmarks[id] = floatArrayOf(posX, posY, posZ)
            
            landmarkObj.put("x", posX)
            landmarkObj.put("y", posY)
            landmarkObj.put("z", posZ)
            landmarkObj.put("visibility", confidence)
            landmarksArray.put(landmarkObj)
        }
        frameObj.put("landmarks", landmarksArray)
        
        try {
            if (frameCount > 1) {
                writer?.write(",\n")
            }
            writer?.write(frameObj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isRecording() = isRecording
    fun getBufferLength() = frameCount
}
