package com.kenkern.engine.rendering

import com.kenkern.engine.rendering.mesh.MeshFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the GPU mesh data (HOTFIX forensic investigation).
 *
 * A mesh whose index buffer references vertices beyond the vertex count, or
 * whose interleaved layout does not match the shader attribute convention,
 * is undefined behavior in OpenGL ES — on low-end Mali/Unisoc drivers this
 * class of data error can terminate the process natively (SIGSEGV), which no
 * Java guard can catch. These tests validate the geometry data itself on the
 * JVM (mesh construction performs no GL calls).
 */
class MeshIntegrityTest {

    private fun assertMeshIntegrity(mesh: com.kenkern.engine.rendering.mesh.Mesh, name: String) {
        val vertices = mesh.vertexArrayForDiagnostics()
        val indices = mesh.indexArrayForDiagnostics()

        assertTrue("$name: must have vertices", vertices.isNotEmpty())
        assertTrue("$name: must have indices", indices.isNotEmpty())
        assertTrue(
            "$name: vertex buffer must be a multiple of the 7-float stride (pos3+col4)",
            vertices.size % 7 == 0
        )
        val vertexCount = vertices.size / 7
        val maxIndex = indices.maxOrNull() ?: -1
        val minIndex = indices.minOrNull() ?: 0
        assertTrue(
            "$name: max index $maxIndex exceeds vertex count $vertexCount (native-crash class)",
            maxIndex < vertexCount
        )
        assertTrue("$name: negative index $minIndex", minIndex >= 0)
        assertTrue(
            "$name: $vertexCount vertices must fit in 16-bit indices",
            vertexCount <= 65536
        )
    }

    @Test
    fun `triangle mesh is intact`() {
        assertMeshIntegrity(MeshFactory.triangle(), "triangle")
    }

    @Test
    fun `plane mesh is intact`() {
        assertMeshIntegrity(MeshFactory.plane(), "plane")
    }

    @Test
    fun `cube mesh is intact`() {
        assertMeshIntegrity(MeshFactory.cube(), "cube")
    }

    @Test
    fun `sphere mesh is intact`() {
        assertMeshIntegrity(MeshFactory.sphere(0f, 0.482f, 1f), "sphere")
    }

    @Test
    fun `cylinder mesh is intact`() {
        assertMeshIntegrity(MeshFactory.cylinder(0.78f, 0.80f, 0.84f), "cylinder")
    }

    @Test
    fun `pyramid mesh is intact`() {
        assertMeshIntegrity(MeshFactory.pyramid(0.95f, 0.80f, 0.20f), "pyramid")
    }

    @Test
    fun `grid mesh is intact`() {
        assertMeshIntegrity(MeshFactory.grid(), "grid")
    }

    @Test
    fun `wire cube mesh is intact`() {
        assertMeshIntegrity(MeshFactory.wireCube(), "wireCube")
    }

    @Test
    fun `all meshes built by the renderer stay within 16-bit index range`() {
        // Every mesh the renderer can build, exactly as GLRenderer.buildMeshes does.
        val all = listOf(
            MeshFactory.triangle(0f, 0.70f, 0.62f, 1f),
            MeshFactory.plane(),
            MeshFactory.cube(),
            MeshFactory.sphere(0f, 0.482f, 1f),
            MeshFactory.cylinder(0.78f, 0.80f, 0.84f),
            MeshFactory.pyramid(0.95f, 0.80f, 0.20f),
            MeshFactory.grid(),
            MeshFactory.wireCube()
        )
        all.forEachIndexed { i, mesh ->
            assertMeshIntegrity(mesh, "renderer-mesh#$i")
        }
    }
}
