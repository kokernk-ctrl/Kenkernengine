package com.kenkern.engine.core

/**
 * Centralized, immutable-by-default engine configuration for KENKERN.
 *
 * Values here describe engine-level constants shared by the editor UI, the
 * rendering subsystem and the scene system. Runtime-tunable options are
 * exposed as `var` fields; everything else is a compile-time constant so the
 * renderer and the scene model can rely on them without indirection.
 */
object EngineConfig {

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------
    const val ENGINE_NAME: String = "KENKERN"
    const val VERSION: String = "0.1.1"

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------
    /** Minimum required OpenGL ES major version (Vulkan is a future backend). */
    const val REQUIRED_GLES_MAJOR_VERSION: Int = 3

    /** Default editor camera parameters. */
    const val EDITOR_CAMERA_FOV_DEGREES: Float = 55f
    const val NEAR_PLANE: Float = 0.1f
    const val FAR_PLANE: Float = 200f

    /** Reference grid extents, in world units. */
    const val GRID_EXTENT: Int = 10
    const val GRID_STEP: Int = 1

    /** Clear color matching the KENKERN dark identity (#0E0E10). */
    const val CLEAR_R: Float = 0.055f
    const val CLEAR_G: Float = 0.055f
    const val CLEAR_B: Float = 0.063f
    const val CLEAR_A: Float = 1f

    // ------------------------------------------------------------------
    // Runtime-tunable options (editor settings surface lives in later blocks)
    // ------------------------------------------------------------------
    /** Master switch for the editor autosave debounce loop. */
    @Volatile
    var autosaveEnabled: Boolean = true

    /** Debounce delay applied after the last scene change before autosave. */
    @Volatile
    var autosaveDelayMs: Long = 1500L

    /** Whether the FPS overlay is shown by default when opening the editor. */
    @Volatile
    var showFpsByDefault: Boolean = true
}
