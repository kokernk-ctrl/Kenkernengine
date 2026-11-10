package com.kenkern.engine.scene

import androidx.annotation.StringRes
import com.kenkern.engine.R

/** High-level grouping used by the Add Nink menu. */
enum class NinkCategory {
    OBJECTS,
    CHARACTERS,
    GROUPS,
    CAMERAS,
    LIGHTING,
    OTHER
}

/**
 * The kind of a Nink.
 *
 * Types are stable serialized identifiers (enum names are written to scene
 * files). New types may be appended in future blocks; removing or renaming
 * a type is a breaking change and must bump the scene format version.
 */
enum class NinkType(
    val category: NinkCategory,
    @StringRes val nameRes: Int,
    /** Whether this type has a viewport representation. */
    val isRenderable: Boolean
) {
    // 3D Objects
    MESH_CUBE(NinkCategory.OBJECTS, R.string.nink_cube, true),
    MESH_SPHERE(NinkCategory.OBJECTS, R.string.nink_sphere, true),
    MESH_PLANE(NinkCategory.OBJECTS, R.string.nink_plane, true),
    MESH_CYLINDER(NinkCategory.OBJECTS, R.string.nink_cylinder, true),
    MESH_TRIANGLE(NinkCategory.OBJECTS, R.string.nink_triangle, true),

    // Characters
    CHARACTER_PLAYER(NinkCategory.CHARACTERS, R.string.nink_player, true),
    CHARACTER_NPC(NinkCategory.CHARACTERS, R.string.nink_npc, true),

    // Groups (pure organization, no forced behavior on children)
    GROUP(NinkCategory.GROUPS, R.string.nink_group, false),
    GROUP_ENVIRONMENT(NinkCategory.GROUPS, R.string.nink_environment_group, false),

    // Cameras
    CAMERA(NinkCategory.CAMERAS, R.string.nink_camera, true),

    // Lighting
    LIGHT_DIRECTIONAL(NinkCategory.LIGHTING, R.string.nink_directional_light, true),
    LIGHT_POINT(NinkCategory.LIGHTING, R.string.nink_point_light, true),
    LIGHT_SPOT(NinkCategory.LIGHTING, R.string.nink_spot_light, true),

    // Other
    EMPTY(NinkCategory.OTHER, R.string.nink_empty, false),
    CUSTOM(NinkCategory.OTHER, R.string.nink_custom, false);

    val isGroup: Boolean
        get() = this == GROUP || this == GROUP_ENVIRONMENT

    val isCamera: Boolean
        get() = this == CAMERA

    val isLight: Boolean
        get() = this == LIGHT_DIRECTIONAL || this == LIGHT_POINT || this == LIGHT_SPOT

    val hasChildrenByDefault: Boolean
        get() = isGroup

    companion object {
        /** Defensive mapping for serialized data with unknown type names. */
        fun fromSerializedName(raw: String?): NinkType =
            entries.firstOrNull { it.name == raw } ?: EMPTY
    }
}
