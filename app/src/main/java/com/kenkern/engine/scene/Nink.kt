package com.kenkern.engine.scene

/**
 * The fundamental scene entity of the KENKERN Engine.
 *
 * A Nink is anything that exists in a scene: 3D objects, characters,
 * cameras, lights, environment elements, groups and empty entities.
 *
 * Identity is [id]; display [name] is never used as identity. Children hold
 * transforms relative to their parent. Groups are organizational only — they
 * never force behavior onto children.
 */
class Nink(
    val id: NinkId,
    var name: String,
    val type: NinkType,
    val transform: Transform = Transform(),
    val components: MutableList<NinkComponent> = mutableListOf(),
    var parentId: NinkId? = null,
    val childIds: MutableList<NinkId> = mutableListOf(),
    var active: Boolean = true,
    var visible: Boolean = true,
    var locked: Boolean = false,
    val customProperties: MutableMap<String, String> = mutableMapOf()
) {

    fun component(): NinkComponent? = components.firstOrNull()

    inline fun <reified T : NinkComponent> componentOfType(): T? =
        components.filterIsInstance<T>().firstOrNull()

    fun addComponent(component: NinkComponent) {
        components.add(component)
    }

    override fun toString(): String = "Nink(id=$id, name=$name, type=$type)"
}
