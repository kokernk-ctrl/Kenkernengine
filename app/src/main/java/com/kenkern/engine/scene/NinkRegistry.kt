package com.kenkern.engine.scene

import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.math.Matrix4

/**
 * Central Nink registry and hierarchy model.
 *
 * This is the single source of truth for scene structure — the editor never
 * maintains a separate hierarchy structure. All mutations happen through the
 * command system ([com.kenkern.engine.scene.commands]), which calls into
 * this registry.
 *
 * Invariants maintained here:
 *  - every nink id is unique,
 *  - parent/child references are always consistent in both directions,
 *  - the hierarchy is acyclic (reparenting into own subtree is rejected),
 *  - child ordering is stable.
 */
class NinkRegistry {

    private val ninks = LinkedHashMap<NinkId, Nink>()
    private val roots = mutableListOf<NinkId>()

    fun nink(id: NinkId?): Nink? = id?.let { ninks[it] }

    fun contains(id: NinkId): Boolean = ninks.containsKey(id)

    fun allNinks(): Collection<Nink> = ninks.values

    fun ninkCount(): Int = ninks.size

    fun rootIds(): List<NinkId> = roots.toList()

    fun childrenOf(parentId: NinkId?): List<NinkId> =
        if (parentId == null) roots.toList()
        else ninks[parentId]?.childIds?.toList() ?: emptyList()

    fun childCount(parentId: NinkId?): Int =
        if (parentId == null) roots.size else ninks[parentId]?.childIds?.size ?: 0

    // ------------------------------------------------------------------
    // Mutation primitives (invoked by commands only)
    // ------------------------------------------------------------------

    /**
     * Adds a nink under [parentId] (null = root) at [index] (-1 = append).
     * Throws on duplicate ids or missing parents — commands validate first,
     * so a throw indicates a real bug, not user error.
     */
    fun add(nink: Nink, parentId: NinkId?, index: Int = -1) {
        require(!ninks.containsKey(nink.id)) { "Duplicate Nink id: ${nink.id}" }
        if (parentId != null) {
            val parent = ninks[parentId]
            requireNotNull(parent) { "Parent $parentId does not exist" }
            require(!subtreeContains(nink.id, parentId)) { "Cannot add ${nink.id} into its own subtree" }
        }
        ninks[nink.id] = nink
        nink.parentId = parentId
        val target = if (parentId == null) roots else ninks[parentId]!!.childIds
        val clamped = if (index < 0 || index > target.size) target.size else index
        target.add(clamped, nink.id)
    }

    /**
     * Removal record produced by [removeSubtree]; consumed by
     * [restoreSubtree] for exact undo of deletions.
     */
    class RemovedSubtree(
        val ninksPreOrder: List<Nink>,
        val parentId: NinkId?,
        val index: Int
    )

    /**
     * Removes a subtree (the nink and all descendants), returning a record
     * that allows exact restoration. Nink objects keep their internal
     * parent/child links so the subtree structure is preserved.
     */
    fun removeSubtree(id: NinkId): RemovedSubtree? {
        val root = ninks[id] ?: return null
        val removed = mutableListOf<Nink>()
        collectPreOrder(root, removed)
        val parentId = root.parentId
        val index = indexOf(id)
        // Detach subtree root from its parent; descendants keep their links.
        if (parentId != null) {
            ninks[parentId]?.childIds?.remove(id)
        } else {
            roots.remove(id)
        }
        for (nink in removed) {
            ninks.remove(nink.id)
        }
        return RemovedSubtree(removed, parentId, index)
    }

    /**
     * Restores a subtree previously returned by [removeSubtree]. The parent
     * of the subtree root must still exist (deletion commands guarantee it:
     * deleting a parent removes the whole subtree with it).
     */
    fun restoreSubtree(removed: RemovedSubtree) {
        val rootParent = removed.parentId?.takeIf { contains(it) }
        for (nink in removed.ninksPreOrder) {
            val isRoot = nink.id == removed.ninksPreOrder.first().id
            val recordedParent = nink.parentId
            val effectiveParent = when {
                isRoot -> rootParent
                recordedParent != null && contains(recordedParent) -> recordedParent
                else -> null // defensive: promote orphan to root
            }
            val index = if (isRoot) removed.index else -1
            // childIds are rebuilt by add() as children are restored; clear
            // the snapshot lists to avoid duplicated entries.
            nink.childIds.clear()
            add(nink, effectiveParent, index)
        }
    }

    /** True when [newParent] is inside the subtree rooted at [id]. */
    fun subtreeContains(id: NinkId, candidate: NinkId?): Boolean {
        if (candidate == null) return false
        if (id == candidate) return true
        val root = ninks[id] ?: return false
        val stack = ArrayDeque<NinkId>()
        stack.addLast(root.id)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == candidate) return true
            ninks[current]?.childIds?.forEach { stack.addLast(it) }
        }
        return false
    }

    /** Whether reparenting [id] under [newParent] is legal. */
    fun canReparent(id: NinkId, newParent: NinkId?): Boolean {
        val nink = ninks[id] ?: return false
        if (newParent == null) return true
        if (!ninks.containsKey(newParent)) return false
        if (newParent == id) return false
        if (subtreeContains(id, newParent)) return false
        if (ninks[newParent]!!.locked) return false
        return true
    }

    /** Moves [id] under [newParent] at [index]. Call [canReparent] first. */
    fun reparent(id: NinkId, newParent: NinkId?, index: Int = -1) {
        val nink = ninks[id] ?: return
        // Detach
        val oldParent = nink.parentId
        if (oldParent != null) {
            ninks[oldParent]?.childIds?.remove(id)
        } else {
            roots.remove(id)
        }
        // Attach
        nink.parentId = newParent
        val target = if (newParent == null) roots else ninks[newParent]!!.childIds
        val clamped = if (index < 0 || index > target.size) target.size else index
        target.add(clamped, id)
    }

    /** Moves [id] within its current parent to [toIndex]. */
    fun reorderWithinParent(id: NinkId, toIndex: Int) {
        val nink = ninks[id] ?: return
        val target = if (nink.parentId == null) roots else ninks[nink.parentId]!!.childIds
        val from = target.indexOf(id)
        if (from < 0) return
        val clamped = toIndex.coerceIn(0, target.size - 1)
        if (clamped == from) return
        target.removeAt(from)
        target.add(clamped, id)
    }

    fun indexOf(id: NinkId): Int {
        val nink = ninks[id] ?: return -1
        val target = if (nink.parentId == null) roots else ninks[nink.parentId]!!.childIds
        return target.indexOf(id)
    }

    /** Index of [id] within its parent (or -1) — used by commands. */
    fun indexInParent(id: NinkId): Int = indexOf(id)

    /**
     * Detaches [id] from its parent (if any) and appends it to the root
     * list. Used defensively to break corrupted cycles during deserialization.
     */
    fun promoteToRoot(id: NinkId) {
        val nink = ninks[id] ?: return
        val parent = nink.parentId
        if (parent != null) {
            ninks[parent]?.childIds?.remove(id)
        } else {
            roots.remove(id)
        }
        nink.parentId = null
        if (!roots.contains(id)) roots.add(id)
    }

    // ------------------------------------------------------------------
    // Math helpers
    // ------------------------------------------------------------------

    /**
     * Computes the world matrix of [id] into [out]. Returns false when the
     * nink does not exist or the chain is broken (missing parent).
     */
    fun worldMatrix(id: NinkId, out: Matrix4, scratch: Matrix4): Boolean {
        val chain = ArrayList<Nink>(8)
        var current = ninks[id] ?: return false
        chain.add(current)
        while (current.parentId != null) {
            val parent = ninks[current.parentId]
            if (parent == null) {
                // Broken chain: treat the deepest existing ancestor as root.
                EngineLogger.w(SUBSYSTEM, "Broken parent chain at ${current.id} (parent ${current.parentId} missing)")
                break
            }
            chain.add(parent)
            current = parent
        }
        out.setIdentity()
        for (i in chain.indices.reversed()) {
            chain[i].transform.toMatrix(scratch)
            out.multiply(out, scratch)
        }
        return true
    }

    /** Depth of [id] in the hierarchy (root = 0), or -1 when missing. */
    fun depthOf(id: NinkId): Int {
        var depth = 0
        var current = ninks[id] ?: return -1
        while (current.parentId != null) {
            val parent = ninks[current.parentId] ?: break
            depth++
            current = parent
        }
        return depth
    }

    fun clear() {
        ninks.clear()
        roots.clear()
    }

    private fun collectPreOrder(nink: Nink, out: MutableList<Nink>) {
        out.add(nink)
        for (childId in nink.childIds.toList()) {
            ninks[childId]?.let { collectPreOrder(it, out) }
        }
    }

    private companion object {
        const val SUBSYSTEM = "NinkRegistry"
    }
}
