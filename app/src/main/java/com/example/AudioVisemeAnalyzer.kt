package com.example

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.log10

class AudioVisemeAnalyzer {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var thread: Thread? = null

    private var sampleRate = 16000
    private var bufferSize = 0

    init {
        val sampleRates = intArrayOf(48000, 44100, 16000)
        for (rate in sampleRates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf > 0) {
                sampleRate = rate
                bufferSize = minBuf * 2
                break
            }
        }
    }

    private val _visemeA = MutableStateFlow(0f)
    val visemeA: StateFlow<Float> = _visemeA

    private val _visemeO = MutableStateFlow(0f)
    val visemeO: StateFlow<Float> = _visemeO

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording || bufferSize <= 0) return
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRecording = true
                thread = Thread { audioLoop() }
                thread?.start()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            isRecording = false
        } catch (e: UnsupportedOperationException) {
            e.printStackTrace()
            isRecording = false
        } catch (e: Throwable) {
            e.printStackTrace()
            isRecording = false
        }
    }

    private fun audioLoop() {
        val buffer = ShortArray(bufferSize / 2)
        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                var sum = 0.0
                var crossings = 0
                for (i in 0 until read) {
                    sum += abs(buffer[i].toDouble())
                    if (i > 0) {
                        if ((buffer[i - 1] > 0 && buffer[i] < 0) || (buffer[i - 1] < 0 && buffer[i] > 0)) {
                            crossings++
                        }
                    }
                }
                val avg = sum / read
                
                // Zero Crossing Rate
                val zcr = crossings.toFloat() / read.toFloat()
                
                // Amplitude to DB
                val db = if (avg > 0) 20 * log10(avg) else 0.0
                
                // A higher amplitude opens the mouth more (JawOpen).
                var valA = ((db - 40) / 40).coerceIn(0.0, 1.0).toFloat()
                
                // O (MouthPucker) uses lower frequencies, meaning lower ZCR. 
                // We boost 'O' if amplitude is high but ZCR is low.
                var valO = 0f
                if (valA > 0.1f) {
                    // Typical ZCR for speech is 0.05 to 0.3. 
                    // Let's say ZCR < 0.1 is 'O' or 'U' (low freq formants), ZCR > 0.15 is 'A' or 'E'
                    valO = ((0.15f - zcr) / 0.1f).coerceIn(0.0f, 1.0f) * valA
                }
                
                _visemeA.value = valA
                _visemeO.value = valO
            }
        }
    }

    fun stop() {
        isRecording = false
        try {
            thread?.join(500)
        } catch (e: Exception) {}
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {}
    }
}
