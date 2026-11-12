package com.kenkern.engine.serialization

import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.scene.Nink
import com.kenkern.engine.scene.NinkComponent
import com.kenkern.engine.scene.NinkId
import com.kenkern.engine.scene.NinkRegistry
import com.kenkern.engine.scene.NinkType
import com.kenkern.engine.scene.Scene
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Raised when scene/prefab data cannot be parsed. */
class SerializationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JSON (de)serialization for scenes and prefabs.
 *
 * Pure Kotlin over org.json (unit-testable on the JVM). Loading is strictly
 * defensive: malformed files raise [SerializationException], while recoverable
 * problems (unknown nink types, missing parents, duplicate ids, cycles) are
 * repaired deterministically and logged — a scene always loads into a valid
 * hierarchy or fails loudly with a clear message.
 */
object SceneSerializer {

    const val SCENE_FORMAT: String = "kenkern-scene"
    const val PREFAB_FORMAT: String = "kenkern-prefab"
    const val FORMAT_VERSION: Int = 1

    private const val TAG = "SceneSerializer"

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val KEY_NAME = "name"
    private const val KEY_MAIN_CAMERA = "mainCamera"
    private const val KEY_NINKS = "ninks"

    private const val KEY_ID = "id"
    private const val KEY_PARENT = "parent"
    private const val KEY_TYPE = "type"
    private const val KEY_ACTIVE = "active"
    private const val KEY_VISIBLE = "visible"
    private const val KEY_LOCKED = "locked"
    private const val KEY_TRANSFORM = "transform"
    private const val KEY_COMPONENTS = "components"
    private const val KEY_PROPS = "props"

    private const val KEY_POS = "position"
    private const val KEY_ROT = "rotation"
    private const val KEY_SCALE = "scale"

    private const val KEY_COMPONENT_KIND = "kind"
    private const val KIND_CAMERA = "camera"
    private const val KIND_LIGHT = "light"

    // ------------------------------------------------------------------
    // Scene serialization
    // ------------------------------------------------------------------

    fun serialize(scene: Scene): String {
        val json = JSONObject()
        json.put(KEY_FORMAT, SCENE_FORMAT)
        json.put(KEY_VERSION, FORMAT_VERSION)
        json.put(KEY_NAME, scene.name)
        json.put(KEY_MAIN_CAMERA, scene.mainCameraId?.value ?: JSONObject.NULL)
        json.put(KEY_NINKS, encodeNinks(preOrderAll(scene)))
        return json.toString(2)
    }

    fun deserialize(text: String): Scene {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw SerializationException("Scene is not valid JSON", e)
        }
        val format = json.optString(KEY_FORMAT)
        if (format != SCENE_FORMAT) {
            throw SerializationException("Unexpected scene format '$format' (expected '$SCENE_FORMAT')")
        }
        val version = json.optInt(KEY_VERSION, -1)
        if (version != FORMAT_VERSION) {
            throw SerializationException("Unsupported scene format version $version")
        }

        val scene = Scene()
        scene.name = json.optString(KEY_NAME).ifBlank { Scene.DEFAULT_NAME }

        // LinkedHashMap keeps file order so parents are processed before
        // their children during hierarchy reconstruction.
        val idMap = LinkedHashMap<String, Nink>()
        val ninkArray = json.optJSONArray(KEY_NINKS) ?: JSONArray()
        val rootsInOrder = mutableListOf<NinkId>()

        for (i in 0 until ninkArray.length()) {
            val entry = ninkArray.optJSONObject(i) ?: continue
            val nink = decodeNink(entry) ?: continue
            if (idMap.containsKey(nink.id.value)) {
                EngineLogger.w(TAG, "Duplicate nink id ${nink.id} skipped (name=${nink.name})")
                continue
            }
            idMap[nink.id.value] = nink
        }

        // Link parents defensively.
        for (entry in idMap.values) {
            val rawParent = entry.parentId?.value
            val parent = rawParent?.takeIf { it != entry.id.value }?.let { idMap[it] }
            if (rawParent != null && parent == null) {
                EngineLogger.w(TAG, "Nink ${entry.id} references missing parent '$rawParent' — promoted to root")
            }
            entry.parentId = parent?.id
            entry.childIds.clear()
        }
        // Rebuild child order from file order (parents appear before children).
        for (entry in idMap.values) {
            val parent = entry.parentId
            if (parent == null) {
                if (!scene.registry.contains(entry.id)) {
                    scene.registry.add(entry, null)
                    rootsInOrder.add(entry.id)
                }
            } else if (scene.registry.contains(parent) && !scene.registry.contains(entry.id)) {
                scene.registry.add(entry, parent)
            } else if (!scene.registry.contains(entry.id)) {
                scene.registry.add(entry, null)
                rootsInOrder.add(entry.id)
            }
        }

        breakCycles(scene)

        scene.mainCameraId = if (json.isNull(KEY_MAIN_CAMERA)) {
            null
        } else {
            NinkId.from(json.optString(KEY_MAIN_CAMERA))?.takeIf { scene.registry.contains(it) }
        }
        val mainCameraRaw = scene.mainCameraId?.value
        if (scene.mainCameraId == null && !json.isNull(KEY_MAIN_CAMERA) && json.optString(KEY_MAIN_CAMERA).isNotBlank()) {
            EngineLogger.w(TAG, "Main camera '$mainCameraRaw' not found in scene")
        }
        return scene
    }

    // ------------------------------------------------------------------
    // Prefab serialization
    // ------------------------------------------------------------------

    fun serializeSubtree(scene: Scene, rootId: NinkId, prefabName: String): String? {
        val ninks = mutableListOf<Nink>()
        val root = scene.registry.nink(rootId) ?: return null
        collectPreOrder(scene, root, ninks)
        val json = JSONObject()
        json.put(KEY_FORMAT, PREFAB_FORMAT)
        json.put(KEY_VERSION, FORMAT_VERSION)
        json.put(KEY_NAME, prefabName)
        val array = JSONArray()
        for (nink in ninks) {
            val encoded = encodeNink(nink)
            // The prefab root is stored without a parent reference.
            if (nink.id == rootId) encoded.put(KEY_PARENT, JSONObject.NULL)
            array.put(encoded)
        }
        json.put(KEY_NINKS, array)
        return json.toString(2)
    }

    /** Parses prefab template ninks (root has parent = null). */
    fun parsePrefab(text: String): List<Nink> {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw SerializationException("Prefab is not valid JSON", e)
        }
        val format = json.optString(KEY_FORMAT)
        if (format != PREFAB_FORMAT) {
            throw SerializationException("Unexpected prefab format '$format'")
        }
        val ninkArray = json.optJSONArray(KEY_NINKS) ?: JSONArray()
        val result = mutableListOf<Nink>()
        val byId = HashMap<String, Nink>()
        for (i in 0 until ninkArray.length()) {
            val nink = decodeNink(ninkArray.optJSONObject(i) ?: continue) ?: continue
            if (byId.containsKey(nink.id.value)) continue
            byId[nink.id.value] = nink
            result.add(nink)
        }
        // Drop children whose parents did not survive parsing.
        return result.filter { nink ->
            val parent = nink.parentId
            parent == null || byId.containsKey(parent.value)
        }
    }

    // ------------------------------------------------------------------
    // Encoding / decoding
    // ------------------------------------------------------------------

    private fun preOrderAll(scene: Scene): List<Nink> {
        val result = mutableListOf<Nink>()
        for (rootId in scene.registry.rootIds()) {
            scene.registry.nink(rootId)?.let { collectPreOrder(scene, it, result) }
        }
        return result
    }

    private fun collectPreOrder(scene: Scene, nink: Nink, out: MutableList<Nink>) {
        val visited = HashSet<String>()
        fun visit(current: Nink) {
            if (!visited.add(current.id.value)) return // defensive: corrupted cycle must not recurse forever
            out.add(current)
            for (childId in current.childIds.toList()) {
                scene.registry.nink(childId)?.let { visit(it) }
            }
        }
        visit(nink)
    }

    private fun encodeNinks(ninks: List<Nink>): JSONArray {
        val array = JSONArray()
        for (nink in ninks) array.put(encodeNink(nink))
        return array
    }

    private fun encodeNink(nink: Nink): JSONObject {
        val json = JSONObject()
        json.put(KEY_ID, nink.id.value)
        json.put(KEY_NAME, nink.name)
        json.put(KEY_TYPE, nink.type.name)
        json.put(KEY_PARENT, nink.parentId?.value ?: JSONObject.NULL)
        json.put(KEY_ACTIVE, nink.active)
        json.put(KEY_VISIBLE, nink.visible)
        json.put(KEY_LOCKED, nink.locked)

        val transform = JSONObject()
        transform.put(KEY_POS, floatArray(nink.transform.position.x, nink.transform.position.y, nink.transform.position.z))
        transform.put(KEY_ROT, floatArray(nink.transform.rotationEulerRadians.x, nink.transform.rotationEulerRadians.y, nink.transform.rotationEulerRadians.z))
        transform.put(KEY_SCALE, floatArray(nink.transform.scale.x, nink.transform.scale.y, nink.transform.scale.z))
        json.put(KEY_TRANSFORM, transform)

        val components = JSONArray()
        for (component in nink.components) {
            val encoded = JSONObject()
            when (component) {
                is NinkComponent.Camera -> {
                    encoded.put(KEY_COMPONENT_KIND, KIND_CAMERA)
                    encoded.put("fov", component.fovDegrees.toDouble())
                    encoded.put("near", component.nearPlane.toDouble())
                    encoded.put("far", component.farPlane.toDouble())
                }

                is NinkComponent.Light -> {
                    encoded.put(KEY_COMPONENT_KIND, KIND_LIGHT)
                    encoded.put("r", component.r.toDouble())
                    encoded.put("g", component.g.toDouble())
                    encoded.put("b", component.b.toDouble())
                    encoded.put("intensity", component.intensity.toDouble())
                    encoded.put("range", component.range.toDouble())
                }
            }
            components.put(encoded)
        }
        json.put(KEY_COMPONENTS, components)

        val props = JSONObject()
        for ((key, value) in nink.customProperties) props.put(key, value)
        json.put(KEY_PROPS, props)
        return json
    }

    private fun decodeNink(entry: JSONObject): Nink? {
        val id = NinkId.from(entry.optString(KEY_ID)) ?: NinkId.generate()
        val name = entry.optString(KEY_NAME).ifBlank { "Nink" }
        val typeRaw = entry.optString(KEY_TYPE, null as String?)
        val type = NinkType.fromSerializedName(typeRaw)
        if (typeRaw != null && type == NinkType.EMPTY && typeRaw != "EMPTY") {
            EngineLogger.w(TAG, "Unknown nink type '$typeRaw' — loaded as EMPTY (name=$name)")
        }

        val nink = Nink(id = id, name = name, type = type)
        nink.parentId = if (entry.isNull(KEY_PARENT)) null else NinkId.from(entry.optString(KEY_PARENT))
        nink.active = entry.optBoolean(KEY_ACTIVE, true)
        nink.visible = entry.optBoolean(KEY_VISIBLE, true)
        nink.locked = entry.optBoolean(KEY_LOCKED, false)

        val transform = entry.optJSONObject(KEY_TRANSFORM)
        if (transform != null) {
            readFloat3(transform.optJSONArray(KEY_POS))?.let { nink.transform.position.set(it[0], it[1], it[2]) }
            readFloat3(transform.optJSONArray(KEY_ROT))?.let { nink.transform.rotationEulerRadians.set(it[0], it[1], it[2]) }
            readFloat3(transform.optJSONArray(KEY_SCALE))?.let { f3 ->
                val sx = f3[0].takeIf { it != 0f } ?: 1f
                val sy = f3[1].takeIf { it != 0f } ?: 1f
                val sz = f3[2].takeIf { it != 0f } ?: 1f
                nink.transform.scale.set(sx, sy, sz)
            }
        }

        val components = entry.optJSONArray(KEY_COMPONENTS) ?: JSONArray()
        for (i in 0 until components.length()) {
            val componentJson = components.optJSONObject(i) ?: continue
            when (componentJson.optString(KEY_COMPONENT_KIND)) {
                KIND_CAMERA -> nink.addComponent(
                    NinkComponent.Camera(
                        fovDegrees = componentJson.optDouble("fov", 60.0).toFloat().coerceIn(1f, 170f),
                        nearPlane = componentJson.optDouble("near", 0.1).toFloat().coerceAtLeast(0.01f),
                        farPlane = componentJson.optDouble("far", 100.0).toFloat().coerceAtLeast(1f)
                    )
                )

                KIND_LIGHT -> nink.addComponent(
                    NinkComponent.Light(
                        r = componentJson.optDouble("r", 1.0).toFloat(),
                        g = componentJson.optDouble("g", 1.0).toFloat(),
                        b = componentJson.optDouble("b", 1.0).toFloat(),
                        intensity = componentJson.optDouble("intensity", 1.0).toFloat().coerceAtLeast(0f),
                        range = componentJson.optDouble("range", 20.0).toFloat().coerceAtLeast(0f)
                    )
                )
            }
        }

        val props = entry.optJSONObject(KEY_PROPS)
        if (props != null) {
            for (key in props.keys()) {
                nink.customProperties[key] = props.optString(key)
            }
        }
        return nink
    }

    private fun readFloat3(array: JSONArray?): FloatArray? {
        if (array == null || array.length() < 3) return null
        return floatArrayOf(
            array.optDouble(0, 0.0).toFloat(),
            array.optDouble(1, 0.0).toFloat(),
            array.optDouble(2, 0.0).toFloat()
        )
    }

    private fun floatArray(x: Float, y: Float, z: Float): JSONArray =
        JSONArray().put(x.toDouble()).put(y.toDouble()).put(z.toDouble())

    /** Detects parent/child cycles created by corrupt data and breaks them. */
    private fun breakCycles(scene: Scene) {
        val registry = scene.registry
        val visited = HashSet<NinkId>()
        val roots = registry.rootIds()
        fun visit(id: NinkId) {
            if (!visited.add(id)) return
            for (child in registry.childrenOf(id)) visit(child)
        }
        roots.forEach { visit(it) }

        // Any nink not reachable from a root is part of a cycle (or orphan
        // ring). Promote it to root to break the cycle.
        var promoted = true
        while (promoted) {
            promoted = false
            for (nink in registry.allNinks().toList()) {
                if (nink.id !in visited) {
                    EngineLogger.w(TAG, "Cycle detected at nink ${nink.id} — promoted to root")
                    registry.promoteToRoot(nink.id)
                    visit(nink.id)
                    promoted = true
                    break
                }
            }
        }
    }
}
