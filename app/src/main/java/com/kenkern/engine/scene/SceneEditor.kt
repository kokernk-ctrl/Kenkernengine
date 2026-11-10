package com.kenkern.engine.scene

import com.kenkern.engine.scene.commands.CommandStack
import com.kenkern.engine.scene.commands.NinkCommands

/**
 * Facade for all editor-driven scene mutations.
 *
 * Every operation validates, then delegates to the command stack so undo,
 * notifications and persistence behave uniformly. Panels never touch the
 * registry directly. Change notifications are emitted by the Scene itself
 * (single notification path); callers that need provenance (e.g. Inspector
 * live writes) wrap the calls and read the scene listener afterwards.
 */
class SceneEditor(
    private val scene: Scene,
    private val commandStack: CommandStack
) {

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    fun addNink(type: NinkType, parentId: NinkId?, baseName: String): NinkId {
        val name = SceneOps.uniqueName(scene, parentId, baseName)
        val nink = Nink(id = NinkId.generate(), name = name, type = type)
        attachDefaultComponents(nink)
        commandStack.execute(NinkCommands.AddNink(nink, parentId))
        return nink.id
    }

    fun addChildNink(type: NinkType, parentId: NinkId?, baseName: String): NinkId =
        addNink(type, parentId, baseName)

    fun duplicateNink(id: NinkId): NinkId? {
        val original = scene.registry.nink(id) ?: return null
        val copies = SceneOps.copySubtree(scene, id) ?: return null
        val parentId = original.parentId
        val index = scene.registry.indexInParent(id) + 1
        val newRootId = copies.first().id
        commandStack.execute(NinkCommands.AddSubtree(copies, parentId, index))
        return newRootId
    }

    /** Instantiates a prefab template (list of ninks, parents-first) under [parentId]. */
    fun instantiatePrefab(template: List<Nink>, parentId: NinkId?, baseName: String): NinkId? {
        if (template.isEmpty()) return null
        val idMap = HashMap<NinkId, NinkId>()
        for (nink in template) idMap[nink.id] = NinkId.generate()

        val fresh = template.map { nink ->
            Nink(
                id = idMap[nink.id]!!,
                name = if (nink.parentId == null) {
                    SceneOps.uniqueName(scene, parentId, baseName)
                } else {
                    nink.name
                },
                type = nink.type,
                transform = nink.transform.copy(),
                components = nink.components.map { it.copyOf() }.toMutableList(),
                parentId = nink.parentId?.let { idMap[it] },
                active = nink.active,
                visible = nink.visible,
                locked = false,
                customProperties = nink.customProperties.toMutableMap()
            )
        }
        val newRootId = fresh.first().id
        commandStack.execute(NinkCommands.AddSubtree(fresh, parentId, -1))
        return newRootId
    }

    // ------------------------------------------------------------------
    // Deletion / grouping
    // ------------------------------------------------------------------

    /** Deletes a nink (and its subtree). Locked ninks are rejected. */
    fun deleteNink(id: NinkId): Boolean {
        val nink = scene.registry.nink(id) ?: return false
        if (nink.locked) return false
        commandStack.execute(NinkCommands.DeleteNink(id))
        return true
    }

    /** Dissolves a group: children move to the group's parent, group is deleted. */
    fun ungroup(id: NinkId): Boolean {
        val nink = scene.registry.nink(id) ?: return false
        if (!nink.type.isGroup || nink.locked) return false
        val parentId = nink.parentId
        val groupIndex = scene.registry.indexInParent(id)
        val children = scene.registry.childrenOf(id)

        val commands = buildList {
            for ((i, childId) in children.withIndex()) {
                add(NinkCommands.ReparentNink(childId, parentId, groupIndex + i, true))
            }
            add(NinkCommands.DeleteNink(id))
        }
        commandStack.execute(NinkCommands.Batch(commands))
        return true
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    fun renameNink(id: NinkId, newName: String): Boolean {
        val name = newName.trim()
        if (name.isEmpty()) return false
        commandStack.execute(NinkCommands.RenameNink(id, name))
        return true
    }

    /** Reparents [id] under [newParentId], preserving world transform. */
    fun reparentNink(id: NinkId, newParentId: NinkId?, index: Int = -1): Boolean {
        if (!scene.registry.canReparent(id, newParentId)) return false
        commandStack.execute(NinkCommands.ReparentNink(id, newParentId, index, preserveWorld = true))
        return true
    }

    fun moveNinkWithinParent(id: NinkId, targetIndex: Int): Boolean {
        val nink = scene.registry.nink(id) ?: return false
        if (nink.locked) return false
        commandStack.execute(NinkCommands.ReorderNink(id, targetIndex))
        return true
    }

    fun moveUp(id: NinkId): Boolean {
        val index = scene.registry.indexInParent(id)
        if (index <= 0) return false
        return moveNinkWithinParent(id, index - 1)
    }

    fun moveDown(id: NinkId): Boolean {
        val nink = scene.registry.nink(id) ?: return false
        val index = scene.registry.indexInParent(id)
        if (index < 0 || index >= scene.registry.childCount(nink.parentId) - 1) return false
        return moveNinkWithinParent(id, index + 1)
    }

    // ------------------------------------------------------------------
    // Properties
    // ------------------------------------------------------------------

    /**
     * Undoable transform write (single edit, used by discrete UI actions).
     */
    fun setTransform(id: NinkId, transform: Transform) {
        val nink = scene.registry.nink(id) ?: return
        commandStack.execute(NinkCommands.SetTransform(id, nink.transform, transform))
    }

    /**
     * Continuous transform write from the Inspector (typing/dragging).
     * Applies directly and coalesces undo history: the first write in a
     * burst becomes the undo step; subsequent writes within the burst merge.
     */
    fun setTransformContinuous(id: NinkId, transform: Transform) {
        val nink = scene.registry.nink(id) ?: return
        commandStack.execute(
            NinkCommands.SetTransform(id, nink.transform, transform),
            coalesceKey = "transform:$id"
        )
    }

    fun setFlags(id: NinkId, active: Boolean? = null, visible: Boolean? = null, locked: Boolean? = null) {
        val nink = scene.registry.nink(id) ?: return
        if (active == null && visible == null && locked == null) return
        commandStack.execute(NinkCommands.SetFlags(id, active, visible, locked))
    }

    fun setActiveCamera(id: NinkId): Boolean {
        val nink = scene.registry.nink(id) ?: return false
        if (!nink.type.isCamera) return false
        commandStack.execute(NinkCommands.SetMainCamera(id))
        return true
    }

    // ------------------------------------------------------------------
    // Undo / redo
    // ------------------------------------------------------------------

    fun undo() {
        commandStack.undo()
    }

    fun redo() {
        commandStack.redo()
    }

    val canUndo: Boolean get() = commandStack.canUndo
    val canRedo: Boolean get() = commandStack.canRedo

    // ------------------------------------------------------------------

    private fun attachDefaultComponents(nink: Nink) {
        when {
            nink.type.isCamera -> nink.addComponent(NinkComponent.Camera())
            nink.type.isLight -> nink.addComponent(NinkComponent.Light())
        }
    }
}
