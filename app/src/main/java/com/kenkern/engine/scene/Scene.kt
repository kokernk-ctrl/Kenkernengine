package com.kenkern.engine.scene

/**
 * Coarse change categories used for change notifications. They let the
 * editor refresh only what is affected (hierarchy, viewport payload,
 * inspector) without rebuilding everything on every mutation.
 */
enum class SceneChangeReason {
    /** Structure changed: add/delete/duplicate/reparent/reorder/rename. */
    STRUCTURE,

    /** A nink transform changed. */
    TRANSFORM,

    /** Flags/components changed: active, visible, locked, main camera. */
    FLAGS,

    /** The whole scene object was replaced (load/new). */
    SCENE_REPLACED
}

/**
 * Listener for scene changes. Called on the main thread after a command (or
 * a validated direct write) has been applied.
 */
fun interface SceneChangeListener {
    fun onSceneChanged(reason: SceneChangeReason, ninkId: NinkId?)
}

/**
 * A KENKERN scene: named collection of Ninks organized as a hierarchy.
 *
 * The [registry] is the single source of truth. Mutations must go through
 * the command system; this class only holds data, the main camera reference
 * and change notification.
 */
class Scene(
    val registry: NinkRegistry = NinkRegistry()
) {

    var name: String = DEFAULT_NAME

    /** Currently active camera for play mode (editor viewport keeps its own camera). */
    var mainCameraId: NinkId? = null

    private val listeners = mutableListOf<SceneChangeListener>()

    fun addListener(listener: SceneChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SceneChangeListener) {
        listeners.remove(listener)
    }

    fun notifyChanged(reason: SceneChangeReason, ninkId: NinkId? = null) {
        // Copy to tolerate listeners that mutate the listener list.
        for (listener in listeners.toList()) {
            listener.onSceneChanged(reason, ninkId)
        }
    }

    companion object {
        const val DEFAULT_NAME: String = "Main Scene"
    }
}
