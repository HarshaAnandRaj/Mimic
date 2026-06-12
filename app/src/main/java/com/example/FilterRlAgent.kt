package com.example

import org.json.JSONArray
import org.json.JSONObject

class FilterRlAgent(private val id: Int) {
    // Q-Table: 3 States (Slow, Med, Fast) x 3 Actions (Decrease, Keep, Increase Beta)
    private val qTable = Array(3) { FloatArray(3) { 0f } }
    
    private val learningRate = 0.1f
    private val discountFactor = 0.9f
    private var explorationRate = 0.1f // Decays over time
    
    private var currentBeta = 0.04f
    private var lastRawPos = 0f
    private var lastVelocity = 0f

    fun getOptimalBeta(rawX: Float, rawY: Float, filteredX: Float, filteredY: Float, dt: Float): Float {
        val currentPos = rawX + rawY
        val velocity = Math.abs(currentPos - lastRawPos) / dt
        val acceleration = Math.abs(velocity - lastVelocity) / dt
        
        lastRawPos = currentPos
        lastVelocity = velocity

        // 1. Determine State based on pixel velocity
        val state = when {
            velocity < 1000f -> 0 // Slow
            velocity < 4000f -> 1 // Medium
            else -> 2             // Fast
        }

        // 2. Choose Action (Epsilon-Greedy)
        val action = if (Math.random() < explorationRate) {
            (0..2).random()
        } else {
            qTable[state].indices.maxByOrNull { qTable[state][it] } ?: 1
        }

        // 3. Apply Action to Beta
        currentBeta = when (action) {
            0 -> (currentBeta - 0.01f).coerceAtLeast(0.001f)
            1 -> currentBeta
            2 -> (currentBeta + 0.02f).coerceAtMost(1.0f)
            else -> currentBeta
        }

        // 4. Calculate Reward
        val latency = Math.abs(rawX - filteredX) + Math.abs(rawY - filteredY)
        val jitter = acceleration // High acceleration means shaking
        
        val reward = if (state == 2) {
            -latency // Punish lag heavily during fast motion to ensure snappiness
        } else {
            -(jitter * 0.01f) - (latency * 0.1f) // Punish shaking heavily during slow motion
        }

        // 5. Update Q-Table (Bellman Equation)
        val oldQ = qTable[state][action]
        val maxFutureQ = qTable[state].maxOrNull() ?: 0f
        qTable[state][action] = oldQ + learningRate * (reward + discountFactor * maxFutureQ - oldQ)

        // 6. Epsilon Decay (gradually lock in the learned values to stop glitching over time)
        explorationRate = (explorationRate * 0.9999f).coerceAtLeast(0.001f)

        return currentBeta
    }

    // JSON serialization to save the AI's memory
    fun serialize(): String {
        val jsonArray = JSONArray()
        for (state in qTable) {
            val stateArray = JSONArray()
            for (value in state) stateArray.put(value.toDouble())
            jsonArray.put(stateArray)
        }
        val obj = JSONObject()
        obj.put("q", jsonArray)
        obj.put("e", explorationRate.toDouble())
        return obj.toString()
    }

    fun deserialize(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val jsonArray = obj.getJSONArray("q")
            for (i in 0 until 3) {
                val stateArray = jsonArray.getJSONArray(i)
                for (j in 0 until 3) {
                    qTable[i][j] = stateArray.getDouble(j).toFloat()
                }
            }
            explorationRate = obj.getDouble("e").toFloat()
        } catch (e: Exception) {}
    }
}
