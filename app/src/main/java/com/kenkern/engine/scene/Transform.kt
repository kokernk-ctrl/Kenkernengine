package com.kenkern.engine.scene

import com.kenkern.engine.math.Matrix4
import com.kenkern.engine.math.Vector3
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/**
 * TRS transform of a Nink, relative to its parent.
 *
 * Rotation uses KENKERN's single Euler convention:
 * R = Rz(z) * Ry(y) * Rx(x)  (X applied first, then Y, then Z),
 * stored in radians. The Inspector displays degrees.
 */
class Transform(
    val position: Vector3 = Vector3(),
    val rotationEulerRadians: Vector3 = Vector3(),
    val scale: Vector3 = Vector3(1f, 1f, 1f)
) {

    /** Composes the local matrix: T * R * S. */
    fun toMatrix(out: Matrix4): Matrix4 = Matrix4.trs(
        out,
        position.x, position.y, position.z,
        rotationEulerRadians.x, rotationEulerRadians.y, rotationEulerRadians.z,
        scale.x, scale.y, scale.z
    )

    fun copyFrom(other: Transform) {
        position.set(other.position)
        rotationEulerRadians.set(other.rotationEulerRadians)
        scale.set(other.scale)
    }

    fun copy(): Transform {
        val t = Transform()
        t.copyFrom(this)
        return t
    }

    /**
     * Decomposes a world matrix (T * Rz*Ry*Rx * S with positive shearing-free
     * composition) back into position/rotation/scale. Used to preserve the
     * world transform when a Nink is reparented.
     */
    fun decomposeFrom(matrix: Matrix4) {
        val v = matrix.values
        position.set(v[12], v[13], v[14])

        var sx = Vector3(v[0], v[1], v[2]).length()
        val sy = Vector3(v[4], v[5], v[6]).length()
        var sz = Vector3(v[8], v[9], v[10]).length()
        if (sx < EPSILON) sx = EPSILON
        if (sz < EPSILON) sz = EPSILON

        // Rotation matrix columns, normalized.
        var c0x = v[0] / sx; var c0y = v[1] / sx; var c0z = v[2] / sx
        var c1x = v[4] / sy; var c1y = v[5] / sy; var c1z = v[6] / sy
        var c2x = v[8] / sz; var c2y = v[9] / sz; var c2z = v[10] / sz

        // Detect mirrored basis (negative determinant): flip X axis sign and
        // encode the flip in the X scale instead of the rotation.
        val det = c0x * (c1y * c2z - c1z * c2y) -
            c0y * (c1x * c2z - c1z * c2x) +
            c0z * (c1x * c2y - c1y * c2x)
        if (det < 0f) {
            sx = -sx
            c0x = -c0x; c0y = -c0y; c0z = -c0z
        }

        // R = Rz(z) * Ry(y) * Rx(x)
        // r[2][0] = -sin(y)  -> column-major index: values[0*4 + 2] = c0z
        val sy2 = (-c0z).coerceIn(-1f, 1f)
        val ry = asin(sy2)
        val ryCos = kotlin.math.cos(ry)
        val rx: Float
        val rz: Float
        if (abs(ryCos) > 1e-6f) {
            // r[2][1] = cos(y)*sin(x) -> c1z ; r[2][2] = cos(y)*cos(x) -> c2z
            rx = atan2(c1z, c2z)
            // r[1][0] = sin(z)*cos(y) -> c0y ; r[0][0] = cos(z)*cos(y) -> c0x
            rz = atan2(c0y, c0x)
        } else {
            // Gimbal lock at |y| = 90°: fall back to z = 0.
            rz = 0f
            // With z = 0: r[1][1] = cos(x) and r[1][2] = -sin(x).
            rx = atan2(-c2y, c1y)
        }
        rotationEulerRadians.set(rx, ry, rz)
        scale.set(sx, sy, sz)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transform) return false
        return position == other.position &&
            rotationEulerRadians == other.rotationEulerRadians &&
            scale == other.scale
    }

    override fun hashCode(): Int {
        var result = position.hashCode()
        result = 31 * result + rotationEulerRadians.hashCode()
        result = 31 * result + scale.hashCode()
        return result
    }

    companion object {
        private const val EPSILON = 1e-6f
    }
}
