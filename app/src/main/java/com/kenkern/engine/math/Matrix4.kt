package com.kenkern.engine.math

import kotlin.math.tan

/**
 * 4x4 column-major matrix (OpenGL layout, identical to what
 * `glUniformMatrix4fv` with `transpose=false` expects).
 *
 * Conventions:
 *  - column vectors: v' = M * v
 *  - `multiply(a, b)` computes this = a * b, i.e. b is applied to the
 *    vector first.
 *  - Euler rotations are applied in X, then Y, then Z order:
 *    R = Rz(z) * Ry(y) * Rx(x). All scene/serialization code must keep
 *    using this single convention.
 */
class Matrix4 {

    /** Column-major storage: element (row, col) lives at values[col * 4 + row]. */
    @JvmField
    val values: FloatArray = FloatArray(16)

    init {
        setIdentity()
    }

    fun setIdentity(): Matrix4 {
        values.fill(0f)
        values[0] = 1f; values[5] = 1f; values[10] = 1f; values[15] = 1f
        return this
    }

    fun set(other: Matrix4): Matrix4 {
        other.values.copyInto(values)
        return this
    }

    /** Copies raw column-major values (length 16) into this matrix. */
    fun set(rawValues: FloatArray): Matrix4 {
        rawValues.copyInto(values, 0, 0, 16)
        return this
    }

    operator fun get(row: Int, col: Int): Float = values[col * 4 + row]

    fun set(row: Int, col: Int, value: Float) {
        values[col * 4 + row] = value
    }

    /** this = a * b. [a] and [b] may be the same instances as `this`. */
    fun multiply(a: Matrix4, b: Matrix4): Matrix4 {
        val av = a.values
        val bv = b.values
        // Result is staged in a per-thread buffer so aliasing (a == this or
        // b == this) is safe and no per-frame allocations occur.
        val tmp = ThreadLocalScratch.get().m16
        for (col in 0 until 4) {
            val c0 = bv[col * 4 + 0]
            val c1 = bv[col * 4 + 1]
            val c2 = bv[col * 4 + 2]
            val c3 = bv[col * 4 + 3]
            tmp[col * 4 + 0] = av[0] * c0 + av[4] * c1 + av[8] * c2 + av[12] * c3
            tmp[col * 4 + 1] = av[1] * c0 + av[5] * c1 + av[9] * c2 + av[13] * c3
            tmp[col * 4 + 2] = av[2] * c0 + av[6] * c1 + av[10] * c2 + av[14] * c3
            tmp[col * 4 + 3] = av[3] * c0 + av[7] * c1 + av[11] * c2 + av[15] * c3
        }
        tmp.copyInto(values)
        return this
    }

    /**
     * General 4x4 inversion (adjugate method, standard gluInvertMatrix
     * algorithm). Returns false when the matrix is singular; `this` is then
     * left untouched.
     */
    fun invert(source: Matrix4 = this): Boolean {
        val m = source.values
        val inv = FloatArray(16)

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f) return false

        det = 1f / det
        for (i in 0 until 16) {
            inv[i] *= det
        }
        inv.copyInto(values)
        return true
    }

    /** Transforms a point (w = 1), writing the result into [out]. */
    fun transformPoint(source: Vector3, out: Vector3): Vector3 {
        val x = source.x
        val y = source.y
        val z = source.z
        out.x = values[0] * x + values[4] * y + values[8] * z + values[12]
        out.y = values[1] * x + values[5] * y + values[9] * z + values[13]
        out.z = values[2] * x + values[6] * y + values[10] * z + values[14]
        return out
    }

    fun copy(): Matrix4 = Matrix4().set(this)

    override fun toString(): String = buildString {
        append("Matrix4(")
        for (row in 0 until 4) {
            append("[")
            for (col in 0 until 4) {
                append("%.3f".format(get(row, col)))
                if (col < 3) append(", ")
            }
            append("]")
        }
        append(")")
    }

    companion object {

        /** this = out. Right-handed perspective projection. */
        fun perspective(out: Matrix4, fovYRadians: Float, aspect: Float, near: Float, far: Float): Matrix4 {
            out.setIdentity()
            val f = 1f / tan(fovYRadians / 2f)
            val range = near - far
            if (aspect == 0f || range == 0f || far == near) return out
            out.values.fill(0f)
            out.values[0] = f / aspect
            out.values[5] = f
            out.values[10] = (far + near) / range
            out.values[11] = -1f
            out.values[14] = 2f * far * near / range
            return out
        }

        /** Right-handed lookAt view matrix. */
        fun lookAt(out: Matrix4, eye: Vector3, target: Vector3, up: Vector3): Matrix4 {
            val scratch = ThreadLocalScratch.get()
            val forward = scratch.vec0.set(target).subtract(eye).normalize()
            val side = scratch.vec1.set(forward).cross(up).normalize()
            val realUp = scratch.vec2.set(side).cross(forward).normalize()

            out.setIdentity()
            out.values[0] = side.x
            out.values[4] = side.y
            out.values[8] = side.z
            out.values[1] = realUp.x
            out.values[5] = realUp.y
            out.values[9] = realUp.z
            out.values[2] = -forward.x
            out.values[6] = -forward.y
            out.values[10] = -forward.z
            out.values[12] = -side.dot(eye)
            out.values[13] = -realUp.dot(eye)
            out.values[14] = forward.dot(eye)
            return out
        }

        fun translation(out: Matrix4, x: Float, y: Float, z: Float): Matrix4 {
            out.setIdentity()
            out.values[12] = x
            out.values[13] = y
            out.values[14] = z
            return out
        }

        fun scaling(out: Matrix4, x: Float, y: Float, z: Float): Matrix4 {
            out.setIdentity()
            out.values[0] = x
            out.values[5] = y
            out.values[10] = z
            return out
        }

        fun rotationX(out: Matrix4, radians: Float): Matrix4 {
            out.setIdentity()
            val c = cosCompat(radians)
            val s = sinCompat(radians)
            out.values[5] = c; out.values[6] = s
            out.values[9] = -s; out.values[10] = c
            return out
        }

        fun rotationY(out: Matrix4, radians: Float): Matrix4 {
            out.setIdentity()
            val c = cosCompat(radians)
            val s = sinCompat(radians)
            out.values[0] = c; out.values[2] = -s
            out.values[8] = s; out.values[10] = c
            return out
        }

        fun rotationZ(out: Matrix4, radians: Float): Matrix4 {
            out.setIdentity()
            val c = cosCompat(radians)
            val s = sinCompat(radians)
            out.values[0] = c; out.values[1] = s
            out.values[4] = -s; out.values[5] = c
            return out
        }

        /**
         * out = T(p) * Rz(z) * Ry(y) * Rx(x) * S(s)
         * (KENKERN's single documented Euler convention.)
         *
         * Not intended for per-frame hot loops; rendering composes matrices
         * from prebuilt TRS data.
         */
        fun trs(
            out: Matrix4,
            px: Float, py: Float, pz: Float,
            rx: Float, ry: Float, rz: Float,
            sx: Float, sy: Float, sz: Float
        ): Matrix4 {
            val scratch = ThreadLocalScratch.get()
            translation(scratch.m0, px, py, pz)
            rotationZ(scratch.m1, rz)
            rotationY(scratch.m2, ry)
            rotationX(scratch.m3, rx)
            scaling(scratch.m4, sx, sy, sz)
            scratch.m5.multiply(scratch.m2, scratch.m3)   // Ry * Rx
            scratch.m6.multiply(scratch.m1, scratch.m5)   // Rz * Ry * Rx
            scratch.m7.multiply(scratch.m6, scratch.m4)   // R * S
            out.multiply(scratch.m0, scratch.m7)          // T * R * S
            return out
        }

        private fun cosCompat(r: Float): Float = kotlin.math.cos(r)
        private fun sinCompat(r: Float): Float = kotlin.math.sin(r)
    }
}

/**
 * Reusable scratch objects for matrix construction. Confined per thread so
 * the GL render thread and the main thread never contend.
 */
internal object ThreadLocalScratch {
    class Bundle {
        val vec0 = Vector3()
        val vec1 = Vector3()
        val vec2 = Vector3()
        val m0 = Matrix4()
        val m1 = Matrix4()
        val m2 = Matrix4()
        val m3 = Matrix4()
        val m4 = Matrix4()
        val m5 = Matrix4()
        val m6 = Matrix4()
        val m7 = Matrix4()
        val m16 = FloatArray(16)
    }

    private val threadLocal = ThreadLocal.withInitial { Bundle() }

    fun get(): Bundle = threadLocal.get()
}
