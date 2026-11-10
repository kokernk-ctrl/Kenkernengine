package com.kenkern.engine.scene

import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds the default scene for newly created projects.
 *
 * The starter content demonstrates the hierarchy (groups with children),
 * a light, an active camera and basic 3D objects. All default names are
 * resolved through [defaultName] so they are properly localized at
 * creation time (stored names are scene data).
 */
object SceneFactory {

    fun createDefaultScene(defaultName: (NinkType) -> String): Scene {
        val scene = Scene()
        val registry = scene.registry

        fun add(type: NinkType, parent: NinkId?, name: String = defaultName(type)): Nink {
            val nink = Nink(id = NinkId.generate(), name = name, type = type)
            when {
                type.isCamera -> nink.addComponent(NinkComponent.Camera())
                type.isLight -> nink.addComponent(NinkComponent.Light())
            }
            registry.add(nink, parent)
            return nink
        }

        // Environment group
        val environment = add(NinkType.GROUP_ENVIRONMENT, null)
        val ground = add(NinkType.MESH_PLANE, environment.id)
        ground.transform.scale.set(8f, 1f, 8f)

        val triangle = add(NinkType.MESH_TRIANGLE, environment.id, defaultName(NinkType.MESH_TRIANGLE))
        triangle.transform.position.set(-2.6f, 1f, 0.2f)
        triangle.transform.rotationEulerRadians.set(0f, 0.5f, 0f)
        triangle.transform.scale.set(1.3f, 1.3f, 1.3f)

        val cube = add(NinkType.MESH_CUBE, environment.id, defaultName(NinkType.MESH_CUBE))
        cube.transform.position.set(1.8f, 0.5f, -0.6f)
        cube.transform.rotationEulerRadians.set(0f, 0.44f, 0f)

        // Player group
        val playerGroup = add(NinkType.GROUP, null)
        val player = add(NinkType.CHARACTER_PLAYER, playerGroup.id)
        player.transform.position.set(0f, 0.5f, 1.6f)

        // Lighting group
        val lighting = add(NinkType.GROUP, null)
        val sun = add(NinkType.LIGHT_DIRECTIONAL, lighting.id)
        val pitch = -0.9f
        val yaw = 0.5f
        sun.transform.rotationEulerRadians.set(pitch, yaw, 0f)
        sun.transform.position.set(
            (sin(yaw) * cos(pitch)) * 4f + 0f,
            4f,
            (cos(yaw) * cos(pitch)) * 4f + 0f
        )

        // Camera
        val camera = add(NinkType.CAMERA, null)
        camera.transform.position.set(0f, 3.2f, 8.5f)
        camera.transform.rotationEulerRadians.set(-0.18f, 0f, 0f)
        scene.mainCameraId = camera.id

        return scene
    }
}
