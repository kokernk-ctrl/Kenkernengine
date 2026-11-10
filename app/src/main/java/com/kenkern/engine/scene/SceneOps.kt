package com.kenkern.engine.scene

import java.util.UUID

/**
 * Structural helper operations shared by commands and the editor facade.
 * Pure functions over the registry; no notifications are emitted here.
 */
object SceneOps {

    /**
     * Deep-copies the subtree rooted at [rootId], assigning fresh ids.
     * Returns copies in parents-first order with parent links already
     * remapped (the root copy keeps `parentId = null`; the caller decides
     * where to attach it via [AddSubtreeCommand]).
     */
    fun copySubtree(scene: Scene, rootId: NinkId): List<Nink>? {
        val source = scene.registry.nink(rootId) ?: return null
        val idMap = HashMap<NinkId, NinkId>()
        val copies = mutableListOf<Nink>()

        fun visit(nink: Nink, parentId: NinkId?) {
            val newId = NinkId(UUID.randomUUID().toString())
            idMap[nink.id] = newId
            val copy = Nink(
                id = newId,
                name = nink.name,
                type = nink.type,
                transform = nink.transform.copy(),
                components = nink.components.map { it.copyOf() }.toMutableList(),
                parentId = parentId,
                active = nink.active,
                visible = nink.visible,
                locked = false, // duplicates start unlocked
                customProperties = nink.customProperties.toMutableMap()
            )
            copies.add(copy)
            for (childId in nink.childIds.toList()) {
                scene.registry.nink(childId)?.let { visit(it, newId) }
            }
        }

        visit(source, null)
        return copies
    }

    /**
     * Generates a display name that is unique among the siblings of
     * [parentId] (null = root level), derived from [baseName].
     */
    fun uniqueName(scene: Scene, parentId: NinkId?, baseName: String): String {
        val siblings = scene.registry.childrenOf(parentId)
            .mapNotNull { scene.registry.nink(it)?.name }
            .toSet()
        if (baseName !in siblings) return baseName
        var counter = 2
        while (true) {
            val candidate = "$baseName $counter"
            if (candidate !in siblings) return candidate
            counter++
        }
    }

    /** Finds the first nink (pre-order) with a camera component. */
    fun findFirstCamera(scene: Scene): NinkId? {
        for (nink in scene.registry.allNinks()) {
            if (nink.type.isCamera) return nink.id
        }
        return null
    }
}
