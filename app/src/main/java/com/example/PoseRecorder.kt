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

    private var previousLandmarks = mutableMapOf<Int, FloatArray>()
    private var deviceGravity: FloatArray? = null
    
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var totalConf = 0f
    private var lowConfFrames = 0
    
    // Advanced Corrective Pipelines
    data class KalmanState(var p: Float = 0f, var v: Float = 0f, var cov: Float = 1f)
    private var kalmanStates = Array(33) { Array(3) { KalmanState() } }
    private var lastFrameTime: Long = 0
    private var wristOccludedFrames = mutableMapOf<Int, Int>()
    
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

    fun calibrate(pose: Pose) {
        val positions = mutableMapOf<Int, FloatArray>()
        pose.allPoseLandmarks.forEach { landmark ->
            val id = landmark.landmarkType
            if (id >= 33) return@forEach
            var posX = landmark.position.x
            var posY = landmark.position.y
            var posZ = 0.0f
            try {
                val p3d = landmark.javaClass.getMethod("getPosition3D").invoke(landmark)
                posZ = p3d.javaClass.getMethod("getZ").invoke(p3d) as Float
                posX = p3d.javaClass.getMethod("getX").invoke(p3d) as Float
                posY = p3d.javaClass.getMethod("getY").invoke(p3d) as Float
            } catch (e: Exception) {}
            
            positions[id] = floatArrayOf(posX, posY, posZ)
            
            // Floor plane analysis. 
            // In MLKit, Y is downwards, so the floor is the maximum Y value.
            if (id in 27..32) {
                if (posY > floorPlaneY) {
                    floorPlaneY = posY
                }
            }
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
    }

    fun setDeviceGravity(gravity: FloatArray) {
        deviceGravity = gravity.clone()
    }

    fun startRecording() {
        frameCount = 0
        totalConf = 0f
        lowConfFrames = 0
        previousLandmarks.clear()
        kalmanStates = Array(33) { Array(3) { KalmanState() } }
        wristOccludedFrames.clear()
        lastFrameTime = System.currentTimeMillis()
        
        try {
            val filename = "mocap_session_${Date().time}.json"
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (dir?.exists() == false) dir.mkdirs()
            currentFile = File(dir, filename)
            
            writer = currentFile?.bufferedWriter()
            writer?.write("[\n")
            
            // Metadata frame
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
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Proceed without file logging safely
            currentFile = null
            writer = null
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
                writer?.close()
                writer = null
            } catch (ignored: Exception) {}
            return null
        }
        
        lastSessionStats = SessionStats(totalConf / frameCount, lowConfFrames, frameCount)
        
        try {
            writer?.write("\n]")
            writer?.close()
            writer = null
            return currentFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun recordFrame(pose: Pose) {
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
            
            var posX = landmark.position.x
            var posY = landmark.position.y
            var posZ = 0.0f
            
            try {
                val p3d = landmark.javaClass.getMethod("getPosition3D").invoke(landmark)
                posZ = p3d.javaClass.getMethod("getZ").invoke(p3d) as Float
                posX = p3d.javaClass.getMethod("getX").invoke(p3d) as Float
                posY = p3d.javaClass.getMethod("getY").invoke(p3d) as Float
            } catch (e: Exception) {}
            
            var confidence = landmark.inFrameLikelihood
            
            // Initialization
            if (kalmanStates[id][0].cov == 1f && previousLandmarks[id] == null) {
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
                         val hipPos = previousLandmarks[hipId]
                         if (hipPos != null) {
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
            val R = 1.0f / (confidence.coerceAtLeast(0.001f) * 10f)
            val measures = floatArrayOf(posX, posY, posZ)
            
            for (axis in 0..2) {
                val state = kalmanStates[id][axis]
                val measure = measures[axis]
                
                // Predict with slight velocity damping
                var dampedV = state.v * 0.95f
                val predP = state.p + dampedV * dt
                val predCov = state.cov + 0.5f // Process noise Q
                
                // Update
                val K = predCov / (predCov + R)
                state.p = predP + K * (measure - predP)
                state.v = dampedV + (K * (measure - predP) / dt).coerceIn(-500f, 500f) // clamp velocity
                state.cov = (1 - K) * predCov
                
                measures[axis] = state.p
            }
            
            var planted = 0f
            if (id == 31 || id == 32) {
                if (previousLandmarks[id] != null && confidence > 0.4f) {
                    val dy = kotlin.math.abs(measures[1] - previousLandmarks[id]!![1])
                    if (dy < 2.0f) planted = 1f
                }
            }
            
            val baseIdx = id * 5
            landmarkData[baseIdx] = measures[0]
            landmarkData[baseIdx + 1] = measures[1]
            landmarkData[baseIdx + 2] = measures[2]
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
            previousLandmarks[i] = floatArrayOf(landmarkData[i*5], landmarkData[i*5+1], landmarkData[i*5+2])
        }
        
        var sumConf = 0f
        for (i in 0 until 33) {
            sumConf += landmarkData[i * 5 + 3]
        }
        val avgConf = sumConf / 33f
        if (avgConf < 0.6f) lowConfFrames++
        totalConf += avgConf
        
        try {
            if (writer != null) {
                val frameObj = JSONObject()
                frameObj.put("frame", frameCount)
                frameObj.put("timestamp", timestamp)
                frameObj.put("timestamp_utc", timestampUtc)
                
                val landmarksArray = JSONArray()
                for (i in 0 until 33) {
                    val baseIdx = i * 5
                    val landmarkObj = JSONObject()
                    landmarkObj.put("id", i)
                    landmarkObj.put("x", landmarkData[baseIdx])
                    landmarkObj.put("y", landmarkData[baseIdx + 1])
                    landmarkObj.put("z", landmarkData[baseIdx + 2])
                    landmarkObj.put("visibility", landmarkData[baseIdx + 3])
                    
                    if (i == 31 || i == 32) {
                        landmarkObj.put("planted", landmarkData[baseIdx + 4].toInt())
                    }
                    landmarksArray.put(landmarkObj)
                }
                frameObj.put("landmarks", landmarksArray)
                
                if (frameCount > 0) {
                    writer?.write(",\n")
                }
                writer?.write(frameObj.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        frameCount++
    }

    fun isRecording() = isRecording
    fun getBufferLength() = frameCount
}
