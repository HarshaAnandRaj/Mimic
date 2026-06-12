package com.example

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Basic implementation of VMC (Virtual Motion Capture) protocol over OSC.
 */
class VmcOscSender(var targetIp: String = "192.16.16.29", var targetPort: Int = 39539) {
    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    
    init {
        try {
            socket = DatagramSocket()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAddress(): InetAddress? {
        if (address == null || address?.hostAddress != targetIp) {
            try {
                address = InetAddress.getByName(targetIp)
            } catch (ignored: Exception) {}
        }
        return address
    }

    fun sendBlendshapes(blendshapes: Map<String, Float>) {
        if (socket == null) return
        executor.submit {
            try {
                for ((name, value) in blendshapes) {
                    sendOscMessage("/VMC/Ext/Blend/Val", name, value)
                }
                sendOscMessage("/VMC/Ext/Blend/Apply")
            } catch (e: Exception) { }
        }
    }
    
    fun sendHeadPose(x: Float, y: Float, z: Float, qx: Float, qy: Float, qz: Float, qw: Float) {
        if (socket == null) return
        executor.submit {
            try {
                sendOscMessage("/VMC/Ext/Bone/Pos", "Head", x, y, z, qx, qy, qz, qw)
            } catch (e: Exception) { }
        }
    }

    private fun sendOscMessage(addressPattern: String, vararg args: Any) {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN)
        writeOscString(buffer, addressPattern)
        
        if (args.isEmpty()) {
            writeOscString(buffer, ",")
        } else {
            val typeTags = StringBuilder(",")
            for (arg in args) {
                when (arg) {
                    is String -> typeTags.append("s")
                    is Float -> typeTags.append("f")
                    is Int -> typeTags.append("i")
                }
            }
            writeOscString(buffer, typeTags.toString())
            for (arg in args) {
                when (arg) {
                    is String -> writeOscString(buffer, arg)
                    is Float -> buffer.putFloat(arg)
                    is Int -> buffer.putInt(arg)
                }
            }
        }
        
        val pos = buffer.position()
        val data = ByteArray(pos)
        buffer.flip()
        buffer.get(data)
        
        val addr = getAddress() ?: return
        val packet = DatagramPacket(data, data.size, addr, targetPort)
        socket?.send(packet)
    }

    private fun writeOscString(buffer: ByteBuffer, value: String) {
        val bytes = value.toByteArray()
        buffer.put(bytes)
        buffer.put(0.toByte())
        val padding = 4 - ((bytes.size + 1) % 4)
        if (padding < 4) {
             for (i in 0 until padding) {
                 buffer.put(0.toByte())
             }
        }
    }

    fun close() {
        try {
            executor.shutdownNow()
            socket?.close()
        } catch (e: Exception) {}
    }
}
