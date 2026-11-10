package com.kenkern.engine.serialization

import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.project.KenkernProject
import com.kenkern.engine.project.ProjectFormatException
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON (de)serialization of [KenkernProject] metadata.
 *
 * Pure Kotlin on top of org.json so it is unit-testable on the JVM.
 * Loading is defensive: malformed or partially valid files raise
 * [ProjectFormatException] instead of corrupting editor state.
 */
object ProjectSerializer {

    const val FORMAT_ID: String = "kenkern-project"
    const val FORMAT_VERSION: Int = 1

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_CREATED = "createdAtMillis"
    private const val KEY_MODIFIED = "lastModifiedMillis"
    private const val KEY_ENGINE_VERSION = "engineVersion"
    private const val KEY_ENTRY_SCENE = "entryScene"

    fun toJson(project: KenkernProject): JSONObject = JSONObject().apply {
        put(KEY_FORMAT, FORMAT_ID)
        put(KEY_VERSION, FORMAT_VERSION)
        put(KEY_ID, project.id)
        put(KEY_NAME, project.name)
        put(KEY_CREATED, project.createdAtMillis)
        put(KEY_MODIFIED, project.lastModifiedMillis)
        put(KEY_ENGINE_VERSION, project.engineVersion)
        put(KEY_ENTRY_SCENE, project.entryScenePath)
    }

    fun toJsonString(project: KenkernProject): String = toJson(project).toString(2)

    fun fromJson(text: String): KenkernProject {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw ProjectFormatException("project.json is not valid JSON", e)
        }
        val format = json.optString(KEY_FORMAT)
        if (format != FORMAT_ID) {
            throw ProjectFormatException("Unexpected format id '$format' (expected '$FORMAT_ID')")
        }
        val version = json.optInt(KEY_VERSION, -1)
        if (version != FORMAT_VERSION) {
            throw ProjectFormatException("Unsupported project format version $version (expected $FORMAT_VERSION)")
        }
        val id = json.optString(KEY_ID)
        if (id.isBlank()) throw ProjectFormatException("Project metadata is missing a valid id")
        val name = json.optString(KEY_NAME).ifBlank { "Untitled" }
        val created = json.optLong(KEY_CREATED, System.currentTimeMillis())
        val modified = json.optLong(KEY_MODIFIED, created)
        val engineVersion = json.optString(KEY_ENGINE_VERSION, EngineConfig.VERSION)
        val entryScene = json.optString(KEY_ENTRY_SCENE, "scenes/main.kscene")
        return KenkernProject(
            id = id,
            name = name,
            createdAtMillis = created,
            lastModifiedMillis = modified,
            engineVersion = engineVersion,
            entryScenePath = entryScene
        )
    }
}
