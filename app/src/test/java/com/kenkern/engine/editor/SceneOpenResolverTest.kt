package com.kenkern.engine.editor

import com.kenkern.engine.serialization.SceneSerializer
import com.kenkern.engine.scene.Scene
import com.kenkern.engine.scene.SceneFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the project-opening decision layer (HOTFIX 0.1.1):
 * creating a project, opening its default scene, loading a saved scene,
 * invalid/missing scene files, empty hierarchies and scene-init failure.
 */
class SceneOpenResolverTest {

    private fun defaultSceneFactory(): Scene =
        SceneFactory.createDefaultScene { type -> type.name }

    // ------------------------------------------------------------------
    // Missing scene file (freshly created project)
    // ------------------------------------------------------------------

    @Test
    fun `missing scene file opens the default scene without error`() {
        val outcome = SceneOpenResolver.open(null, ::defaultSceneFactory)
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Ready)
        outcome as SceneOpenResolver.SceneOpenOutcome.Ready
        assertNull(outcome.recoveredFrom)
        assertTrue(outcome.scene.registry.ninkCount() > 0)
        assertNotNull(outcome.scene.mainCameraId)
    }

    // ------------------------------------------------------------------
    // Invalid scene files (must recover, never crash)
    // ------------------------------------------------------------------

    @Test
    fun `corrupt json recovers with default scene and records the error`() {
        val outcome = SceneOpenResolver.open("this is not json {{{", ::defaultSceneFactory)
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Ready)
        outcome as SceneOpenResolver.SceneOpenOutcome.Ready
        assertNotNull(outcome.recoveredFrom)
        assertTrue(outcome.scene.registry.ninkCount() > 0)
    }

    @Test
    fun `wrong format id recovers with default scene`() {
        val json = """{"format":"someone-else-scene","version":1,"name":"X","ninks":[]}"""
        val outcome = SceneOpenResolver.open(json, ::defaultSceneFactory)
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Ready)
        outcome as SceneOpenResolver.SceneOpenOutcome.Ready
        assertNotNull(outcome.recoveredFrom)
    }

    @Test
    fun `wrong format version recovers with default scene`() {
        val json = """{"format":"kenkern-scene","version":99,"name":"X","ninks":[]}"""
        val outcome = SceneOpenResolver.open(json, ::defaultSceneFactory)
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Ready)
        outcome as SceneOpenResolver.SceneOpenOutcome.Ready
        assertNotNull(outcome.recoveredFrom)
    }

    @Test
    fun `scene with ninks referencing missing parents still opens`() {
        val json = """
            {"format":"kenkern-scene","version":1,"name":"Broken","mainCamera":null,
             "ninks":[{"id":"a","name":"Child","type":"MESH_CUBE","parent":"ghost"},
                      {"id":"b","name":"Root","type":"EMPTY","parent":null}]}
        """.trimIndent()
        val outcome = SceneOpenResolver.open(json, ::defaultSceneFactory)
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Ready)
        outcome as SceneOpenResolver.SceneOpenOutcome.Ready
        assertNull(outcome.recoveredFrom)
        assertEquals(2, outcome.scene.registry.ninkCount())
        // The orphan was promoted to root by the defensive loader.
        assertEquals(2, outcome.scene.registry.rootIds().size)
    }

    // ------------------------------------------------------------------
    // Empty hierarchy
    // ------------------------------------------------------------------

    @Test
    fun `empty nink hierarchy loads as a valid empty scene`() {
        val json = """{"format":"kenkern-scene","version":1,"name":"Empty","mainCamera":null,"ninks":[]}"""
        val outcome = SceneOpenResolver.open(json, ::defaultSceneFactory)
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Ready)
        outcome as SceneOpenResolver.SceneOpenOutcome.Ready
        assertNull(outcome.recoveredFrom)
        assertEquals(0, outcome.scene.registry.ninkCount())
        assertEquals(0, outcome.scene.registry.rootIds().size)
        assertNull(outcome.scene.mainCameraId)
    }

    // ------------------------------------------------------------------
    // Valid saved scene
    // ------------------------------------------------------------------

    @Test
    fun `saved scene opens with its data intact`() {
        val original = Scene()
        original.name = "Saved Scene"
        val sceneJson = SceneSerializer.serialize(original)
        val outcome = SceneOpenResolver.open(sceneJson, ::defaultSceneFactory)
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Ready)
        outcome as SceneOpenResolver.SceneOpenOutcome.Ready
        assertNull(outcome.recoveredFrom)
        assertEquals("Saved Scene", outcome.scene.name)
    }

    // ------------------------------------------------------------------
    // Scene initialization failure (default-scene factory itself fails)
    // ------------------------------------------------------------------

    @Test
    fun `default scene factory failure surfaces as Failed instead of crashing`() {
        val outcome = SceneOpenResolver.open(null) {
            throw IllegalStateException("no localized resources")
        }
        assertTrue(outcome is SceneOpenResolver.SceneOpenOutcome.Failed)
        outcome as SceneOpenResolver.SceneOpenOutcome.Failed
        assertTrue(outcome.error is IllegalStateException)
    }
}
