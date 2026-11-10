package com.kenkern.engine.scene

/**
 * Component attached to a Nink.
 *
 * Kept intentionally minimal in this block: the model is a sealed interface
 * so future blocks (physics, audio, scripting) can add components without
 * changing the Nink data structure or the serialization contract.
 */
sealed interface NinkComponent {

    /** Camera parameters for CAMERA ninks. */
    data class Camera(
        val fovDegrees: Float = 60f,
        val nearPlane: Float = 0.1f,
        val farPlane: Float = 100f
    ) : NinkComponent

    /** Light parameters for LIGHT_* ninks. Colors are RGB 0..1. */
    data class Light(
        val r: Float = 1f,
        val g: Float = 1f,
        val b: Float = 1f,
        val intensity: Float = 1f,
        val range: Float = 20f
    ) : NinkComponent

    fun copyOf(): NinkComponent = when (this) {
        is Camera -> copy()
        is Light -> copy()
    }
}
