package com.kenkern.engine.scene

import com.kenkern.engine.math.Matrix4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for hierarchy-walk cycle safety (HOTFIX forensic
 * investigation).
 *
 * A corrupted parent/child cycle used to make `worldMatrix`, `depthOf`,
 * `subtreeContains` and the pre-order collectors loop or recurse forever —
 * freezing the main thread (ANR → kill) or overflowing the stack. The walks
 * are now bounded by visited sets; these tests inject cycles directly (as a
 * corrupted file / memory corruption would) and assert termination plus sane
 * results. All classes are pure Kotlin, so this runs on the JVM.
 */
class NinkRegistryCycleTest {

    private fun buildTree(): Triple<NinkRegistry, Nink, Nink> {
        val registry = NinkRegistry()
        val root = Nink(id = NinkId.generate(), name = "Root", type = NinkType.EMPTY)
        val child = Nink(id = NinkId.generate(), name = "Child", type = NinkType.MESH_CUBE)
        registry.add(root, null)
        registry.add(child, root.id)
        return Triple(registry, root, child)
    }

    @Test
    fun `worldMatrix terminates and truncates when the parent chain cycles`() {
        val (registry, root, child) = buildTree()
        // Corrupt: make the child's parent point at the child itself.
        child.parentId = child.id

        val out = Matrix4()
        val scratch = Matrix4()
        val result = registry.worldMatrix(child.id, out, scratch)
        assertTrue("worldMatrix should still report success", result)
        // The truncated chain contains exactly one node (the corrupted child).
        val expected = Matrix4()
        child.transform.toMatrix(expected)
        assertEquals(expected.values.toList(), out.values.toList())
    }

    @Test
    fun `worldMatrix terminates on a two-node parent cycle`() {
        val (registry, root, child) = buildTree()
        // Corrupt: root claims the child as its parent while the child claims root.
        root.parentId = child.id

        val out = Matrix4()
        val scratch = Matrix4()
        assertTrue(registry.worldMatrix(root.id, out, scratch))
        assertTrue(registry.worldMatrix(child.id, out, scratch))
    }

    @Test
    fun `depthOf terminates when the parent chain cycles`() {
        val (registry, root, child) = buildTree()
        root.parentId = child.id
        val d1 = registry.depthOf(root.id)
        val d2 = registry.depthOf(child.id)
        assertTrue("depth must stay bounded in a cycle (got $d1)", d1 >= 0 && d1 < 10)
        assertTrue("depth must stay bounded in a cycle (got $d2)", d2 >= 0 && d2 < 10)
    }

    @Test
    fun `subtreeContains terminates on a child cycle`() {
        val (registry, root, child) = buildTree()
        // Corrupt childIds: root lists itself as a child.
        root.childIds.add(root.id)
        // Must terminate; result may be true (root is inside its own subtree
        // traversal) or false — the contract under test is termination.
        registry.subtreeContains(root.id, child.id)
        registry.subtreeContains(child.id, root.id)
    }

    @Test
    fun `subtreeContains still rejects reparenting into own subtree`() {
        val (registry, root, child) = buildTree()
        assertTrue(registry.subtreeContains(root.id, child.id))
        assertTrue(registry.subtreeContains(child.id, child.id))
        assertEquals(false, registry.subtreeContains(child.id, root.id))
        assertEquals(false, registry.subtreeContains(child.id, null))
    }

    @Test
    fun `removeSubtree terminates when childIds cycle`() {
        val (registry, root, child) = buildTree()
        root.childIds.add(root.id)
        val removed = registry.removeSubtree(root.id)
        assertTrue(removed != null)
        assertEquals(0, registry.ninkCount())
    }

    @Test
    fun `healthy hierarchy still yields the correct world matrix`() {
        val (registry, root, child) = buildTree()
        child.transform.position.set(1f, 2f, 3f)
        root.transform.position.set(10f, 20f, 30f)

        val out = Matrix4()
        val scratch = Matrix4()
        assertTrue(registry.worldMatrix(child.id, out, scratch))
        // World = T(root) * T(child): x = 11, y = 22, z = 33.
        assertEquals(11f, out.values[12], 1e-4f)
        assertEquals(22f, out.values[13], 1e-4f)
        assertEquals(33f, out.values[14], 1e-4f)
    }
}
