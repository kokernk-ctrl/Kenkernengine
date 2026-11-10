package com.kenkern.engine.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.math.PI
import org.junit.Test

class Vector3Test {

    @Test
    fun `add subtract scale`() {
        val a = Vector3(1f, 2f, 3f)
        val b = Vector3(0.5f, 0.5f, 0.5f)
        a.add(b)
        assertEquals(1.5f, a.x, 1e-6f)
        assertEquals(2.5f, a.y, 1e-6f)
        assertEquals(3.5f, a.z, 1e-6f)
        a.subtract(b)
        assertEquals(1f, a.x, 1e-6f)
        a.scale(2f)
        assertEquals(2f, a.x, 1e-6f)
        assertEquals(4f, a.y, 1e-6f)
        assertEquals(6f, a.z, 1e-6f)
    }

    @Test
    fun `normalize handles zero vector`() {
        val v = Vector3()
        v.normalize()
        assertEquals(0f, v.x, 1e-6f)
        val w = Vector3(3f, 0f, 0f)
        w.normalize()
        assertEquals(1f, w.length(), 1e-6f)
    }

    @Test
    fun `dot cross length`() {
        val x = Vector3(1f, 0f, 0f)
        val y = Vector3(0f, 1f, 0f)
        assertEquals(0f, x.dot(y), 1e-6f)
        val z = x.copy().cross(y)
        assertEquals(0f, z.x, 1e-6f)
        assertEquals(0f, z.y, 1e-6f)
        assertEquals(1f, z.z, 1e-6f)
        assertEquals(1f, x.length(), 1e-6f)
    }

    @Test
    fun `angle between orthogonal vectors is 90 degrees`() {
        val x = Vector3(1f, 0f, 0f)
        val y = Vector3(0f, 1f, 0f)
        assertEquals(PI.toFloat() / 2f, x.angleTo(y), 1e-5f)
    }

    @Test
    fun `equality uses epsilon`() {
        assertTrue(Vector3(1f, 2f, 3f) == Vector3(1f + 1e-9f, 2f, 3f))
        assertFalse(Vector3(1f, 2f, 3f) == Vector3(1.1f, 2f, 3f))
    }
}

class Matrix4Test {

    @Test
    fun `identity multiply leaves matrix unchanged`() {
        val m = Matrix4()
        m.set(1, 2, 5f)
        val result = Matrix4().multiply(Matrix4(), m)
        assertEquals(5f, result[1, 2], 1e-6f)
        assertEquals(1f, result[0, 0], 1e-6f)
    }

    @Test
    fun `multiply is not commutative and scales correctly`() {
        val t = Matrix4()
        Matrix4.translation(t, 2f, 0f, 0f)
        val s = Matrix4()
        Matrix4.scaling(s, 3f, 3f, 3f)
        // T*S applied to a point scales first, then translates.
        val ts = Matrix4().multiply(t, s)
        val p = Vector3(1f, 0f, 0f)
        val out = Vector3()
        ts.transformPoint(p, out)
        assertEquals(5f, out.x, 1e-6f)
        // S*T means translate is applied first, then scale: (1,0,0) -> (3,0,0) -> (9,0,0).
        val st = Matrix4().multiply(s, t)
        st.transformPoint(p, out)
        assertEquals(9f, out.x, 1e-6f)
    }

    @Test
    fun `rotationY 90 degrees maps +X to -Z`() {
        val r = Matrix4()
        Matrix4.rotationY(r, (PI / 2).toFloat())
        val out = Vector3()
        r.transformPoint(Vector3(1f, 0f, 0f), out)
        assertEquals(0f, out.x, 1e-5f)
        assertEquals(-1f, out.z, 1e-5f)
    }

    @Test
    fun `perspective maps near plane to -1 and far plane to +1 NDC z`() {
        val p = Matrix4()
        Matrix4.perspective(p, (Math.PI / 4).toFloat(), 16f / 9f, 0.1f, 100f)
        val out = Vector3()
        // Clip z divided by clip w (-z_view) must be -1 at the near plane
        // and +1 at the far plane.
        p.transformPoint(Vector3(0f, 0f, -0.1f), out)
        assertEquals(-1f, out.z / 0.1f, 1e-4f)
        p.transformPoint(Vector3(0f, 0f, -100f), out)
        assertEquals(1f, out.z / 100f, 1e-3f)
    }

    @Test
    fun `lookAt camera at +Z looks towards origin`() {
        val view = Matrix4()
        Matrix4.lookAt(view, Vector3(0f, 0f, 5f), Vector3(0f, 0f, 0f), Vector3(0f, 1f, 0f))
        val out = Vector3()
        view.transformPoint(Vector3(0f, 0f, 0f), out)
        assertEquals(0f, out.x, 1e-5f)
        assertEquals(0f, out.y, 1e-5f)
        assertEquals(-5f, out.z, 1e-5f) // origin is 5 units in front of camera (-Z)
    }

    @Test
    fun `invert roundtrip recovers original`() {
        val m = Matrix4()
        Matrix4.trs(m, 1f, -2f, 3f, 0.3f, -0.8f, 1.2f, 2f, 0.5f, 1.5f)
        val inv = Matrix4()
        assertTrue(m.invert(inv))
        val back = Matrix4().multiply(m, inv)
        // m * inv = identity
        for (i in 0 until 16) {
            val expected = if (i % 5 == 0) 1f else 0f
            assertEquals(expected, back.values[i], 1e-4f)
        }
    }

    @Test
    fun `invert returns false for singular matrix`() {
        val singular = Matrix4()
        Matrix4.scaling(singular, 0f, 1f, 1f)
        val out = Matrix4()
        // out.invert(source): attempts to invert `singular` into `out`.
        assertFalse(out.invert(singular))
    }

    @Test
    fun `trs position lands in translation column`() {
        val m = Matrix4()
        Matrix4.trs(m, 4f, 5f, 6f, 0f, 0f, 0f, 1f, 1f, 1f)
        assertEquals(4f, m.values[12], 1e-6f)
        assertEquals(5f, m.values[13], 1e-6f)
        assertEquals(6f, m.values[14], 1e-6f)
    }

    @Test
    fun `trs euler convention rotates +X to +Y with z rotation`() {
        // R = Rz * Ry * Rx; pure z = 90° rotates +X to +Y.
        val m = Matrix4()
        Matrix4.trs(m, 0f, 0f, 0f, 0f, 0f, (PI / 2).toFloat(), 1f, 1f, 1f)
        val out = Vector3()
        m.transformPoint(Vector3(1f, 0f, 0f), out)
        assertEquals(0f, out.x, 1e-5f)
        assertEquals(1f, out.y, 1e-5f)
        assertEquals(0f, out.z, 1e-5f)
    }

    @Test
    fun `matrix multiply aliasing is safe`() {
        val a = Matrix4()
        Matrix4.rotationY(a, 0.5f)
        val b = Matrix4()
        Matrix4.translation(b, 1f, 2f, 3f)
        val reference = Matrix4().multiply(a, b)
        // a.multiply(a, b) must equal reference despite aliasing.
        a.multiply(a, b)
        for (i in 0 until 16) {
            assertEquals(reference.values[i], a.values[i], 1e-5f)
        }
    }
}
