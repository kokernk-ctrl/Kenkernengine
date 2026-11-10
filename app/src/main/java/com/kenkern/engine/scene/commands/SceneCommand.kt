package com.kenkern.engine.scene.commands

import com.kenkern.engine.scene.NinkId
import com.kenkern.engine.scene.Scene

/**
 * A reversible scene mutation.
 *
 * Commands encapsulate both the forward operation and its inverse so the
 * editor gets reliable undo/redo. Commands must be deterministic and must
 * not perform IO or UI work.
 */
interface SceneCommand {
    val debugName: String

    fun execute(scene: Scene)

    fun undo(scene: Scene)
}

/**
 * Commands with the same [coalesceKey] executed back-to-back merge into a
 * single undo step (used for continuous Inspector dragging/typing).
 */
interface CoalescingCommand {
    val coalesceKey: String

    /** Adopts the later state of [later] while keeping the original old state. */
    fun mergeFrom(later: SceneCommand)
}

/**
 * LIFO command stack with undo/redo and change notification.
 *
 * All scene mutations from the editor flow through [execute]; direct scene
 * writes are limited to deserialization and validated inspector writes.
 */
class CommandStack(
    private val scene: Scene,
    private val onStackChanged: () -> Unit = {}
) {

    private val undoStack = ArrayDeque<SceneCommand>()
    private val redoStack = ArrayDeque<SceneCommand>()

    var canUndo: Boolean = false
        private set

    var canRedo: Boolean = false
        private set

    fun execute(command: SceneCommand, coalesceKey: String? = null) {
        command.execute(scene)
        val last = undoStack.lastOrNull()
        val merged = coalesceKey != null &&
            last is CoalescingCommand &&
            last.coalesceKey == coalesceKey
        if (merged && last is CoalescingCommand) {
            last.mergeFrom(command)
        } else {
            undoStack.addLast(command)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        }
        redoStack.clear()
        publishState()
    }

    fun undo(): SceneCommand? {
        val command = undoStack.removeLastOrNull() ?: return null
        command.undo(scene)
        redoStack.addLast(command)
        publishState()
        return command
    }

    fun redo(): SceneCommand? {
        val command = redoStack.removeLastOrNull() ?: return null
        command.execute(scene)
        undoStack.addLast(command)
        publishState()
        return command
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        publishState()
    }

    private fun publishState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        onStackChanged()
    }

    private companion object {
        const val MAX_HISTORY = 128
    }
}
