package com.kenkern.engine.rendering

/**
 * Immutable snapshot of what the GL thread should draw for one frame.
 *
 * The editor (main thread) rebuilds this whenever scene data changes; the
 * renderer consumes it on the GL thread. Publication is a single volatile
 * swap — the GL thread never touches live scene objects.
 */
class RenderFrameData(
    /** Renderable items with world-space model matrices. */
    val items: List<Item>,
    /** Nink id of the current selection, or null. */
    val selectionKey: String?,
    /** World matrix of the selection (for the highlight outline), or null. */
    val selectionMatrix: FloatArray?
) {

    class Item(
        /** Scene-space mesh kind to draw (mapped from the Nink type). */
        val meshKind: MeshKind,
        /** Column-major world matrix (owned by this frame data). */
        val modelMatrix: FloatArray,
        /** RGBA color, premultiplied by type palette and flags. */
        val colorRgba: FloatArray
    )
}

/**
 * Thread-safe hand-off point between the editor thread and the GL thread.
 */
object RenderFramePublisher {

    @Volatile
    var current: RenderFrameData? = null

    @Volatile
    var gridVisible: Boolean = true
}

/**
 * Mesh families the renderer can draw. Scene ninks map to one of these;
 * non-visual nink types (groups, empties) never produce items.
 */
enum class MeshKind {
    TRIANGLE,
    PLANE,
    CUBE,
    SPHERE,
    CYLINDER,
    PYRAMID,
    WIRE_CUBE;

    companion object {
        /** Fallback mapping for types without a dedicated mesh. */
        fun fromNinkType(typeName: String): MeshKind = when (typeName) {
            "MESH_TRIANGLE" -> TRIANGLE
            "MESH_PLANE" -> PLANE
            "MESH_CUBE" -> CUBE
            "MESH_SPHERE" -> SPHERE
            "MESH_CYLINDER" -> CYLINDER
            "CHARACTER_PLAYER", "CHARACTER_NPC" -> CUBE
            "CAMERA" -> PYRAMID
            "LIGHT_DIRECTIONAL", "LIGHT_POINT", "LIGHT_SPOT" -> PYRAMID
            else -> CUBE
        }
    }
}
