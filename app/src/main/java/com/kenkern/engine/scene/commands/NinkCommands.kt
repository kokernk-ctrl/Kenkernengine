package com.kenkern.engine.scene.commands

import com.kenkern.engine.scene.Nink
import com.kenkern.engine.scene.NinkId
import com.kenkern.engine.scene.NinkRegistry
import com.kenkern.engine.scene.Scene
import com.kenkern.engine.scene.SceneChangeReason
import com.kenkern.engine.scene.Transform

/**
 * Concrete scene commands (Phase 2 set).
 *
 * Every command stores everything needed to reverse itself. Notifications
 * are emitted through [Scene.notifyChanged] so the editor, viewport payload
 * and persistence stay synchronized from a single path.
 */
object NinkCommands {

    // ------------------------------------------------------------------
    // Add single nink
    // ------------------------------------------------------------------

    class AddNink(
        private val nink: Nink,
        private val parentId: NinkId?,
        private val index: Int = -1
    ) : SceneCommand {

        override val debugName: String = "AddNink(${nink.name})"

        override fun execute(scene: Scene) {
            scene.registry.add(nink, parentId, index)
            scene.notifyChanged(SceneChangeReason.STRUCTURE, nink.id)
        }

        override fun undo(scene: Scene) {
            scene.registry.removeSubtree(nink.id)
            scene.notifyChanged(SceneChangeReason.STRUCTURE, nink.id)
        }
    }

    // ------------------------------------------------------------------
    // Add a prepared subtree (duplicate, prefab instantiation)
    // ------------------------------------------------------------------

    class AddSubtree(
        private val ninksPreOrder: List<Nink>,
        private val attachParentId: NinkId?,
        private val attachIndex: Int
    ) : SceneCommand {

        override val debugName: String = "AddSubtree(${ninksPreOrder.firstOrNull()?.name ?: "?"})"

        private val subtreeRootId: NinkId = ninksPreOrder.first().id

        override fun execute(scene: Scene) {
            for (nink in ninksPreOrder) {
                val isRoot = nink.id == subtreeRootId
                val parent = if (isRoot) attachParentId else nink.parentId
                val index = if (isRoot) attachIndex else -1
                scene.registry.add(nink, parent, index)
            }
            scene.notifyChanged(SceneChangeReason.STRUCTURE, subtreeRootId)
        }

        override fun undo(scene: Scene) {
            scene.registry.removeSubtree(subtreeRootId)
            scene.notifyChanged(SceneChangeReason.STRUCTURE, subtreeRootId)
        }
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    class DeleteNink(private val targetId: NinkId) : SceneCommand {

        override val debugName: String = "DeleteNink($targetId)"

        private var removed: NinkRegistry.RemovedSubtree? = null
        private var wasMainCamera = false

        override fun execute(scene: Scene) {
            removed = scene.registry.removeSubtree(targetId)
            wasMainCamera = scene.mainCameraId != null && scene.registry.subtreeContains(targetId, scene.mainCameraId)
            if (wasMainCamera) scene.mainCameraId = null
            scene.notifyChanged(SceneChangeReason.STRUCTURE, targetId)
        }

        override fun undo(scene: Scene) {
            val removed = this.removed ?: return
            scene.registry.restoreSubtree(removed)
            if (wasMainCamera) scene.mainCameraId = targetId
            scene.notifyChanged(SceneChangeReason.STRUCTURE, targetId)
        }
    }

    // ------------------------------------------------------------------
    // Rename
    // ------------------------------------------------------------------

    class RenameNink(private val id: NinkId, private val newName: String) : SceneCommand {

        override val debugName: String = "RenameNink($id)"

        private var oldName: String = ""

        override fun execute(scene: Scene) {
            val nink = scene.registry.nink(id) ?: return
            oldName = nink.name
            nink.name = newName
            scene.notifyChanged(SceneChangeReason.STRUCTURE, id)
        }

        override fun undo(scene: Scene) {
            val nink = scene.registry.nink(id) ?: return
            nink.name = oldName
            scene.notifyChanged(SceneChangeReason.STRUCTURE, id)
        }
    }

    // ------------------------------------------------------------------
    // Transform (coalescable for continuous inspector edits)
    // ------------------------------------------------------------------

    class SetTransform(
        private val id: NinkId,
        oldTransform: Transform,
        newTransform: Transform
    ) : SceneCommand, CoalescingCommand {

        override val debugName: String = "SetTransform($id)"

        override val coalesceKey: String = "transform:$id"

        private val oldTransform: Transform = oldTransform.copy()
        private val newTransform: Transform = newTransform.copy()

        override fun mergeFrom(later: SceneCommand) {
            if (later is SetTransform) {
                this.newTransform.copyFrom(later.newTransform)
            }
        }

        override fun execute(scene: Scene) {
            val nink = scene.registry.nink(id) ?: return
            nink.transform.copyFrom(newTransform)
            scene.notifyChanged(SceneChangeReason.TRANSFORM, id)
        }

        override fun undo(scene: Scene) {
            val nink = scene.registry.nink(id) ?: return
            nink.transform.copyFrom(oldTransform)
            scene.notifyChanged(SceneChangeReason.TRANSFORM, id)
        }
    }

    // ------------------------------------------------------------------
    // Flags
    // ------------------------------------------------------------------

    class SetFlags(
        private val id: NinkId,
        private val active: Boolean? = null,
        private val visible: Boolean? = null,
        private val locked: Boolean? = null
    ) : SceneCommand {

        override val debugName: String = "SetFlags($id)"

        private var oldActive: Boolean = true
        private var oldVisible: Boolean = true
        private var oldLocked: Boolean = false

        override fun execute(scene: Scene) {
            val nink = scene.registry.nink(id) ?: return
            active?.let { oldActive = nink.active; nink.active = it }
            visible?.let { oldVisible = nink.visible; nink.visible = it }
            locked?.let { oldLocked = nink.locked; nink.locked = it }
            scene.notifyChanged(SceneChangeReason.FLAGS, id)
        }

        override fun undo(scene: Scene) {
            val nink = scene.registry.nink(id) ?: return
            active?.let { nink.active = oldActive }
            visible?.let { nink.visible = oldVisible }
            locked?.let { nink.locked = oldLocked }
            scene.notifyChanged(SceneChangeReason.FLAGS, id)
        }
    }

    // ------------------------------------------------------------------
    // Reparent (with optional world-transform preservation)
    // ------------------------------------------------------------------

    class ReparentNink(
        private val id: NinkId,
        private val newParentId: NinkId?,
        private val newIndex: Int,
        private val preserveWorld: Boolean
    ) : SceneCommand {

        override val debugName: String = "ReparentNink($id -> $newParentId)"

        private var oldParentId: NinkId? = null
        private var oldIndex: Int = -1
        private val oldLocal = Transform()
        private val newLocal = Transform()

        override fun execute(scene: Scene) {
            val registry = scene.registry
            val nink = registry.nink(id) ?: return
            oldParentId = nink.parentId
            oldIndex = registry.indexInParent(id)
            oldLocal.copyFrom(nink.transform)

            if (preserveWorld) {
                val scratch = com.kenkern.engine.math.Matrix4()
                val world = com.kenkern.engine.math.Matrix4()
                registry.worldMatrix(id, world, scratch)
                val parentWorld = com.kenkern.engine.math.Matrix4()
                if (newParentId != null) {
                    registry.worldMatrix(newParentId, parentWorld, scratch)
                    parentWorld.invert()
                }
                val combined = com.kenkern.engine.math.Matrix4()
                combined.multiply(parentWorld, world)
                newLocal.decomposeFrom(combined)
            } else {
                newLocal.copyFrom(nink.transform)
            }

            registry.reparent(id, newParentId, newIndex)
            nink.transform.copyFrom(newLocal)
            scene.notifyChanged(SceneChangeReason.STRUCTURE, id)
        }

        override fun undo(scene: Scene) {
            val registry = scene.registry
            val nink = registry.nink(id) ?: return
            registry.reparent(id, oldParentId, oldIndex)
            nink.transform.copyFrom(oldLocal)
            scene.notifyChanged(SceneChangeReason.STRUCTURE, id)
        }
    }

    // ------------------------------------------------------------------
    // Reorder within parent
    // ------------------------------------------------------------------

    class ReorderNink(private val id: NinkId, private val toIndex: Int) : SceneCommand {

        override val debugName: String = "ReorderNink($id -> $toIndex)"

        private var oldIndex: Int = -1

        override fun execute(scene: Scene) {
            val registry = scene.registry
            oldIndex = registry.indexInParent(id)
            registry.reorderWithinParent(id, toIndex)
            scene.notifyChanged(SceneChangeReason.STRUCTURE, id)
        }

        override fun undo(scene: Scene) {
            if (oldIndex >= 0) {
                scene.registry.reorderWithinParent(id, oldIndex)
                scene.notifyChanged(SceneChangeReason.STRUCTURE, id)
            }
        }
    }

    // ------------------------------------------------------------------
    // Active camera
    // ------------------------------------------------------------------

    class SetMainCamera(private val id: NinkId) : SceneCommand {

        override val debugName: String = "SetMainCamera($id)"

        private var oldCameraId: NinkId? = null

        override fun execute(scene: Scene) {
            oldCameraId = scene.mainCameraId
            scene.mainCameraId = id
            scene.notifyChanged(SceneChangeReason.FLAGS, id)
        }

        override fun undo(scene: Scene) {
            scene.mainCameraId = oldCameraId
            scene.notifyChanged(SceneChangeReason.FLAGS, id)
        }
    }

    // ------------------------------------------------------------------
    // Batch (ungroup = reparent children + delete group)
    // ------------------------------------------------------------------

    class Batch(private val commands: List<SceneCommand>) : SceneCommand {

        override val debugName: String = "Batch(${commands.size})"

        override fun execute(scene: Scene) {
            for (command in commands) command.execute(scene)
        }

        override fun undo(scene: Scene) {
            for (command in commands.reversed()) command.undo(scene)
        }
    }
}
