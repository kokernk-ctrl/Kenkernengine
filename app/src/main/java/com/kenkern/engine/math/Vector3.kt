package com.kenkern.engine.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Mutable 3-component vector used across the math, rendering and scene
 * subsystems.
 *
 * Mutability is deliberate: per-frame rendering code reuses vector instances
 * to avoid garbage-collection pressure. Scene data copies values when a
 * snapshot is required.
 */
class Vector3(
    @JvmField var x: Float = 0f,
    @JvmField var y: Float = 0f,
    @JvmField var z: Float = 0f
) {

    fun set(x: Float, y: Float, z: Float): Vector3 {
        this.x = x; this.y = y; this.z = z
        return this
    }

    fun set(other: Vector3): Vector3 = set(other.x, other.y, other.z)

    fun setZero(): Vector3 = set(0f, 0f, 0f)

    fun add(other: Vector3): Vector3 = add(other.x, other.y, other.z)

    fun add(x: Float, y: Float, z: Float): Vector3 {
        this.x += x; this.y += y; this.z += z
        return this
    }

    fun subtract(other: Vector3): Vector3 = add(-other.x, -other.y, -other.z)

    fun scale(factor: Float): Vector3 {
        x *= factor; y *= factor; z *= factor
        return this
    }

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun lengthSquared(): Float = x * x + y * y + z * z

    fun normalize(): Vector3 {
        val len = length()
        if (len > EPSILON) {
            x /= len; y /= len; z /= len
        } else {
            setZero()
        }
        return this
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    /** this = this x [other]. */
    fun cross(other: Vector3): Vector3 {
        val nx = y * other.z - z * other.y
        val ny = z * other.x - x * other.z
        val nz = x * other.y - y * other.x
        return set(nx, ny, nz)
    }

    /** Angle between this and [other] in radians. Zero vectors yield 0. */
    fun angleTo(other: Vector3): Float {
        val denominator = sqrt(lengthSquared() * other.lengthSquared())
        if (denominator < EPSILON) return 0f
        val clamped = (dot(other) / denominator).coerceIn(-1f, 1f)
        return acos(clamped)
    }

    fun distanceTo(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun copy(): Vector3 = Vector3(x, y, z)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector3) return false
        return abs(x - other.x) < EPSILON &&
            abs(y - other.y) < EPSILON &&
            abs(z - other.z) < EPSILON
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    override fun toString(): String = "Vector3(x=$x, y=$y, z=$z)"

    companion object {
        const val EPSILON: Float = 1e-6f
    }
}
