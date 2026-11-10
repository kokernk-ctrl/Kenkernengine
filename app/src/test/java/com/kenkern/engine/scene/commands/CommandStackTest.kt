package com.kenkern.engine.scene.commands

import com.kenkern.engine.math.Matrix4
import com.kenkern.engine.scene.Nink
import com.kenkern.engine.scene.NinkId
import com.kenkern.engine.scene.NinkType
import com.kenkern.engine.scene.Scene
import com.kenkern.engine.scene.Transform
import com.kenkern.engine.scene.commands.NinkCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandStackTest {

    private fun sceneWithGroup(): Pair<Scene, Nink> {
        val scene = Scene()
        val group = Nink(NinkId.generate(), "Group", NinkType.GROUP)
        scene.registry.add(group, null)
        return scene to group
    }

    @Test
    fun `rename is undoable and redoable`() {
        val (scene, group) = sceneWithGroup()
        val stack = CommandStack(scene)

        stack.execute(NinkCommands.RenameNink(group.id, "Renamed"))
        assertEquals("Renamed", group.name)

        stack.undo()
        assertEquals("Group", group.name)

        stack.redo()
        assertEquals("Renamed", group.name)
    }

    @Test
    fun `add then undo removes the nink`() {
        val (scene, group) = sceneWithGroup()
        val stack = CommandStack(scene)
        val cube = Nink(NinkId.generate(), "Cube", NinkType.MESH_CUBE)

        stack.execute(NinkCommands.AddNink(cube, group.id))
        assertTrue(scene.registry.contains(cube.id))

        stack.undo()
        assertFalse(scene.registry.contains(cube.id))
        assertEquals(listOf(group.id), scene.registry.rootIds())

        stack.redo()
        assertTrue(scene.registry.contains(cube.id))
        assertEquals(group.id, cube.parentId)
    }

    @Test
    fun `delete of subtree restores fully on undo`() {
        val scene = Scene()
        val group = Nink(NinkId.generate(), "Group", NinkType.GROUP)
        val child = Nink(NinkId.generate(), "Child", NinkType.MESH_CUBE)
        val grandchild = Nink(NinkId.generate(), "Grand", NinkType.EMPTY)
        scene.registry.add(group, null)
        scene.registry.add(child, group.id)
        scene.registry.add(grandchild, child.id)
        val stack = CommandStack(scene)

        stack.execute(NinkCommands.DeleteNink(group.id))
        assertEquals(0, scene.registry.ninkCount())

        stack.undo()
        assertEquals(3, scene.registry.ninkCount())
        assertEquals(listOf(group.id), scene.registry.rootIds())
        assertEquals(listOf(child.id), scene.registry.childrenOf(group.id))
        assertEquals(listOf(grandchild.id), scene.registry.childrenOf(child.id))
    }

    @Test
    fun `delete removes stale main camera reference and restores it`() {
        val scene = Scene()
        val camera = Nink(NinkId.generate(), "Cam", NinkType.CAMERA)
        scene.registry.add(camera, null)
        scene.mainCameraId = camera.id
        val stack = CommandStack(scene)

        stack.execute(NinkCommands.DeleteNink(camera.id))
        assertNull(scene.mainCameraId)
        stack.undo()
        assertEquals(camera.id, scene.mainCameraId)
    }

    @Test
    fun `setTransform undo restores previous transform`() {
        val (scene, group) = sceneWithGroup()
        val stack = CommandStack(scene)
        val old = Transform()
        old.position.set(1f, 1f, 1f)
        group.transform.copyFrom(old)

        val new = Transform()
        new.position.set(5f, 0f, -3f)
        stack.execute(NinkCommands.SetTransform(group.id, old, new))
        assertEquals(5f, group.transform.position.x, 1e-6f)

        stack.undo()
        assertEquals(1f, group.transform.position.x, 1e-6f)
    }

    @Test
    fun `continuous transform edits coalesce into one undo step`() {
        val (scene, group) = sceneWithGroup()
        val stack = CommandStack(scene)
        val first = Transform()
        first.position.set(1f, 0f, 0f)
        group.transform.copyFrom(first)

        var latest = first.copy()
        for (i in 2..5) {
            latest = latest.copy()
            latest.position.set(i.toFloat(), 0f, 0f)
            stack.execute(
                NinkCommands.SetTransform(group.id, group.transform, latest),
                coalesceKey = "transform:${group.id}"
            )
        }
        assertEquals(5f, group.transform.position.x, 1e-6f)
        assertTrue(stack.canUndo)

        stack.undo()
        assertEquals(1f, group.transform.position.x, 1e-6f)
        assertFalse(stack.canUndo)
    }

    @Test
    fun `reparent preserves world transform when requested`() {
        val scene = Scene()
        val source = Nink(NinkId.generate(), "Source", NinkType.GROUP)
        val target = Nink(NinkId.generate(), "Target", NinkType.GROUP)
        val cube = Nink(NinkId.generate(), "Cube", NinkType.MESH_CUBE)
        source.transform.position.set(10f, 5f, 0f)
        target.transform.position.set(-4f, 2f, 0f)
        cube.transform.position.set(1f, 1f, 0f)
        scene.registry.add(source, null)
        scene.registry.add(target, null)
        scene.registry.add(cube, source.id)

        // World position before: (11, 6, 0)
        val worldBefore = Matrix4()
        scene.registry.worldMatrix(cube.id, worldBefore, Matrix4())

        val stack = CommandStack(scene)
        stack.execute(NinkCommands.ReparentNink(cube.id, target.id, -1, preserveWorld = true))

        val worldAfter = Matrix4()
        scene.registry.worldMatrix(cube.id, worldAfter, Matrix4())
        assertEquals(worldBefore.values[12], worldAfter.values[12], 1e-4f)
        assertEquals(worldBefore.values[13], worldAfter.values[13], 1e-4f)
        assertEquals(worldBefore.values[14], worldAfter.values[14], 1e-4f)
        // New local position compensates the new parent offset.
        assertEquals(15f, cube.transform.position.x, 1e-4f)
        assertEquals(4f, cube.transform.position.y, 1e-4f)

        stack.undo()
        assertEquals(source.id, cube.parentId)
        assertEquals(1f, cube.transform.position.x, 1e-4f)
    }

    @Test
    fun `undo and redo on empty stacks are safe`() {
        val (scene, _) = sceneWithGroup()
        val stack = CommandStack(scene)
        assertNull(stack.undo())
        assertNull(stack.redo())
    }

    @Test
    fun `new command clears redo history`() {
        val (scene, group) = sceneWithGroup()
        val stack = CommandStack(scene)
        stack.execute(NinkCommands.RenameNink(group.id, "A"))
        stack.undo()
        assertTrue(stack.canRedo)
        stack.execute(NinkCommands.RenameNink(group.id, "B"))
        assertFalse(stack.canRedo)
    }

    @Test
    fun `ungroup moves children up and removes the group`() {
        val scene = Scene()
        val parent = Nink(NinkId.generate(), "Root", NinkType.GROUP)
        val group = Nink(NinkId.generate(), "Group", NinkType.GROUP)
        val childA = Nink(NinkId.generate(), "A", NinkType.MESH_CUBE)
        val childB = Nink(NinkId.generate(), "B", NinkType.MESH_SPHERE)
        scene.registry.add(parent, null)
        scene.registry.add(group, parent.id)
        scene.registry.add(childA, group.id)
        scene.registry.add(childB, group.id)
        val stack = CommandStack(scene)

        val batch = NinkCommands.Batch(
            listOf(
                NinkCommands.ReparentNink(childA.id, parent.id, 1, preserveWorld = true),
                NinkCommands.ReparentNink(childB.id, parent.id, 2, preserveWorld = true),
                NinkCommands.DeleteNink(group.id)
            )
        )
        stack.execute(batch)
        assertFalse(scene.registry.contains(group.id))
        assertEquals(listOf(childA.id, childB.id), scene.registry.childrenOf(parent.id))

        stack.undo()
        assertTrue(scene.registry.contains(group.id))
        assertEquals(listOf(childA.id, childB.id), scene.registry.childrenOf(group.id))
        assertEquals(listOf(group.id), scene.registry.childrenOf(parent.id))
    }

    @Test
    fun `setFlags undo restores flags`() {
        val (scene, group) = sceneWithGroup()
        val stack = CommandStack(scene)
        stack.execute(NinkCommands.SetFlags(group.id, visible = false, locked = true))
        assertFalse(group.visible)
        assertTrue(group.locked)
        stack.undo()
        assertTrue(group.visible)
        assertFalse(group.locked)
    }

    @Test
    fun `setMainCamera undo restores previous camera`() {
        val scene = Scene()
        val cam1 = Nink(NinkId.generate(), "Cam1", NinkType.CAMERA)
        val cam2 = Nink(NinkId.generate(), "Cam2", NinkType.CAMERA)
        scene.registry.add(cam1, null)
        scene.registry.add(cam2, null)
        scene.mainCameraId = cam1.id
        val stack = CommandStack(scene)

        stack.execute(NinkCommands.SetMainCamera(cam2.id))
        assertEquals(cam2.id, scene.mainCameraId)
        stack.undo()
        assertEquals(cam1.id, scene.mainCameraId)
    }
}
