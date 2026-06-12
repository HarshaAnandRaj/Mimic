package com.example

import android.content.Context
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PoseRecorder(private val context: Context) {
    private var isRecording = false
    private var frameCount = 0
    private var startTimeMillis: Long = 0

    private var previousLandmarks = Array(33) { FloatArray(3) }
    private var previousLandmarksValid = BooleanArray(33)
    private var deviceGravity: FloatArray? = null
    
    private var jsonWriter: android.util.JsonWriter? = null
    private var currentFile: File? = null
    private var totalConf = 0f
    private var lowConfFrames = 0
    
    // Advanced Corrective Pipelines
    data class KalmanState(var p: Float = 0f, var v: Float = 0f, var cov: Float = 1f)
    private var kalmanStates = Array(33) { Array(3) { KalmanState() } }
    private var lastFrameTime: Long = 0
    private var wristOccludedFrames = mutableMapOf<Int, Int>()
    
    private val tempMeasures = FloatArray(3)

    private val bonePairs = listOf(
        Pair(11, 13), Pair(13, 15), // Left arm
        Pair(12, 14), Pair(14, 16), // Right arm
        Pair(23, 25), Pair(25, 27), Pair(27, 31), // Left leg
        Pair(24, 26), Pair(26, 28), Pair(28, 32), // Right leg
        Pair(11, 23), Pair(12, 24), // Torso sides
        Pair(11, 12), Pair(23, 24)  // Shoulders, Hips
    )
    private val calibratedBoneLengths = mutableMapOf<Pair<Int, Int>, Float>()
    private var floorPlaneY = Float.MIN_VALUE
    private var calibratedTPoseJson: org.json.JSONArray? = null
    private val calibrationLock = Any()
    
    private val calibrationAccumulator = mutableMapOf<Int, FloatArray>()
    private var calibrationFramesAccumulated = 0

    fun accumulateCalibration(pose: SmoothedPose) {
        pose.allPoseLandmarks.forEach { landmark ->
            val id = landmark.landmarkType
            if (id >= 33) return@forEach
            
            var posX = landmark.x
            var posY = landmark.y
            var posZ = landmark.z
            
            if (calibrationFramesAccumulated == 0 && id == 0) {
                calibrationAccumulator.clear()
            }
            
            val acc = calibrationAccumulator.getOrPut(id) { FloatArray(3) }
            acc[0] += posX
            acc[1] += posY
            acc[2] += posZ
        }
        if (pose.allPoseLandmarks.isNotEmpty()) {
            calibrationFramesAccumulated++
        }
    }

    fun finalizeCalibration() {
        if (calibrationFramesAccumulated == 0) return
        
        floorPlaneY = Float.MIN_VALUE
        val positions = mutableMapOf<Int, FloatArray>()
        
        val tempJsonArray = org.json.JSONArray()

        for (id in 0 until 33) {
            val acc = calibrationAccumulator[id] ?: continue
            val posX = acc[0] / calibrationFramesAccumulated
            val posY = acc[1] / calibrationFramesAccumulated
            val posZ = acc[2] / calibrationFramesAccumulated
            
            positions[id] = floatArrayOf(posX, posY, posZ)
            
            val lmJson = org.json.JSONObject()
            lmJson.put("id", id)
            lmJson.put("x", posX)
            lmJson.put("y", posY)
            lmJson.put("z", posZ)
            lmJson.put("visibility", 1.0)
            tempJsonArray.put(lmJson)
            
            if (id in 27..32) {
                if (posY > floorPlaneY) {
                    floorPlaneY = posY
                }
            }
        }

        synchronized(calibrationLock) {
            calibratedTPoseJson = tempJsonArray
        }
        
        for (pair in bonePairs) {
            val p1 = positions[pair.first]
            val p2 = positions[pair.second]
            if (p1 != null && p2 != null) {
                val dx = p1[0] - p2[0]
                val dy = p1[1] - p2[1]
                val dz = p1[2] - p2[2]
                val dist = kotlin.math.sqrt((dx*dx + dy*dy + dz*dz).toDouble()).toFloat()
                calibratedBoneLengths[pair] = dist
            }
        }
        
        // Reset
        calibrationAccumulator.clear()
        calibrationFramesAccumulated = 0
    }

    fun calibrate(pose: SmoothedPose) {
        accumulateCalibration(pose)
        finalizeCalibration()
    }

    fun setDeviceGravity(gravity: FloatArray) {
        deviceGravity = gravity.clone()
    }

    fun startRecording(imageWidth: Int, imageHeight: Int, isFrontCamera: Boolean) {
        frameCount = 0
        totalConf = 0f
        lowConfFrames = 0
        previousLandmarksValid.fill(false)
        kalmanStates = Array(33) { Array(3) { KalmanState() } }
        wristOccludedFrames.clear()
        lastFrameTime = System.currentTimeMillis()
        
        try {
            val filename = "mocap_session_${Date().time}.json"
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (dir?.exists() == false) dir.mkdirs()
            currentFile = File(dir, filename)
            
            jsonWriter = android.util.JsonWriter(currentFile?.bufferedWriter())
            jsonWriter?.beginArray()
            
            // Metadata frame
            jsonWriter?.beginObject()
            jsonWriter?.name("type")?.value("metadata")
            jsonWriter?.name("image_width")?.value(imageWidth)
            jsonWriter?.name("image_height")?.value(imageHeight)
            jsonWriter?.name("is_front_camera")?.value(isFrontCamera)
            
            deviceGravity?.let {
                jsonWriter?.name("device_gravity_vector")
                jsonWriter?.beginArray()
                jsonWriter?.value(it[0])
                jsonWriter?.value(it[1])
                jsonWriter?.value(it[2])
                jsonWriter?.endArray()
            }
            
            synchronized(calibrationLock) {
                if (calibratedTPoseJson != null) {
                    jsonWriter?.name("tpose")
                    jsonWriter?.beginArray()
                    for (i in 0 until calibratedTPoseJson!!.length()) {
                        val lmJson = calibratedTPoseJson!!.getJSONObject(i)
                        jsonWriter?.beginObject()
                        if (lmJson.has("id")) jsonWriter?.name("id")?.value(lmJson.getInt("id"))
                        if (lmJson.has("x")) jsonWriter?.name("x")?.value(lmJson.getDouble("x"))
                        if (lmJson.has("y")) jsonWriter?.name("y")?.value(lmJson.getDouble("y"))
                        if (lmJson.has("z")) jsonWriter?.name("z")?.value(lmJson.getDouble("z"))
                        if (lmJson.has("visibility")) jsonWriter?.name("visibility")?.value(lmJson.getDouble("visibility"))
                        jsonWriter?.endObject()
                    }
                    jsonWriter?.endArray()
                }
            }
            jsonWriter?.endObject()
            
        } catch (e: Exception) {
            e.printStackTrace()
            currentFile = null
            jsonWriter = null
        }
        
        startTimeMillis = System.currentTimeMillis()
        isRecording = true
    }

    data class SessionStats(val avgConfidence: Float, val lowConfFrames: Int, val totalFrames: Int)
    var lastSessionStats: SessionStats? = null

    fun stopRecording(): File? {
        isRecording = false
        if (frameCount == 0 || currentFile == null) {
            try {
                jsonWriter?.close()
                jsonWriter = null
                currentFile?.delete()
            } catch (ignored: Exception) {}
            return null
        }
        
        lastSessionStats = SessionStats(totalConf / frameCount, lowConfFrames, frameCount)
        
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

    fun recordFrame(pose: SmoothedPose) {
        if (!isRecording) return

        val currentTime = System.currentTimeMillis()
        val timestamp = (currentTime - startTimeMillis) / 1000.0
        val timestampUtc = currentTime
        var dt = (currentTime - lastFrameTime) / 1000f
        if (dt <= 0f) dt = 0.033f
        lastFrameTime = currentTime

        val landmarkData = FloatArray(33 * 5)
        
        // 1. Base processing & Kalman Filtering
        pose.allPoseLandmarks.forEach { landmark ->
            val id = landmark.landmarkType
            if (id >= 33) return@forEach
            
            var posX = landmark.x
            var posY = landmark.y
            var posZ = landmark.z
            
            var confidence = landmark.inFrameLikelihood
            
            // Initialization
            if (kalmanStates[id][0].cov == 1f && !previousLandmarksValid[id]) {
                kalmanStates[id][0].p = posX
                kalmanStates[id][1].p = posY
                kalmanStates[id][2].p = posZ
            }
            
            // 5. Occlusion Fallback (Dead Reckoning)
            if (id == 15 || id == 16) { // Wrists
                if (confidence < 0.3f) {
                    val occludedCount = wristOccludedFrames.getOrDefault(id, 0) + 1
                    wristOccludedFrames[id] = occludedCount
                    
                    if (occludedCount > 4) {
                         // Interpolate down to hip
                         val hipId = if (id == 15) 23 else 24
                         if (previousLandmarksValid[hipId]) {
                             val hipPos = previousLandmarks[hipId]
                             posX = kalmanStates[id][0].p + (hipPos[0] - kalmanStates[id][0].p) * 0.1f
                             posY = kalmanStates[id][1].p + ((hipPos[1] + 150f) - kalmanStates[id][1].p) * 0.1f // Add to Y to drop it slightly below hip
                             posZ = kalmanStates[id][2].p + (hipPos[2] - kalmanStates[id][2].p) * 0.1f
                             confidence = 1.0f // Treat artificial target as strong
                         } else {
                             confidence = 0.001f // Dead reckon
                         }
                    } else {
                        confidence = 0.001f // Dead reckon for first few frames
                    }
                } else {
                    wristOccludedFrames[id] = 0
                }
            }

            // 4. Kalman Update
            val Q = if (id in listOf(15, 16, 27, 28, 31, 32)) 5.0f else if (id in 0..10 || id == 23 || id == 24) 0.1f else 0.5f // Ankles/Wrists move fast, Face/Hips move smooth
            val R = 1.0f / (confidence.coerceAtLeast(0.001f) * 10f)
            tempMeasures[0] = posX
            tempMeasures[1] = posY
            tempMeasures[2] = posZ
            
            for (axis in 0..2) {
                val state = kalmanStates[id][axis]
                val measure = tempMeasures[axis]
                
                // Predict with slight velocity damping
                var dampedV = state.v * 0.95f
                val predP = state.p + dampedV * dt
                val predCov = state.cov + Q // Adaptive Process noise Q
                
                // Update
                val K = predCov / (predCov + R)
                state.p = predP + K * (measure - predP)
                state.v = dampedV + (K * (measure - predP) / dt).coerceIn(-500f, 500f) // clamp velocity
                state.cov = (1 - K) * predCov
                
                tempMeasures[axis] = state.p
            }
            
            var planted = 0f
            if (id == 31 || id == 32) {
                if (previousLandmarksValid[id] && confidence > 0.4f) {
                    val dy = kotlin.math.abs(tempMeasures[1] - previousLandmarks[id][1])
                    if (dy < 2.0f) planted = 1f
                }
            }
            
            val baseIdx = id * 5
            landmarkData[baseIdx] = tempMeasures[0]
            landmarkData[baseIdx + 1] = tempMeasures[1]
            landmarkData[baseIdx + 2] = tempMeasures[2]
            landmarkData[baseIdx + 3] = confidence
            landmarkData[baseIdx + 4] = planted
        }
        
        // 3. Absolute Floor Penetration Guard
        if (floorPlaneY > Float.MIN_VALUE) {
            for (id in 27..32) {
                if (landmarkData[id*5 + 1] > floorPlaneY) {
                    landmarkData[id*5 + 1] = floorPlaneY
                    kalmanStates[id][1].p = floorPlaneY
                    kalmanStates[id][1].v = 0f
                }
            }
        }
        
        // 1. Bone Length Enforcement (Distance Cage)
        for (pair in bonePairs) {
            val parent = pair.first
            val child = pair.second
            val expectedLen = calibratedBoneLengths[pair] ?: continue
            
            val pX = landmarkData[parent*5]
            val pY = landmarkData[parent*5+1]
            val pZ = landmarkData[parent*5+2]
            
            val cX = landmarkData[child*5]
            val cY = landmarkData[child*5+1]
            val cZ = landmarkData[child*5+2]
            
            val dx = cX - pX
            val dy = cY - pY
            val dz = cZ - pZ
            val currentLen = kotlin.math.sqrt((dx*dx + dy*dy + dz*dz).toDouble()).toFloat()
            
            if (currentLen > 0.001f) {
                val scale = expectedLen / currentLen
                val newX = pX + dx * scale
                val newY = pY + dy * scale
                val newZ = pZ + dz * scale
                
                landmarkData[child*5] = newX
                landmarkData[child*5+1] = newY
                landmarkData[child*5+2] = newZ
                
                // Update Kalman state to reflect physical constraint
                kalmanStates[child][0].p = newX
                kalmanStates[child][1].p = newY
                kalmanStates[child][2].p = newZ
            }
        }
        
        // 2. Anatomical Hinge Clamping
        val clampHinge = { parentId: Int, hingeId: Int, childId: Int, isKnee: Boolean ->
            val hX = landmarkData[hingeId*5]; val hY = landmarkData[hingeId*5+1]; val hZ = landmarkData[hingeId*5+2]
            val pX = landmarkData[parentId*5]; val pY = landmarkData[parentId*5+1]; val pZ = landmarkData[parentId*5+2]
            val cX = landmarkData[childId*5]; val cY = landmarkData[childId*5+1]; val cZ = landmarkData[childId*5+2]
            
            val v1 = Vector3(hX - pX, hY - pY, hZ - pZ).normalized()
            val v2 = Vector3(cX - hX, cY - hY, cZ - hZ).normalized()
            
            // Vector from Left Hip (23) to Right Hip (24) -> body right
            val rHipX = landmarkData[24*5]; val rHipZ = landmarkData[24*5+2]
            val lHipX = landmarkData[23*5]; val lHipZ = landmarkData[23*5+2]
            val rightward = Vector3(rHipX - lHipX, 0f, rHipZ - lHipZ).normalized()
            
            // Forward is Rightward x Down
            val down = Vector3(0f, 1f, 0f)
            val forward = rightward.cross(down).normalized()

            if (isKnee) {
                // Knee bends back (away from forward). If shin points forward, it's bent backwards (flamingo).
                val proj = v2.dot(forward)
                if (proj > 0.1f) {
                    // Remove forward component
                    val clampedV2 = v2.sub(forward.mul(proj)).normalized()
                    val boneLen = calibratedBoneLengths[Pair(hingeId, childId)] ?: Vector3(cX - hX, cY - hY, cZ - hZ).length()
                    val newPos = Vector3(hX, hY, hZ).add(clampedV2.mul(boneLen))
                    landmarkData[childId*5] = newPos.x
                    landmarkData[childId*5+1] = newPos.y
                    landmarkData[childId*5+2] = newPos.z
                    kalmanStates[childId][0].p = newPos.x; kalmanStates[childId][1].p = newPos.y; kalmanStates[childId][2].p = newPos.z
                }
            } else {
                // Elbow bends forward. If forearm points backward (opposite of forward), it's bent backwards.
                val proj = v2.dot(forward)
                if (proj < -0.1f) {
                    val clampedV2 = v2.sub(forward.mul(proj)).normalized()
                    val boneLen = calibratedBoneLengths[Pair(hingeId, childId)] ?: Vector3(cX - hX, cY - hY, cZ - hZ).length()
                    val newPos = Vector3(hX, hY, hZ).add(clampedV2.mul(boneLen))
                    landmarkData[childId*5] = newPos.x
                    landmarkData[childId*5+1] = newPos.y
                    landmarkData[childId*5+2] = newPos.z
                    kalmanStates[childId][0].p = newPos.x; kalmanStates[childId][1].p = newPos.y; kalmanStates[childId][2].p = newPos.z
                }
            }
        }
        
        // Clamp knees
        clampHinge(23, 25, 27, true) // Left knee
        clampHinge(24, 26, 28, true) // Right knee
        // Clamp elbows
        clampHinge(11, 13, 15, false) // Left elbow
        clampHinge(12, 14, 16, false) // Right elbow

        // Finalize state update
        for (i in 0 until 33) {
            previousLandmarks[i][0] = landmarkData[i*5]
            previousLandmarks[i][1] = landmarkData[i*5+1]
            previousLandmarks[i][2] = landmarkData[i*5+2]
            previousLandmarksValid[i] = true
        }
        
        var sumConf = 0f
        for (i in 0 until 33) {
            sumConf += landmarkData[i * 5 + 3]
        }
        val avgConf = sumConf / 33f
        if (avgConf < 0.6f) lowConfFrames++
        totalConf += avgConf
        
        try {
            if (jsonWriter != null) {
                jsonWriter?.beginObject()
                jsonWriter?.name("frame")?.value(frameCount)
                jsonWriter?.name("timestamp")?.value(timestamp)
                jsonWriter?.name("timestamp_utc")?.value(timestampUtc)
                
                jsonWriter?.name("landmarks")
                jsonWriter?.beginArray()
                for (i in 0 until 33) {
                    val baseIdx = i * 5
                    
                    jsonWriter?.beginObject()
                    jsonWriter?.name("id")?.value(i)
                    jsonWriter?.name("x")?.value(landmarkData[baseIdx])
                    jsonWriter?.name("y")?.value(landmarkData[baseIdx + 1])
                    jsonWriter?.name("z")?.value(landmarkData[baseIdx + 2])
                    jsonWriter?.name("visibility")?.value(landmarkData[baseIdx + 3])
                    
                    if (i == 31 || i == 32) {
                        jsonWriter?.name("planted")?.value(landmarkData[baseIdx + 4].toInt())
                    }
                    jsonWriter?.endObject()
                }
                jsonWriter?.endArray()
                
                jsonWriter?.endObject()
            }
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
}
