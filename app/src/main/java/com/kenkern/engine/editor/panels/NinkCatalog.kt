package com.kenkern.engine.editor.panels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kenkern.engine.R
import com.kenkern.engine.scene.NinkType

/**
 * Extensible catalog that drives the Add Nink menu.
 *
 * Future Nink types are registered here (see [register]) without rewriting
 * the menu UI — the menu renders whatever the catalog contains, grouped by
 * category.
 */
enum class NinkMenuCategory(val titleRes: Int) {
    OBJECTS(R.string.add_menu_3d_objects),
    CHARACTERS(R.string.add_menu_characters),
    GROUPS(R.string.add_menu_groups),
    CAMERAS(R.string.add_menu_cameras),
    LIGHTING(R.string.add_menu_lighting),
    OTHER(R.string.add_menu_other)
}

data class NinkCatalogEntry(
    val type: NinkType,
    val category: NinkMenuCategory,
    val icon: ImageVector
)

object NinkCatalog {

    private val entries = mutableListOf<NinkCatalogEntry>()

    fun register(entry: NinkCatalogEntry) {
        entries.add(entry)
    }

    fun all(): List<NinkCatalogEntry> = entries.toList()

    fun byCategory(): Map<NinkMenuCategory, List<NinkCatalogEntry>> =
        entries.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })

    @Composable
    fun defaultName(type: NinkType): String = stringResource(type.nameRes)

    init {
        register(NinkCatalogEntry(NinkType.MESH_CUBE, NinkMenuCategory.OBJECTS, Icons.Filled.Category))
        register(NinkCatalogEntry(NinkType.MESH_SPHERE, NinkMenuCategory.OBJECTS, Icons.Filled.Circle))
        register(NinkCatalogEntry(NinkType.MESH_PLANE, NinkMenuCategory.OBJECTS, Icons.Filled.CropLandscape))
        register(NinkCatalogEntry(NinkType.MESH_CYLINDER, NinkMenuCategory.OBJECTS, Icons.Filled.TripOrigin))
        register(NinkCatalogEntry(NinkType.MESH_TRIANGLE, NinkMenuCategory.OBJECTS, Icons.Filled.ChangeHistory))
        register(NinkCatalogEntry(NinkType.CHARACTER_PLAYER, NinkMenuCategory.CHARACTERS, Icons.Filled.Person))
        register(NinkCatalogEntry(NinkType.CHARACTER_NPC, NinkMenuCategory.CHARACTERS, Icons.Filled.PersonOutline))
        register(NinkCatalogEntry(NinkType.GROUP, NinkMenuCategory.GROUPS, Icons.Filled.Folder))
        register(NinkCatalogEntry(NinkType.GROUP_ENVIRONMENT, NinkMenuCategory.GROUPS, Icons.Filled.Public))
        register(NinkCatalogEntry(NinkType.CAMERA, NinkMenuCategory.CAMERAS, Icons.Filled.Videocam))
        register(NinkCatalogEntry(NinkType.LIGHT_DIRECTIONAL, NinkMenuCategory.LIGHTING, Icons.Filled.WbSunny))
        register(NinkCatalogEntry(NinkType.LIGHT_POINT, NinkMenuCategory.LIGHTING, Icons.Filled.Lightbulb))
        register(NinkCatalogEntry(NinkType.LIGHT_SPOT, NinkMenuCategory.LIGHTING, Icons.Filled.Highlight))
        register(NinkCatalogEntry(NinkType.EMPTY, NinkMenuCategory.OTHER, Icons.Filled.RadioButtonUnchecked))
        register(NinkCatalogEntry(NinkType.CUSTOM, NinkMenuCategory.OTHER, Icons.Filled.Extension))
    }

    /** Icon used for prefab entries in the Add menu. */
    val prefabIcon: ImageVector = Icons.Filled.Widgets
}
