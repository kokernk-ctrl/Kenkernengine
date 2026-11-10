package com.kenkern.engine.rendering

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression coverage for the scene-type → mesh mapping used by the viewport. */
class MeshKindTest {

    @Test
    fun `mesh types map to their dedicated mesh`() {
        assertEquals(MeshKind.TRIANGLE, MeshKind.fromNinkType("MESH_TRIANGLE"))
        assertEquals(MeshKind.PLANE, MeshKind.fromNinkType("MESH_PLANE"))
        assertEquals(MeshKind.CUBE, MeshKind.fromNinkType("MESH_CUBE"))
        assertEquals(MeshKind.SPHERE, MeshKind.fromNinkType("MESH_SPHERE"))
        assertEquals(MeshKind.CYLINDER, MeshKind.fromNinkType("MESH_CYLINDER"))
    }

    @Test
    fun `characters render as cubes and cameras and lights as pyramids`() {
        assertEquals(MeshKind.CUBE, MeshKind.fromNinkType("CHARACTER_PLAYER"))
        assertEquals(MeshKind.CUBE, MeshKind.fromNinkType("CHARACTER_NPC"))
        assertEquals(MeshKind.PYRAMID, MeshKind.fromNinkType("CAMERA"))
        assertEquals(MeshKind.PYRAMID, MeshKind.fromNinkType("LIGHT_DIRECTIONAL"))
        assertEquals(MeshKind.PYRAMID, MeshKind.fromNinkType("LIGHT_POINT"))
        assertEquals(MeshKind.PYRAMID, MeshKind.fromNinkType("LIGHT_SPOT"))
    }

    @Test
    fun `unknown types fall back to the cube gizmo`() {
        assertEquals(MeshKind.CUBE, MeshKind.fromNinkType("MESH_UNKNOWN_FUTURE"))
        assertEquals(MeshKind.CUBE, MeshKind.fromNinkType(""))
    }
}
