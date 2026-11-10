package com.kenkern.engine.rendering

import java.util.concurrent.ConcurrentHashMap

/**
 * Color palette applied to ninks in the viewport.
 *
 * Derived from the KENKERN identity (white shapes, blue accent highlights)
 * plus a muted, technical set of hues so every nink type is distinguishable.
 * Values are linear RGB 0..1 with alpha.
 */
object NinkRenderColors {

    private val cache = ConcurrentHashMap<String, FloatArray>()

    fun colorForType(typeName: String): FloatArray = cache.getOrPut(typeName) {
        when (typeName) {
            "MESH_CUBE" -> floatArrayOf(0.86f, 0.88f, 0.92f, 1f)
            "MESH_SPHERE" -> floatArrayOf(0f, 0.482f, 1f, 1f)
            "MESH_PLANE" -> floatArrayOf(0.55f, 0.58f, 0.62f, 1f)
            "MESH_CYLINDER" -> floatArrayOf(0.78f, 0.80f, 0.84f, 1f)
            "MESH_TRIANGLE" -> floatArrayOf(0f, 0.70f, 0.62f, 1f)
            "CHARACTER_PLAYER" -> floatArrayOf(0.30f, 0.69f, 0.31f, 1f)
            "CHARACTER_NPC" -> floatArrayOf(0.95f, 0.61f, 0.14f, 1f)
            "CAMERA" -> floatArrayOf(0.95f, 0.80f, 0.20f, 1f)
            "LIGHT_DIRECTIONAL" -> floatArrayOf(0.98f, 0.92f, 0.60f, 1f)
            "LIGHT_POINT" -> floatArrayOf(0.90f, 0.40f, 0.70f, 1f)
            "LIGHT_SPOT" -> floatArrayOf(0.85f, 0.55f, 0.90f, 1f)
            else -> floatArrayOf(0.70f, 0.72f, 0.75f, 1f)
        }
    }

    /** Dimmed variant used for inactive ninks (still rendered, muted). */
    fun dimmed(color: FloatArray): FloatArray = floatArrayOf(
        color[0] * 0.45f, color[1] * 0.45f, color[2] * 0.45f, 0.6f * color[3]
    )
}
