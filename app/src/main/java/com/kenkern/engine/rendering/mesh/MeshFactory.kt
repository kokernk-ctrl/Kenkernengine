package com.kenkern.engine.rendering.mesh

import android.opengl.GLES30
import com.kenkern.engine.core.EngineConfig
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural builders for the built-in meshes (triangle, plane, cube,
 * sphere, cylinder, camera pyramid, grid, wire cube).
 *
 * All meshes share the interleaved position+color layout of [Mesh]. Because
 * the Phase 1 shader is unlit, builders bake subtle directional shading into
 * vertex colors so 3D shapes remain readable without lighting.
 */
object MeshFactory {

    // A fixed key-light direction used only for baked vertex shading.
    private val LIGHT_DIR = floatArrayOf(0.35f, 0.85f, 0.45f).let { v ->
        val len = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    fun triangle(): Mesh = triangle(1f, 0.72f, 0.85f, 1f)

    fun triangle(r: Float, g: Float, b: Float, a: Float): Mesh {
        val height = 1f
        val halfBase = 0.6f
        val vertices = floatArrayOf(
            //      x         y       z      r  g  b  a
            -halfBase, -height / 2f, 0f, r, g, b, a,
            halfBase, -height / 2f, 0f, r, g, b, a,
            0f, height / 2f, 0f, r, g, b, a
        )
        return Mesh("triangle", vertices, shortArrayOf(0, 1, 2))
    }

    /** Ground-aligned plane (XZ), facing +Y. */
    fun plane(): Mesh = plane(1f, 0.72f, 0.85f, 1f)

    fun plane(r: Float, g: Float, b: Float, a: Float): Mesh {
        val h = 0.5f
        val top = shadeForNormal(0f, 1f, 0f)
        val v = floatArrayOf(
            -h, 0f, -h, r * top, g * top, b * top, a,
            -h, 0f, h, r * top, g * top, b * top, a,
            h, 0f, h, r * top, g * top, b * top, a,
            h, 0f, -h, r * top, g * top, b * top, a
        )
        return Mesh("plane", v, shortArrayOf(0, 2, 1, 0, 3, 2))
    }

    fun cube(): Mesh = cube(0.86f, 0.88f, 0.92f, 1f)

    fun cube(r: Float, g: Float, b: Float, a: Float): Mesh {
        val h = 0.5f
        // Faces: +X, -X, +Y, -Y, +Z, -Z (CCW from outside).
        data class Face(val normal: Triple<Float, Float, Float>, val corners: Array<FloatArray>)

        val faces = listOf(
            Face(Triple(1f, 0f, 0f), arrayOf(floatArrayOf(h, -h, h), floatArrayOf(h, -h, -h), floatArrayOf(h, h, -h), floatArrayOf(h, h, h))),
            Face(Triple(-1f, 0f, 0f), arrayOf(floatArrayOf(-h, -h, -h), floatArrayOf(-h, -h, h), floatArrayOf(-h, h, h), floatArrayOf(-h, h, -h))),
            Face(Triple(0f, 1f, 0f), arrayOf(floatArrayOf(-h, h, h), floatArrayOf(h, h, h), floatArrayOf(h, h, -h), floatArrayOf(-h, h, -h))),
            Face(Triple(0f, -1f, 0f), arrayOf(floatArrayOf(-h, -h, -h), floatArrayOf(h, -h, -h), floatArrayOf(h, -h, h), floatArrayOf(-h, -h, h))),
            Face(Triple(0f, 0f, 1f), arrayOf(floatArrayOf(-h, -h, h), floatArrayOf(h, -h, h), floatArrayOf(h, h, h), floatArrayOf(-h, h, h))),
            Face(Triple(0f, 0f, -1f), arrayOf(floatArrayOf(h, -h, -h), floatArrayOf(-h, -h, -h), floatArrayOf(-h, h, -h), floatArrayOf(h, h, -h)))
        )

        val vertexCount = faces.size * 4
        val vertices = FloatArray(vertexCount * 7)
        val indices = ShortArray(faces.size * 6)
        var vi = 0
        var ii = 0
        faces.forEachIndexed { faceIndex, face ->
            val shade = shadeForNormal(face.normal.first, face.normal.second, face.normal.third)
            for (corner in face.corners) {
                vertices[vi++] = corner[0]
                vertices[vi++] = corner[1]
                vertices[vi++] = corner[2]
                vertices[vi++] = r * shade
                vertices[vi++] = g * shade
                vertices[vi++] = b * shade
                vertices[vi++] = a
            }
            val base = (faceIndex * 4).toShort()
            indices[ii++] = base
            indices[ii++] = (base + 1).toShort()
            indices[ii++] = (base + 2).toShort()
            indices[ii++] = base
            indices[ii++] = (base + 2).toShort()
            indices[ii++] = (base + 3).toShort()
        }
        return Mesh("cube", vertices, indices)
    }

    /** UV sphere, equator-aligned. */
    fun sphere(r: Float, g: Float, b: Float, a: Float = 1f, stacks: Int = 14, slices: Int = 20): Mesh {
        val vertices = ArrayList<Float>((stacks + 1) * (slices + 1) * 7)
        val indices = ArrayList<Short>(stacks * slices * 6)
        for (stack in 0..stacks) {
            val phi = Math.PI * stack / stacks
            val y = cos(phi).toFloat()
            val ringRadius = sin(phi).toFloat()
            for (slice in 0..slices) {
                val theta = 2.0 * Math.PI * slice / slices
                val x = (ringRadius * cos(theta)).toFloat()
                val z = (ringRadius * sin(theta)).toFloat()
                val shade = shadeForNormal(x, y, z)
                vertices.apply {
                    add(x * 0.5f); add(y * 0.5f); add(z * 0.5f)
                    add(r * shade); add(g * shade); add(b * shade); add(a)
                }
            }
        }
        for (stack in 0 until stacks) {
            for (slice in 0 until slices) {
                val row0 = stack * (slices + 1)
                val row1 = (stack + 1) * (slices + 1)
                val i0 = (row0 + slice).toShort()
                val i1 = (row0 + slice + 1).toShort()
                val i2 = (row1 + slice).toShort()
                val i3 = (row1 + slice + 1).toShort()
                indices.addAll(listOf(i0, i2, i1, i1, i2, i3))
            }
        }
        return Mesh("sphere", vertices.toFloatArray(), indices.toShortArray())
    }

    /** Cylinder along the Y axis with caps. */
    fun cylinder(r: Float, g: Float, b: Float, a: Float = 1f, segments: Int = 24): Mesh {
        val vertices = ArrayList<Float>(segments * 6 * 7 + segments * 2 * 7)
        val indices = ArrayList<Short>(segments * 12)

        // Side ring vertices.
        for (i in 0..segments) {
            val theta = 2.0 * Math.PI * i / segments
            val x = cos(theta).toFloat()
            val z = sin(theta).toFloat()
            val shadeSide = shadeForNormal(x, 0f, z)
            vertices.apply {
                add(x * 0.5f); add(-0.5f); add(z * 0.5f)
                add(r * shadeSide); add(g * shadeSide); add(b * shadeSide); add(a)
                add(x * 0.5f); add(0.5f); add(z * 0.5f)
                add(r * shadeSide); add(g * shadeSide); add(b * shadeSide); add(a)
            }
        }
        for (i in 0 until segments) {
            val base = (i * 2).toShort()
            indices.addAll(listOf(base, (base + 1).toShort(), (base + 2).toShort(), (base + 2).toShort(), (base + 1).toShort(), (base + 3).toShort()))
        }

        // Caps: fan around center vertices.
        val topCenterIndex = (vertices.size / 7).toShort()
        vertices.apply {
            add(0f); add(0.5f); add(0f)
            val topShade = shadeForNormal(0f, 1f, 0f)
            add(r * topShade); add(g * topShade); add(b * topShade); add(a)
        }
        val topRingStart = vertices.size / 7
        for (i in 0..segments) {
            val theta = 2.0 * Math.PI * i / segments
            val x = cos(theta).toFloat()
            val z = sin(theta).toFloat()
            val shade = shadeForNormal(0f, 1f, 0f)
            vertices.apply {
                add(x * 0.5f); add(0.5f); add(z * 0.5f)
                add(r * shade); add(g * shade); add(b * shade); add(a)
            }
        }
        val bottomCenterIndex = (vertices.size / 7).toShort()
        vertices.apply {
            add(0f); add(-0.5f); add(0f)
            val bottomShade = shadeForNormal(0f, -1f, 0f)
            add(r * bottomShade); add(g * bottomShade); add(b * bottomShade); add(a)
        }
        val bottomRingStart = vertices.size / 7
        for (i in 0..segments) {
            val theta = 2.0 * Math.PI * i / segments
            val x = cos(theta).toFloat()
            val z = sin(theta).toFloat()
            val shade = shadeForNormal(0f, -1f, 0f)
            vertices.apply {
                add(x * 0.5f); add(-0.5f); add(z * 0.5f)
                add(r * shade); add(g * shade); add(b * shade); add(a)
            }
        }
        for (i in 0 until segments) {
            val t0 = (topRingStart + i).toShort()
            indices.addAll(listOf(topCenterIndex, t0, (t0 + 1).toShort()))
            val b0 = (bottomRingStart + i).toShort()
            indices.addAll(listOf(bottomCenterIndex, (b0 + 1).toShort(), b0))
        }

        return Mesh("cylinder", vertices.toFloatArray(), indices.toShortArray())
    }

    /** Camera/light gizmo: pyramid pointing towards -Z. */
    fun pyramid(r: Float, g: Float, b: Float, a: Float = 1f): Mesh {
        val apexZ = -0.55f
        val baseZ = 0.25f
        val h = 0.3f

        val base0 = floatArrayOf(-h, -h, baseZ)
        val base1 = floatArrayOf(h, -h, baseZ)
        val base2 = floatArrayOf(h, h, baseZ)
        val base3 = floatArrayOf(-h, h, baseZ)
        val apex = floatArrayOf(0f, 0f, apexZ)

        // Side faces (triangle fan around apex) with baked shading.
        val sideIndices = listOf(
            shortArrayOf(0, 1, 4), shortArrayOf(1, 2, 4),
            shortArrayOf(2, 3, 4), shortArrayOf(3, 0, 4)
        )
        val sideShade = shadeForNormal(0.5f, 0.5f, 0.7f)

        val vertices = ArrayList<Float>(9 * 7)
        // 0: apex, 1..4: base corners (sides)
        listOf(apex, base0, base1, base2, base3).forEach { p ->
            vertices.addAll(listOf(p[0], p[1], p[2], r * sideShade, g * sideShade, b * sideShade, a))
        }
        // 5..8: base corners again for the darker back quad
        listOf(base0, base1, base2, base3).forEach { p ->
            vertices.addAll(listOf(p[0], p[1], p[2], r * 0.45f, g * 0.45f, b * 0.45f, a))
        }

        val indices = ArrayList<Short>()
        sideIndices.forEach { f -> indices.addAll(f.toList()) }
        // Back quad (two triangles), wound to face +Z.
        indices.addAll(listOf(5, 7, 6, 5, 8, 7))

        return Mesh("pyramid", vertices.toFloatArray(), indices.toShortArray())
    }

    // ------------------------------------------------------------------
    // Grid & outlines
    // ------------------------------------------------------------------

    /** XZ reference grid with faded minor lines and colored axis lines. */
    fun grid(): Mesh {
        val extent = EngineConfig.GRID_EXTENT
        val step = EngineConfig.GRID_STEP
        val minor = floatArrayOf(0.45f, 0.47f, 0.52f, 0.28f)
        val major = floatArrayOf(0.55f, 0.58f, 0.62f, 0.45f)
        val axisX = floatArrayOf(0.85f, 0.30f, 0.30f, 0.9f)
        val axisZ = floatArrayOf(0.30f, 0.55f, 0.90f, 0.9f)
        val y = 0f

        val vertices = ArrayList<Float>()
        val indices = ArrayList<Short>()
        var index: Short = 0

        fun line(x0: Float, z0: Float, x1: Float, z1: Float, color: FloatArray) {
            vertices.addAll(listOf(x0, y, z0, color[0], color[1], color[2], color[3]))
            vertices.addAll(listOf(x1, y, z1, color[0], color[1], color[2], color[3]))
            indices.add(index)
            indices.add((index + 1).toShort())
            index = (index + 2).toShort()
        }

        val range = -extent..extent
        for (i in range) {
            val pos = (i * step).toFloat()
            val isMajor = i % 5 == 0
            val color = if (isMajor) major else minor
            if (i != 0) {
                line(pos, -extent.toFloat(), pos, extent.toFloat(), color) // Z-direction lines
                line(-extent.toFloat(), pos, extent.toFloat(), pos, color) // X-direction lines
            }
        }
        line(0f, -extent.toFloat(), 0f, extent.toFloat(), axisZ)
        line(-extent.toFloat(), 0f, extent.toFloat(), 0f, axisX)

        return Mesh("grid", vertices.toFloatArray(), indices.toShortArray(), GLES30.GL_LINES)
    }

    /** Unit cube outline used as the selection highlight. */
    fun wireCube(): Mesh {
        val h = 0.5f
        val color = floatArrayOf(0f, 0.482f, 1f, 1f)
        val corners = arrayOf(
            floatArrayOf(-h, -h, -h), floatArrayOf(h, -h, -h),
            floatArrayOf(h, h, -h), floatArrayOf(-h, h, -h),
            floatArrayOf(-h, -h, h), floatArrayOf(h, -h, h),
            floatArrayOf(h, h, h), floatArrayOf(-h, h, h)
        )
        val vertices = FloatArray(8 * 7)
        corners.forEachIndexed { i, c ->
            vertices[i * 7 + 0] = c[0]
            vertices[i * 7 + 1] = c[1]
            vertices[i * 7 + 2] = c[2]
            vertices[i * 7 + 3] = color[0]
            vertices[i * 7 + 4] = color[1]
            vertices[i * 7 + 5] = color[2]
            vertices[i * 7 + 6] = color[3]
        }
        val edges = shortArrayOf(
            0, 1, 1, 2, 2, 3, 3, 0,
            4, 5, 5, 6, 6, 7, 7, 4,
            0, 4, 1, 5, 2, 6, 3, 7
        )
        return Mesh("wireCube", vertices, edges, GLES30.GL_LINES)
    }

    // ------------------------------------------------------------------

    private fun shadeForNormal(nx: Float, ny: Float, nz: Float): Float {
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
        if (len < 1e-6f) return 1f
        val dot = (nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1] + nz * LIGHT_DIR[2]) / len
        // Ambient + diffuse, biased up so dark sides stay readable on #0E0E10.
        return (0.52f + 0.48f * kotlin.math.max(0f, dot)).coerceIn(0.35f, 1f)
    }
}
