package com.kenkern.engine.editor

import com.kenkern.engine.serialization.SerializationException
import com.kenkern.engine.scene.Scene
import com.kenkern.engine.serialization.SceneSerializer

/**
 * Pure decision layer for opening a scene inside the editor (extracted from
 * [EditorViewModel] so the open/recover/fail policy is unit-testable on the
 * JVM without Android dependencies).
 *
 * Policy:
 *  - no scene file (new project)      -> default scene, no error,
 *  - scene file fails to parse        -> default scene + the recorded
 *    [SerializationException]; the project and its (corrupt) file are never
 *    deleted or rewritten by the load path itself, and the user sees a
 *    localized message,
 *  - anything unexpected while opening -> [SceneOpenOutcome.Failed]; the
 *    editor must surface an error and return to the launcher instead of
 *    showing a broken editor or crashing.
 */
object SceneOpenResolver {

    sealed interface SceneOpenOutcome {

        /** A scene is ready to edit. [recoveredFrom] is set when file data was unusable. */
        data class Ready(
            val scene: Scene,
            val recoveredFrom: SerializationException? = null
        ) : SceneOpenOutcome

        /** Opening failed unexpectedly; the caller must show an error, never crash. */
        data class Failed(val error: Exception) : SceneOpenOutcome
    }

    /**
     * @param rawJson scene file content, or `null` when the project has no
     *        entry scene yet (fresh project).
     * @param defaultSceneFactory builds a fresh default scene (localized
     *        names are supplied by the caller, keeping this class Android-free).
     */
    fun open(rawJson: String?, defaultSceneFactory: () -> Scene): SceneOpenOutcome {
        return try {
            if (rawJson == null) {
                SceneOpenOutcome.Ready(defaultSceneFactory())
            } else {
                val scene = try {
                    SceneSerializer.deserialize(rawJson)
                } catch (e: SerializationException) {
                    // Recoverable data problem: keep the project, fall back to
                    // a valid scene and record the actual error for the UI.
                    return SceneOpenOutcome.Ready(defaultSceneFactory(), e)
                }
                SceneOpenOutcome.Ready(scene)
            }
        } catch (e: Exception) {
            SceneOpenOutcome.Failed(e)
        }
    }
}
