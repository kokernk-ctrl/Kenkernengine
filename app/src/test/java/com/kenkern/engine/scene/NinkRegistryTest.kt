package com.kenkern.engine.scene

import com.kenkern.engine.math.Matrix4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NinkRegistryTest {

    private fun makeScene(): Scene = Scene()

    @Test
    fun `add and children maintain consistent links`() {
        val scene = makeScene()
        val parent = Nink(NinkId.generate(), "Parent", NinkType.GROUP)
        val child = Nink(NinkId.generate(), "Child", NinkType.MESH_CUBE)
        scene.registry.add(parent, null)
        scene.registry.add(child, parent.id)

        assertEquals(listOf(child.id), scene.registry.childrenOf(parent.id))
        assertEquals(parent.id, child.parentId)
        assertEquals(2, scene.registry.ninkCount())
        assertEquals(1, scene.registry.rootIds().size)
    }

    @Test
    fun `duplicate id is rejected`() {
        val scene = makeScene()
        val nink = Nink(NinkId.generate(), "A", NinkType.EMPTY)
        scene.registry.add(nink, null)
        assertThrows(IllegalArgumentException::class.java) {
            scene.registry.add(Nink(nink.id, "B", NinkType.EMPTY), null)
        }
    }

    @Test
    fun `reparent into own subtree is rejected`() {
        val scene = makeScene()
        val root = Nink(NinkId.generate(), "Root", NinkType.GROUP)
        val child = Nink(NinkId.generate(), "Child", NinkType.GROUP)
        val grandchild = Nink(NinkId.generate(), "Grandchild", NinkType.EMPTY)
        scene.registry.add(root, null)
        scene.registry.add(child, root.id)
        scene.registry.add(grandchild, child.id)

        assertFalse(scene.registry.canReparent(root.id, child.id))
        assertFalse(scene.registry.canReparent(root.id, root.id))
        assertTrue(scene.registry.canReparent(grandchild.id, root.id))
    }

    @Test
    fun `reparent updates links both ways`() {
        val scene = makeScene()
        val a = Nink(NinkId.generate(), "A", NinkType.GROUP)
        val b = Nink(NinkId.generate(), "B", NinkType.GROUP)
        val n = Nink(NinkId.generate(), "N", NinkType.MESH_CUBE)
        scene.registry.add(a, null)
        scene.registry.add(b, null)
        scene.registry.add(n, a.id)

        scene.registry.reparent(n.id, b.id)
        assertFalse(scene.registry.childrenOf(a.id).contains(n.id))
        assertEquals(listOf(n.id), scene.registry.childrenOf(b.id))
        assertEquals(b.id, n.parentId)
    }

    @Test
    fun `removeSubtree removes descendants and restoreSubtree restores them`() {
        val scene = makeScene()
        val group = Nink(NinkId.generate(), "Group", NinkType.GROUP)
        val childA = Nink(NinkId.generate(), "A", NinkType.MESH_CUBE)
        val childB = Nink(NinkId.generate(), "B", NinkType.MESH_SPHERE)
        scene.registry.add(group, null)
        scene.registry.add(childA, group.id)
        scene.registry.add(childB, group.id)

        val removed = scene.registry.removeSubtree(group.id)!!
        assertEquals(3, removed.ninksPreOrder.size)
        assertEquals(0, scene.registry.ninkCount())
        assertTrue(scene.registry.rootIds().isEmpty())

        scene.registry.restoreSubtree(removed)
        assertEquals(3, scene.registry.ninkCount())
        assertEquals(listOf(group.id), scene.registry.rootIds())
        assertEquals(listOf(childA.id, childB.id), scene.registry.childrenOf(group.id))
    }

    @Test
    fun `worldMatrix composes parent chain`() {
        val scene = makeScene()
        val parent = Nink(NinkId.generate(), "P", NinkType.EMPTY)
        parent.transform.position.set(10f, 0f, 0f)
        val child = Nink(NinkId.generate(), "C", NinkType.EMPTY)
        child.transform.position.set(0f, 5f, 0f)
        scene.registry.add(parent, null)
        scene.registry.add(child, parent.id)

        val world = Matrix4()
        assertTrue(scene.registry.worldMatrix(child.id, world, Matrix4()))
        assertEquals(10f, world.values[12], 1e-5f)
        assertEquals(5f, world.values[13], 1e-5f)
        assertTrue(scene.registry.worldMatrix(NinkId.generate(), world, Matrix4()).not())
    }

    @Test
    fun `reorderWithinParent moves index`() {
        val scene = makeScene()
        val parent = Nink(NinkId.generate(), "P", NinkType.GROUP)
        val a = Nink(NinkId.generate(), "A", NinkType.EMPTY)
        val b = Nink(NinkId.generate(), "B", NinkType.EMPTY)
        val c = Nink(NinkId.generate(), "C", NinkType.EMPTY)
        scene.registry.add(parent, null)
        scene.registry.add(a, parent.id)
        scene.registry.add(b, parent.id)
        scene.registry.add(c, parent.id)

        scene.registry.reorderWithinParent(c.id, 0)
        assertEquals(listOf(c.id, a.id, b.id), scene.registry.childrenOf(parent.id))
        assertEquals(0, scene.registry.indexInParent(c.id))
    }

    @Test
    fun `depthOf reports nesting depth`() {
        val scene = makeScene()
        val root = Nink(NinkId.generate(), "R", NinkType.GROUP)
        val child = Nink(NinkId.generate(), "C", NinkType.GROUP)
        val grand = Nink(NinkId.generate(), "G", NinkType.EMPTY)
        scene.registry.add(root, null)
        scene.registry.add(child, root.id)
        scene.registry.add(grand, child.id)
        assertEquals(0, scene.registry.depthOf(root.id))
        assertEquals(2, scene.registry.depthOf(grand.id))
        assertEquals(-1, scene.registry.depthOf(NinkId.generate()))
    }
}

class TransformTest {

    @Test
    fun `decompose roundtrip recovers transform`() {
        val original = Transform()
        original.position.set(1.5f, -2f, 3.25f)
        original.rotationEulerRadians.set(0.3f, -0.6f, 1.1f)
        original.scale.set(1.2f, 0.8f, 2.0f)

        val matrix = Matrix4()
        original.toMatrix(matrix)
        val decoded = Transform()
        decoded.decomposeFrom(matrix)

        val rematrix = Matrix4()
        decoded.toMatrix(rematrix)
        for (i in 0 until 16) {
            assertEquals(matrix.values[i], rematrix.values[i], 1e-4f)
        }
    }

    @Test
    fun `decompose identity stays identity`() {
        val t = Transform()
        t.decomposeFrom(Matrix4())
        assertEquals(0f, t.position.x, 1e-6f)
        assertEquals(1f, t.scale.x, 1e-6f)
        assertEquals(1f, t.scale.z, 1e-6f)
        assertEquals(0f, t.rotationEulerRadians.x, 1e-6f)
    }

    @Test
    fun `decompose negative scale flips sign into scale`() {
        val source = Transform()
        source.scale.set(2f, 2f, -2f)
        val matrix = Matrix4()
        source.toMatrix(matrix)
        val decoded = Transform()
        decoded.decomposeFrom(matrix)
        val rematrix = Matrix4()
        decoded.toMatrix(rematrix)
        for (i in 0 until 16) {
            assertEquals(matrix.values[i], rematrix.values[i], 1e-4f)
        }
    }
}

class SceneOpsTest {

    @Test
    fun `uniqueName appends counters when base exists`() {
        val scene = Scene()
        val parent = Nink(NinkId.generate(), "G", NinkType.GROUP)
        scene.registry.add(parent, null)
        scene.registry.add(Nink(NinkId.generate(), "Cube", NinkType.MESH_CUBE), parent.id)
        scene.registry.add(Nink(NinkId.generate(), "Cube 2", NinkType.MESH_CUBE), parent.id)

        assertEquals("Cube 3", SceneOps.uniqueName(scene, parent.id, "Cube"))
        assertEquals("Sphere", SceneOps.uniqueName(scene, parent.id, "Sphere"))
    }

    @Test
    fun `copySubtree creates fresh ids and preserves structure`() {
        val scene = Scene()
        val group = Nink(NinkId.generate(), "G", NinkType.GROUP)
        val child = Nink(NinkId.generate(), "C", NinkType.MESH_CUBE)
        scene.registry.add(group, null)
        scene.registry.add(child, group.id)

        val copies = SceneOps.copySubtree(scene, group.id)!!
        assertEquals(2, copies.size)
        val copyRoot = copies.first()
        assertNull(copyRoot.parentId)
        assertFalse(copyRoot.id == group.id)
        // Child copy links to the copied root (childIds are rebuilt by the
        // registry when the subtree is attached).
        assertEquals(copyRoot.id, copies[1].parentId)
        assertFalse(copies[1].id == child.id)
        assertFalse(copies[1].parentId == group.id)
    }
}
