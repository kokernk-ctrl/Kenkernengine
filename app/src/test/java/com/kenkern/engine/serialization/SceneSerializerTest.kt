package com.kenkern.engine.serialization

import com.kenkern.engine.scene.Nink
import com.kenkern.engine.scene.NinkComponent
import com.kenkern.engine.scene.NinkId
import com.kenkern.engine.scene.NinkType
import com.kenkern.engine.scene.Scene
import com.kenkern.engine.scene.SceneFactory
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneSerializerTest {

    private fun buildComplexScene(): Scene {
        val scene = Scene()
        scene.name = "Test Scene"
        val registry = scene.registry

        val group = Nink(NinkId.generate(), "Environment", NinkType.GROUP_ENVIRONMENT)
        val ground = Nink(NinkId.generate(), "Ground", NinkType.MESH_PLANE)
        ground.transform.position.set(0f, -0.5f, 0f)
        ground.transform.scale.set(4f, 1f, 4f)
        val hidden = Nink(NinkId.generate(), "Hidden Cube", NinkType.MESH_CUBE)
        hidden.visible = false
        val inactive = Nink(NinkId.generate(), "Inactive Sphere", NinkType.MESH_SPHERE)
        inactive.active = false
        val locked = Nink(NinkId.generate(), "Locked", NinkType.MESH_CYLINDER)
        locked.locked = true

        val camera = Nink(NinkId.generate(), "Main Cam", NinkType.CAMERA)
        camera.addComponent(NinkComponent.Camera(fovDegrees = 70f, nearPlane = 0.2f, farPlane = 250f))
        val light = Nink(NinkId.generate(), "Sun", NinkType.LIGHT_DIRECTIONAL)
        light.addComponent(NinkComponent.Light(intensity = 2.5f, range = 40f))

        registry.add(group, null)
        registry.add(ground, group.id)
        registry.add(hidden, group.id)
        registry.add(inactive, null)
        registry.add(locked, null)
        registry.add(camera, null)
        registry.add(light, null)
        scene.mainCameraId = camera.id
        return scene
    }

    @Test
    fun `scene roundtrip preserves structure flags transforms and components`() {
        val original = buildComplexScene()
        val json = SceneSerializer.serialize(original)
        val restored = SceneSerializer.deserialize(json)

        assertEquals("Test Scene", restored.name)
        assertEquals(original.registry.ninkCount(), restored.registry.ninkCount())
        assertEquals(original.mainCameraId, restored.mainCameraId)

        // Find "Environment" group and verify children.
        val group = restored.registry.allNinks().first { it.name == "Environment" }
        assertTrue(group.type.isGroup)
        assertEquals(2, restored.registry.childrenOf(group.id).size)

        val ground = restored.registry.allNinks().first { it.name == "Ground" }
        assertEquals(group.id, ground.parentId)
        assertEquals(-0.5f, ground.transform.position.y, 1e-5f)
        assertEquals(4f, ground.transform.scale.x, 1e-5f)

        val hidden = restored.registry.allNinks().first { it.name == "Hidden Cube" }
        assertTrue(!hidden.visible)
        val inactive = restored.registry.allNinks().first { it.name == "Inactive Sphere" }
        assertTrue(!inactive.active)
        val locked = restored.registry.allNinks().first { it.name == "Locked" }
        assertTrue(locked.locked)

        val camera = restored.registry.allNinks().first { it.name == "Main Cam" }
        val cameraComponent = camera.componentOfType<NinkComponent.Camera>()
        assertNotNull(cameraComponent)
        assertEquals(70f, cameraComponent!!.fovDegrees, 1e-4f)
        assertEquals(0.2f, cameraComponent.nearPlane, 1e-4f)
        assertEquals(250f, cameraComponent.farPlane, 1e-4f)

        val light = restored.registry.allNinks().first { it.name == "Sun" }
        val lightComponent = light.componentOfType<NinkComponent.Light>()
        assertNotNull(lightComponent)
        assertEquals(2.5f, lightComponent!!.intensity, 1e-4f)
    }

    @Test
    fun `corrupt json raises SerializationException`() {
        var thrown = false
        try {
            SceneSerializer.deserialize("this is not json {")
        } catch (e: SerializationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `unknown format is rejected`() {
        val json = """{"format":"other-engine","version":1,"ninks":[]}"""
        var thrown = false
        try {
            SceneSerializer.deserialize(json)
        } catch (e: SerializationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `missing parent reference promotes nink to root`() {
        val scene = Scene()
        val nink = Nink(NinkId.generate(), "Orphan", NinkType.MESH_CUBE)
        nink.parentId = NinkId.generate() // dangling
        scene.registry.add(nink, null)
        val restored = SceneSerializer.deserialize(SceneSerializer.serialize(scene))
        assertEquals(1, restored.registry.rootIds().size)
        assertNull(restored.registry.nink(restored.registry.rootIds().first())!!.parentId)
    }

    @Test
    fun `unknown nink type loads as EMPTY with data intact`() {
        val json = """
            {
              "format": "kenkern-scene",
              "version": 1,
              "name": "S",
              "ninks": [
                {"id": "11111111-1111-1111-1111-111111111111", "name": "Weird", "type": "MESH_DRAGON", "parent": null}
              ]
            }
        """.trimIndent()
        val restored = SceneSerializer.deserialize(json)
        val weird = restored.registry.nink(NinkId("11111111-1111-1111-1111-111111111111"))
        assertNotNull(weird)
        assertEquals(NinkType.EMPTY, weird!!.type)
        assertEquals("Weird", weird.name)
    }

    @Test
    fun `cyclic hierarchy is broken deterministically`() {
        val a = Nink(NinkId("aaaaaaaa-1111-1111-1111-111111111111"), "A", NinkType.GROUP)
        val b = Nink(NinkId("bbbbbbbb-1111-1111-1111-111111111111"), "B", NinkType.EMPTY)
        a.childIds.add(b.id)
        b.childIds.add(a.id)
        a.parentId = b.id
        b.parentId = a.id
        val json = """
            {
              "format": "kenkern-scene",
              "version": 1,
              "name": "Cycle",
              "ninks": [
                {"id": "aaaaaaaa-1111-1111-1111-111111111111", "name": "A", "type": "GROUP", "parent": "bbbbbbbb-1111-1111-1111-111111111111"},
                {"id": "bbbbbbbb-1111-1111-1111-111111111111", "name": "B", "type": "EMPTY", "parent": "aaaaaaaa-1111-1111-1111-111111111111"}
              ]
            }
        """.trimIndent()
        val restored = SceneSerializer.deserialize(json)
        assertEquals(2, restored.registry.ninkCount())
        // After breaking the cycle at least one nink is a root and the
        // hierarchy is acyclic again.
        val roots = restored.registry.rootIds()
        assertTrue(roots.isNotEmpty())
    }

    @Test
    fun `duplicate ids in file are skipped`() {
        val id = "cccccccc-1111-1111-1111-111111111111"
        val json = """
            {
              "format": "kenkern-scene",
              "version": 1,
              "name": "Dup",
              "ninks": [
                {"id": "$id", "name": "First", "type": "EMPTY", "parent": null},
                {"id": "$id", "name": "Second", "type": "EMPTY", "parent": null}
              ]
            }
        """.trimIndent()
        val restored = SceneSerializer.deserialize(json)
        assertEquals(1, restored.registry.ninkCount())
        assertEquals("First", restored.registry.nink(NinkId(id))!!.name)
    }

    @Test
    fun `prefab roundtrip keeps subtree and detaches root`() {
        val scene = buildComplexScene()
        val group = scene.registry.allNinks().first { it.name == "Environment" }
        val json = SceneSerializer.serializeSubtree(scene, group.id, "EnvPrefab")!!
        val template = SceneSerializer.parsePrefab(json)

        // Environment + Ground + Hidden Cube
        assertEquals(3, template.size)
        assertNull(template.first().parentId)
        assertEquals("Environment", template.first().name)
        assertEquals(template.first().id, template[1].parentId)
        assertEquals(template.first().id, template[2].parentId)
    }

    @Test
    fun `default scene is valid and serializes`() {
        val scene = SceneFactory.createDefaultScene { type -> type.name }
        val json = SceneSerializer.serialize(scene)
        val restored = SceneSerializer.deserialize(json)
        assertEquals(scene.registry.ninkCount(), restored.registry.ninkCount())
        assertNotNull(restored.mainCameraId)
        // Default scene contains the Phase 1 demo geometry as ninks.
        val names = restored.registry.allNinks().map { it.type }
        assertTrue(names.contains(NinkType.MESH_TRIANGLE))
        assertTrue(names.contains(NinkType.MESH_CUBE))
        assertTrue(names.contains(NinkType.CAMERA))
        assertTrue(names.any { it.isGroup })
    }

    @Test
    fun `missing ninks array loads empty scene`() {
        val json = """{"format":"kenkern-scene","version":1,"name":"Empty"}"""
        val restored = SceneSerializer.deserialize(json)
        assertEquals(0, restored.registry.ninkCount())
        assertEquals("Empty", restored.name)
    }

    // ------------------------------------------------------------------
    // HOTFIX 0.1.1 regression: the exact sequence performed when creating a
    // project (default scene) and reopening it later (saved scene).
    // ------------------------------------------------------------------

    @Test
    fun `default scene survives the create-then-reopen roundtrip exactly`() {
        val created = SceneFactory.createDefaultScene { type -> type.name }

        // Simulate project creation persisting the starter scene...
        val savedJson = SceneSerializer.serialize(created)
        // ...and the editor loading it again afterwards.
        val reopened = SceneSerializer.deserialize(savedJson)

        assertEquals(created.name, reopened.name)
        assertEquals(created.registry.ninkCount(), reopened.registry.ninkCount())
        assertEquals(created.mainCameraId, reopened.mainCameraId)

        // Parent/child links survive: every child of the default environment
        // group is re-attached under the same-named group after reopening.
        val createdEnv = created.registry.allNinks().first { it.type == NinkType.GROUP_ENVIRONMENT }
        val reopenedEnv = reopened.registry.allNinks().first { it.type == NinkType.GROUP_ENVIRONMENT }
        assertEquals(createdEnv.childIds.size, reopenedEnv.childIds.size)
        for (childId in createdEnv.childIds) {
            val child = created.registry.nink(childId)!!
            val reopenedChild = reopened.registry.allNinks().first { it.name == child.name }
            assertEquals(reopenedEnv.id, reopenedChild.parentId)
        }
        // Transforms survive.
        val createdCube = created.registry.allNinks().first { it.type == NinkType.MESH_CUBE }
        val reopenedCube = reopened.registry.allNinks().first { it.type == NinkType.MESH_CUBE }
        assertEquals(createdCube.transform, reopenedCube.transform)
    }

    @Test
    fun `empty hierarchy survives the save-reopen roundtrip`() {
        val empty = Scene()
        empty.name = "No Ninks"
        val json = SceneSerializer.serialize(empty)
        val reopened = SceneSerializer.deserialize(json)
        assertEquals(0, reopened.registry.ninkCount())
        assertEquals(0, reopened.registry.rootIds().size)
        assertNull(reopened.mainCameraId)
        assertEquals("No Ninks", reopened.name)
        // Serializing the reopened scene again is stable (save on exit).
        val resaved = SceneSerializer.serialize(reopened)
        val reloaded = SceneSerializer.deserialize(resaved)
        assertEquals(0, reloaded.registry.ninkCount())
    }
}
