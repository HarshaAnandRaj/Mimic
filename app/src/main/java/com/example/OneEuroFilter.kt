package com.example

class OneEuroFloat(
    private var minCutoff: Float = 1.0f,
    private var beta: Float = 0.02f,
    private var dCutoff: Float = 1.0f
) {
    private var prevValue: Float? = null
    private var prevDerivative: Float = 0f

    fun reset() {
        prevValue = null
        prevDerivative = 0f
    }

    fun updateParams(minCutoff: Float, beta: Float, dCutoff: Float = 1.0f) {
        this.minCutoff = minCutoff
        this.beta = beta
        this.dCutoff = dCutoff
    }

    fun filter(value: Float, dt: Float): Float {
        val safeDt = dt.coerceAtLeast(1f / 120f)
        val prev = prevValue ?: run {
            prevValue = value
            return value
        }

        val derivative = (value - prev) / safeDt
        val ed = lowPass(derivative, prevDerivative, alpha(dCutoff, safeDt))
        prevDerivative = ed

        val cutoff = minCutoff + beta * kotlin.math.abs(ed)
        val result = lowPass(value, prev, alpha(cutoff, safeDt))
        prevValue = result
        return result
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lowPass(value: Float, previous: Float, alpha: Float): Float {
        return alpha * value + (1f - alpha) * previous
    }
}

class SmoothJointBank {
    private val filters = Array(33) { Array(3) { OneEuroFloat() } }
    
    // ADD THIS: Instantiate an AI agent for each joint
    private val rlAgents = Array(33) { FilterRlAgent(it) }
    private var lastTimeMs: Long = 0L

    fun smooth(id: Int, x: Float, y: Float, z: Float, confidence: Float, timestampMs: Long): FloatArray {
        if (id < 0 || id >= 33) return floatArrayOf(x, y, z)
        val dt = if (lastTimeMs == 0L) 1f / 30f else (timestampMs - lastTimeMs) / 1000f
        lastTimeMs = timestampMs

        val confidenceScale = confidence.coerceIn(0.05f, 1f)

        val minCutoff = when (id) {
            0, 11, 12, 23, 24 -> 0.8f      // Head, shoulders, hips: keep them smooth
            15, 16, 27, 28, 31, 32 -> 2.5f // Wrists, ankles, feet: make them highly responsive!
            else -> 1.5f
        }

        // ADD THIS: Apply RL Agent to Wrists and Ankles (ids 15, 16, 27, 28)
        val isDynamicJoint = id in listOf(15, 16, 27, 28)
        val dynamicBeta = if (isDynamicJoint) {
            val filteredX = filters[id][0].filter(x, dt) // Quick lookahead
            val filteredY = filters[id][1].filter(y, dt)
            // Ask the AI what the beta should be!
            rlAgents[id].getOptimalBeta(x, y, filteredX, filteredY, dt)
        } else {
            0.4f
        }

        for (axis in 0..2) {
            filters[id][axis].updateParams(
                // Don't punish the cutoff too harshly when blurry
                minCutoff = minCutoff * confidenceScale.coerceAtLeast(0.3f),
                // FIX: Increase beta from 0.04f to 0.4f to allow high-speed snaps!
                beta = dynamicBeta, 
                dCutoff = 1.0f
            )
        }

        return floatArrayOf(
            if (isDynamicJoint) filters[id][0].filter(x, dt) else filters[id][0].filter(x, dt),
            if (isDynamicJoint) filters[id][1].filter(y, dt) else filters[id][1].filter(y, dt),
            filters[id][2].filter(z, dt)
        )
    }

    // ADD THESE: Functions to save/load memory
    fun getAgentData(): String {
        val obj = org.json.JSONObject()
        listOf(15, 16, 27, 28).forEach { id -> obj.put(id.toString(), rlAgents[id].serialize()) }
        return obj.toString()
    }
    
    fun loadAgentData(jsonStr: String) {
        try {
            val obj = org.json.JSONObject(jsonStr)
            listOf(15, 16, 27, 28).forEach { id ->
                if (obj.has(id.toString())) rlAgents[id].deserialize(obj.getString(id.toString()))
            }
        } catch (e: Exception) {}
    }
}
