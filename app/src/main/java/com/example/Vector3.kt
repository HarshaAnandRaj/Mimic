package com.example

import kotlin.math.sqrt

data class Vector3(var x: Float, var y: Float, var z: Float) {
    fun add(v: Vector3) = Vector3(x + v.x, y + v.y, z + v.z)
    fun sub(v: Vector3) = Vector3(x - v.x, y - v.y, z - v.z)
    fun mul(s: Float) = Vector3(x * s, y * s, z * s)
    fun dot(v: Vector3) = x * v.x + y * v.y + z * v.z
    fun cross(v: Vector3) = Vector3(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )
    fun length() = sqrt(x * x + y * y + z * z).toFloat()
    fun normalized(): Vector3 {
        val l = length()
        return if (l > 0.0001f) Vector3(x / l, y / l, z / l) else Vector3(0f, 0f, 0f)
    }
}
