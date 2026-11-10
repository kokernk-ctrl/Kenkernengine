package com.kenkern.engine.project

import android.content.Context
import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.serialization.ProjectSerializer
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
    }
}
