package com.kenkern.engine.project

import android.content.Context
import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.serialization.ProjectSerializer
import com.kenkern.engine.serialization.SceneSerializer
import com.kenkern.engine.scene.SceneFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * File-system backed repository for KENKERN projects.
 *
 * All blocking IO happens inside this class and is always invoked from a
 * background dispatcher. Metadata is validated on load; missing or invalid
 * project data raises typed exceptions rather than returning partial state.
 */
class ProjectRepository(private val context: Context) {

    private val log = "ProjectRepo"

    fun baseDir(): File = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(projectId: String): File = File(baseDir(), projectId)

    fun metadataFile(projectId: String): File = File(projectDir(projectId), "project.json")

    fun scenesDir(projectId: String): File = File(projectDir(projectId), "scenes")

    fun prefabsDir(projectId: String): File = File(projectDir(projectId), "prefabs")

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    /** Lists valid projects on disk, most recently modified first. */
    suspend fun listProjects(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ProjectSummary>()
        val dirs = baseDir().listFiles { f -> f.isDirectory } ?: emptyArray()
        for (dir in dirs) {
            try {
                val project = readMetadata(dir)
                result.add(ProjectSummary(project.id, project.name, project.lastModifiedMillis))
            } catch (e: ProjectException) {
                EngineLogger.w(log, "Skipping invalid project directory ${dir.name}: ${e.message}")
            }
        }
        result.sortByDescending { it.lastModifiedMillis }
        result
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    suspend fun createProject(rawName: String): KenkernProject = withContext(Dispatchers.IO) {
        val name = rawName.trim().ifEmpty { "Untitled" }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val project = KenkernProject(
            id = id,
            name = name,
            createdAtMillis = now,
            lastModifiedMillis = now,
            engineVersion = EngineConfig.VERSION,
            entryScenePath = DEFAULT_ENTRY_SCENE
        )
        val dir = projectDir(id)
        check(dir.mkdirs() || dir.isDirectory) { "Could not create project directory" }
        File(dir, "scenes").mkdirs()
        writeMetadata(project)
        // Starter scene with localized default nink names.
        val defaultScene = SceneFactory.createDefaultScene { type -> context.getString(type.nameRes) }
        saveSceneJson(id, DEFAULT_ENTRY_SCENE, SceneSerializer.serialize(defaultScene))
        EngineLogger.i(log, "Created project '$name' ($id)")
        project
    }

    // ------------------------------------------------------------------
    // Load / save
    // ------------------------------------------------------------------

    suspend fun loadProject(projectId: String): KenkernProject = withContext(Dispatchers.IO) {
        readMetadata(projectDir(projectId))
    }

    suspend fun saveMetadata(project: KenkernProject): Unit = withContext(Dispatchers.IO) {
        writeMetadata(project.copy(lastModifiedMillis = System.currentTimeMillis()))
    }

    // ------------------------------------------------------------------
    // Scene persistence
    // ------------------------------------------------------------------

    fun sceneFile(projectId: String, scenePath: String): File = File(projectDir(projectId), scenePath)

    /** Returns the entry scene JSON, or null when the project has no scene yet. */
    suspend fun loadSceneJson(projectId: String, scenePath: String): String? = withContext(Dispatchers.IO) {
        val file = sceneFile(projectId, scenePath)
        if (file.isFile) file.readText() else null
    }

    suspend fun saveSceneJson(projectId: String, scenePath: String, json: String): Unit = withContext(Dispatchers.IO) {
        val file = sceneFile(projectId, scenePath)
        file.parentFile?.mkdirs()
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "Atomic scene rename failed" }
            }
        } catch (e: Exception) {
            throw ProjectSaveException("Failed writing scene $scenePath for $projectId", e)
        }
    }

    // ------------------------------------------------------------------
    // Prefab persistence
    // ------------------------------------------------------------------

    /** Lists prefab names (file names without extension), alphabetically. */
    suspend fun listPrefabs(projectId: String): List<String> = withContext(Dispatchers.IO) {
        val dir = prefabsDir(projectId)
        dir.listFiles { f -> f.isFile && f.extension == PREFAB_EXTENSION }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    suspend fun savePrefabJson(projectId: String, name: String, json: String): Unit = withContext(Dispatchers.IO) {
        val dir = prefabsDir(projectId)
        dir.mkdirs()
        try {
            File(dir, "$name.$PREFAB_EXTENSION").writeText(json)
        } catch (e: Exception) {
            throw ProjectSaveException("Failed writing prefab '$name' for $projectId", e)
        }
    }

    suspend fun loadPrefabJson(projectId: String, name: String): String? = withContext(Dispatchers.IO) {
        val file = File(prefabsDir(projectId), "$name.$PREFAB_EXTENSION")
        if (file.isFile) file.readText() else null
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    suspend fun deleteProject(projectId: String): Unit = withContext(Dispatchers.IO) {
        val dir = projectDir(projectId)
        if (!dir.exists()) {
            EngineLogger.w(log, "Delete requested for missing project $projectId")
            return@withContext
        }
        val deleted = dir.deleteRecursively()
        if (!deleted) {
            throw ProjectSaveException("Could not fully delete project directory ${dir.absolutePath}")
        }
        EngineLogger.i(log, "Deleted project $projectId")
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun readMetadata(dir: File): KenkernProject {
        if (!dir.isDirectory) throw ProjectNotFoundException("Project directory not found: ${dir.name}")
        val file = metadataFile(dir.name)
        if (!file.isFile) throw ProjectLoadException("project.json missing in ${dir.name}")
        val project = ProjectSerializer.fromJson(file.readText())
        if (project.id != dir.name) {
            throw ProjectFormatException("Metadata id '${project.id}' does not match directory '${dir.name}'")
        }
        return project
    }

    private fun writeMetadata(project: KenkernProject) {
        val dir = projectDir(project.id)
        if (!dir.isDirectory) throw ProjectSaveException("Project directory missing: ${project.id}")
        try {
            val tmp = File(dir, "project.json.tmp")
            tmp.writeText(ProjectSerializer.toJsonString(project))
            val target = metadataFile(project.id)
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Atomic metadata rename failed" }
            }
        } catch (e: Exception) {
            throw ProjectSaveException("Failed writing project.json for ${project.id}", e)
        }
    }

    companion object {
        const val DEFAULT_ENTRY_SCENE: String = "scenes/main.kscene"
        const val PREFAB_EXTENSION: String = "kprefab"
    }
}
