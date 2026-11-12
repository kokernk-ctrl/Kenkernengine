package com.kenkern.engine.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kenkern.engine.R
import com.kenkern.engine.core.CrashDiag
import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.math.Matrix4
import com.kenkern.engine.project.ProjectRepository
import com.kenkern.engine.rendering.FrameStats
import com.kenkern.engine.rendering.GLCapabilities
import com.kenkern.engine.rendering.GLRenderer
import com.kenkern.engine.rendering.MeshKind
import com.kenkern.engine.rendering.NinkRenderColors
import com.kenkern.engine.rendering.RenderFrameData
import com.kenkern.engine.rendering.RenderFramePublisher
import com.kenkern.engine.rendering.camera.CameraController
import com.kenkern.engine.scene.Nink
import com.kenkern.engine.scene.NinkComponent
import com.kenkern.engine.scene.NinkId
import com.kenkern.engine.scene.NinkType
import com.kenkern.engine.scene.Scene
import com.kenkern.engine.scene.SceneChangeReason
import com.kenkern.engine.scene.SceneChangeListener
import com.kenkern.engine.scene.SceneEditor
import com.kenkern.engine.scene.SceneFactory
import com.kenkern.engine.scene.SceneOps
import com.kenkern.engine.scene.Transform
import com.kenkern.engine.scene.commands.CommandStack
import com.kenkern.engine.serialization.SceneSerializer
import com.kenkern.engine.serialization.SerializationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Flat row model of the Hierarchy panel (rebuilt from the registry on
 * structural changes; never a parallel source of truth).
 */
data class HierarchyRow(
    val id: NinkId,
    val name: String,
    val type: NinkType,
    val depth: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
    val visible: Boolean,
    val active: Boolean,
    val locked: Boolean
)

/**
 * State holder of the KENKERN editor screen.
 *
 * Owns the loaded [Scene], the [SceneEditor]/[CommandStack] pair and all
 * UI-facing state. The Hierarchy panel renders [hierarchyRows] (a projection
 * of the registry); the viewport consumes the published render payload;
 * the Inspector reads live scene values keyed by revisions.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val LOG_TAG = "EditorVM"
    }

    private val repository = ProjectRepository(application)
    private val appContext = application

    // ------------------------------------------------------------------
    // Project state
    // ------------------------------------------------------------------

    var projectId by mutableStateOf<String?>(null)
        private set
    var projectName by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    /** Localized, user-facing description of the open failure (when [loadFailed]). */
    var loadErrorMessage by mutableStateOf<String?>(null)
        private set

    // ------------------------------------------------------------------
    // Scene state
    // ------------------------------------------------------------------

    private var sceneInternal: Scene? = null
    private var commandStackInternal: CommandStack? = null

    var sceneEditor: SceneEditor? = null
        private set

    var sceneName by mutableStateOf("")
        private set

    /** Bumps whenever structure/flags change (hierarchy + inspector resync). */
    var structureRevision by mutableStateOf(0)
        private set

    /** Bumps on transform changes not originating from the Inspector. */
    var transformRevision by mutableStateOf(0)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // ------------------------------------------------------------------
    // Editor UI state
    // ------------------------------------------------------------------

    var selection by mutableStateOf<NinkId?>(null)
    val expandedIds = mutableStateMapOf<NinkId, Boolean>()
    var searchQuery by mutableStateOf("")
    var hierarchyVisible by mutableStateOf(true)
    var inspectorVisible by mutableStateOf(true)

    var hierarchyRows by mutableStateOf<List<HierarchyRow>>(emptyList())
        private set
    var prefabNames by mutableStateOf<List<String>>(emptyList())
        private set

    var isDirty by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var toastMessage by mutableStateOf<String?>(null)
        private set

    // ------------------------------------------------------------------
    // Rendering state
    // ------------------------------------------------------------------

    val frameStats = FrameStats()
    val camera = CameraController()

    var fpsOverlayVisible by mutableStateOf(EngineConfig.showFpsByDefault)

    var renderInitError by mutableStateOf<String?>(null)
        private set
    var glSummary by mutableStateOf("")
        private set
    var fps by mutableStateOf(0)
        private set
    var frameMs by mutableStateOf(0f)
        private set

    private var rendererRef: GLRenderer? = null

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private var loaded = false
    private var entryScenePath: String = ProjectRepository.DEFAULT_ENTRY_SCENE
    private var projectRef: com.kenkern.engine.project.KenkernProject? = null
    private var autosaveJob: Job? = null
    private var inspectorTransformEdit = false
    private var flushOnExitStarted = false

    private val sceneListener = SceneChangeListener { reason, ninkId ->
        onSceneChanged(reason, ninkId)
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Idempotent project + scene load. */
    fun ensureLoaded(id: String) {
        if (loaded) return
        loaded = true
        projectId = id
        CrashDiag.step("EDITOR_LOAD_BEGIN", "projectId=$id")
        viewModelScope.launch {
            try {
                val project = withContext(Dispatchers.IO) { repository.loadProject(id) }
                CrashDiag.step("EDITOR_PROJECT_LOADED", "name='${project.name}' entry='${project.entryScenePath}'")
                projectRef = project
                entryScenePath = project.entryScenePath
                val sceneJson = withContext(Dispatchers.IO) {
                    repository.loadSceneJson(id, project.entryScenePath)
                }
                CrashDiag.step("EDITOR_SCENE_JSON", "bytes=${sceneJson?.length ?: -1}")
                when (
                    val opened = SceneOpenResolver.open(sceneJson) {
                        SceneFactory.createDefaultScene { type -> appContext.getString(type.nameRes) }
                    }
                ) {
                    is SceneOpenResolver.SceneOpenOutcome.Failed -> throw opened.error

                    is SceneOpenResolver.SceneOpenOutcome.Ready -> {
                        opened.recoveredFrom?.let { error ->
                            EngineLogger.e(LOG_TAG, "Scene file invalid — a default scene replaced it", error)
                            CrashDiag.step("EDITOR_SCENE_RECOVERED", "${error.message}")
                            errorMessage = appContext.getString(
                                R.string.error_scene_load_recovered,
                                error.message ?: "invalid scene"
                            )
                        }
                        val scene = opened.scene
                        scene.addListener(sceneListener)
                        if (scene.mainCameraId == null) {
                            // Load-time repair; no change notification required.
                            SceneOps.findFirstCamera(scene)?.let { scene.mainCameraId = it }
                        }
                        sceneInternal = scene
                        commandStackInternal = CommandStack(scene) { publishUndoState() }
                        sceneEditor = SceneEditor(scene, commandStackInternal!!)
                        projectName = project.name
                        sceneName = scene.name
                        isLoading = false
                        rebuildRows()
                        publishRenderData()
                        refreshPrefabs()
                        CrashDiag.step(
                            "EDITOR_LOAD_OK",
                            "ninks=${scene.registry.ninkCount()} camera=${scene.mainCameraId != null} rows=${hierarchyRows.size}"
                        )
                    }
                }
            } catch (e: Exception) {
                // Opening failed: record the real exception, keep the project on
                // disk untouched and let the user return to the launcher — the
                // editor must never silently die or show a broken shell.
                EngineLogger.e(LOG_TAG, "Failed to open project $id", e)
                CrashDiag.stepFailed("EDITOR_LOAD_FAILED", e)
                isLoading = false
                loadFailed = true
                loadErrorMessage = appContext.getString(
                    R.string.error_project_load,
                    e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    private fun publishUndoState() {
        canUndo = commandStackInternal?.canUndo ?: false
        canRedo = commandStackInternal?.canRedo ?: false
    }

    private fun onSceneChanged(reason: SceneChangeReason, ninkId: NinkId?) {
        when (reason) {
            SceneChangeReason.TRANSFORM -> {
                publishRenderData()
                if (!inspectorTransformEdit) {
                    transformRevision++
                }
            }

            SceneChangeReason.SCENE_REPLACED -> {
                structureRevision++
                transformRevision++
                rebuildRows()
                publishRenderData()
            }

            else -> {
                structureRevision++
                rebuildRows()
                publishRenderData()
            }
        }
        publishUndoState()
        isDirty = true
        scheduleAutosave()
    }

    // ------------------------------------------------------------------
    // Hierarchy projections
    // ------------------------------------------------------------------

    fun selectedNink(): Nink? = selection?.let { sceneInternal?.registry?.nink(it) }

    /** Direct registry accessor for UI panels (read-only usage). */
    fun selectedNinkById(id: NinkId): Nink? = sceneInternal?.registry?.nink(id)

    /**
     * Valid reparent targets for "Move to Group": every group nink that is
     * not the nink itself, inside its subtree, or locked. Returns
     * (id, displayName) pairs sorted by name.
     */
    fun groupTargetsFor(id: NinkId): List<Pair<NinkId, String>> {
        val scene = sceneInternal ?: return emptyList()
        val registry = scene.registry
        return registry.allNinks()
            .filter { it.type.isGroup && it.id != id && !it.locked && !registry.subtreeContains(id, it.id) }
            .map { it.id to it.name }
            .sortedBy { it.second }
    }

    fun mainCameraId(): NinkId? = sceneInternal?.mainCameraId

    fun isMainCamera(id: NinkId): Boolean = sceneInternal?.mainCameraId == id

    fun refreshSearch(query: String) {
        searchQuery = query
        rebuildRows()
    }

    fun toggleExpanded(id: NinkId) {
        expandedIds[id] = !(expandedIds[id] ?: true)
        rebuildRows()
    }

    fun expandAll() {
        val scene = sceneInternal ?: return
        for (nink in scene.registry.allNinks()) {
            if (nink.childIds.isNotEmpty()) expandedIds[nink.id] = true
        }
        rebuildRows()
    }

    fun collapseAll() {
        val scene = sceneInternal ?: return
        for (nink in scene.registry.allNinks()) {
            if (nink.childIds.isNotEmpty()) expandedIds[nink.id] = false
        }
        rebuildRows()
    }

    private fun rebuildRows() {
        val scene = sceneInternal ?: return
        val query = searchQuery.trim().lowercase()
        val rows = mutableListOf<HierarchyRow>()

        fun visit(id: NinkId, depth: Int) {
            val nink = scene.registry.nink(id) ?: return
            val children = scene.registry.childrenOf(id)
            val expanded = if (query.isEmpty()) expandedIds[id] ?: true else true
            rows.add(
                HierarchyRow(
                    id = id,
                    name = nink.name,
                    type = nink.type,
                    depth = depth,
                    hasChildren = children.isNotEmpty(),
                    expanded = expanded,
                    visible = nink.visible,
                    active = nink.active,
                    locked = nink.locked
                )
            )
            if (!expanded) return
            for (child in children) {
                if (query.isEmpty() || subtreeMatches(child, query)) {
                    visit(child, depth + 1)
                }
            }
        }

        for (root in scene.registry.rootIds()) {
            if (query.isEmpty() || subtreeMatches(root, query)) {
                visit(root, 0)
            }
        }
        hierarchyRows = rows
    }

    private fun subtreeMatches(id: NinkId, query: String): Boolean {
        val scene = sceneInternal ?: return false
        val nink = scene.registry.nink(id) ?: return false
        if (nink.name.lowercase().contains(query)) return true
        return scene.registry.childrenOf(id).any { subtreeMatches(it, query) }
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    fun select(id: NinkId?) {
        selection = id
        publishRenderData()
    }

    // ------------------------------------------------------------------
    // Nink operations (delegating to the SceneEditor)
    // ------------------------------------------------------------------

    fun addNink(type: NinkType) {
        val editor = sceneEditor ?: return
        val parent = selection
        val id = editor.addNink(type, parent, appContext.getString(type.nameRes))
        if (parent != null) expandedIds[parent] = true
        select(id)
        rebuildRows()
    }

    fun addChildNink(type: NinkType, parentId: NinkId) {
        val editor = sceneEditor ?: return
        val id = editor.addChildNink(type, parentId, appContext.getString(type.nameRes))
        expandedIds[parentId] = true
        select(id)
        rebuildRows()
    }

    fun renameNink(id: NinkId, newName: String) {
        val ok = sceneEditor?.renameNink(id, newName) ?: false
        if (!ok) {
            errorMessage = appContext.getString(R.string.error_invalid_name)
        }
    }

    fun duplicateNink(id: NinkId) {
        val newId = sceneEditor?.duplicateNink(id) ?: return
        select(newId)
        rebuildRows()
    }

    fun deleteNink(id: NinkId) {
        val editor = sceneEditor ?: return
        val registry = sceneInternal?.registry ?: return
        val nink = registry.nink(id)
        if (nink?.locked == true) {
            errorMessage = appContext.getString(R.string.error_locked_nink)
            return
        }
        val selectionSnapshot = selection
        val deleted = editor.deleteNink(id)
        if (deleted && selectionSnapshot != null && !registry.contains(selectionSnapshot)) {
            selection = null
            publishRenderData()
        }
        rebuildRows()
    }

    fun reparentNink(id: NinkId, newParent: NinkId?) {
        sceneEditor?.reparentNink(id, newParent)
        rebuildRows()
    }

    fun moveUp(id: NinkId) {
        sceneEditor?.moveUp(id)
        rebuildRows()
    }

    fun moveDown(id: NinkId) {
        sceneEditor?.moveDown(id)
        rebuildRows()
    }

    fun setActiveCamera(id: NinkId) {
        sceneEditor?.setActiveCamera(id)
        rebuildRows()
    }

    fun ungroup(id: NinkId) {
        sceneEditor?.ungroup(id)
        rebuildRows()
    }

    fun setFlags(id: NinkId, active: Boolean? = null, visible: Boolean? = null, locked: Boolean? = null) {
        sceneEditor?.setFlags(id, active, visible, locked)
        rebuildRows()
    }

    // ------------------------------------------------------------------
    // Inspector writes
    // ------------------------------------------------------------------

    /** Continuous transform write from the Inspector (typing). */
    fun setTransformFromInspector(id: NinkId, transform: Transform) {
        val editor = sceneEditor ?: return
        inspectorTransformEdit = true
        try {
            editor.setTransformContinuous(id, transform)
        } finally {
            inspectorTransformEdit = false
        }
        publishRenderData()
    }

    /** Live camera component write (component data; undo for components lands in a later block). */
    fun updateCameraComponent(id: NinkId, fovDegrees: Float? = null, nearPlane: Float? = null, farPlane: Float? = null) {
        val nink = sceneInternal?.registry?.nink(id) ?: return
        val camera = nink.componentOfType<NinkComponent.Camera>() ?: return
        val updated = camera.copy(
            fovDegrees = fovDegrees?.coerceIn(1f, 170f) ?: camera.fovDegrees,
            nearPlane = nearPlane?.coerceAtLeast(0.01f) ?: camera.nearPlane,
            farPlane = farPlane?.coerceAtLeast(camera.nearPlane + 0.1f) ?: camera.farPlane
        )
        val index = nink.components.indexOfFirst { it is NinkComponent.Camera }
        if (index >= 0) {
            nink.components[index] = updated
            sceneInternal?.notifyChanged(SceneChangeReason.FLAGS, id)
        }
    }

    /** Live light component write. */
    fun updateLightComponent(id: NinkId, intensity: Float? = null, range: Float? = null) {
        val nink = sceneInternal?.registry?.nink(id) ?: return
        val light = nink.componentOfType<NinkComponent.Light>() ?: return
        val updated = light.copy(
            intensity = intensity?.coerceAtLeast(0f) ?: light.intensity,
            range = range?.coerceAtLeast(0f) ?: light.range
        )
        val index = nink.components.indexOfFirst { it is NinkComponent.Light }
        if (index >= 0) {
            nink.components[index] = updated
            sceneInternal?.notifyChanged(SceneChangeReason.FLAGS, id)
        }
    }

    // ------------------------------------------------------------------
    // Prefabs
    // ------------------------------------------------------------------

    fun createPrefab(id: NinkId, rawName: String) {
        val scene = sceneInternal ?: return
        val pid = projectId ?: return
        val name = rawName.trim()
        if (name.isEmpty()) {
            errorMessage = appContext.getString(R.string.error_invalid_name)
            return
        }
        val json = SceneSerializer.serializeSubtree(scene, id, name)
        if (json == null) {
            errorMessage = appContext.getString(R.string.error_generic, "prefab serialization failed")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.savePrefabJson(pid, sanitizePrefabName(name), json) }
                refreshPrefabs()
                toastMessage = appContext.getString(R.string.toast_prefab_created, name)
            } catch (e: Exception) {
                errorMessage = appContext.getString(R.string.error_generic, e.message ?: "prefab save failed")
            }
        }
    }

    fun instantiatePrefab(name: String) {
        val editor = sceneEditor ?: return
        val id = projectId ?: return
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { repository.loadPrefabJson(id, name) }
                if (json == null) {
                    errorMessage = appContext.getString(R.string.error_generic, "prefab '$name' missing")
                    return@launch
                }
                val template = SceneSerializer.parsePrefab(json)
                val parentId = selection
                editor.instantiatePrefab(template, parentId, name)
                if (parentId != null) expandedIds[parentId] = true
                rebuildRows()
            } catch (e: SerializationException) {
                errorMessage = appContext.getString(R.string.error_generic, e.message ?: "invalid prefab")
            }
        }
    }

    private fun refreshPrefabs() {
        val id = projectId ?: return
        viewModelScope.launch {
            try {
                prefabNames = withContext(Dispatchers.IO) { repository.listPrefabs(id) }
            } catch (e: Exception) {
                prefabNames = emptyList()
            }
        }
    }

    private fun sanitizePrefabName(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9-_ ]"), "").trim().replace(' ', '_').ifEmpty { "prefab" }

    // ------------------------------------------------------------------
    // Undo / redo
    // ------------------------------------------------------------------

    fun undo() {
        sceneEditor?.undo()
        rebuildRows()
    }

    fun redo() {
        sceneEditor?.redo()
        rebuildRows()
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private fun scheduleAutosave() {
        if (!EngineConfig.autosaveEnabled) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(EngineConfig.autosaveDelayMs)
            performSave(showToast = false)
        }
    }

    fun saveNow() {
        autosaveJob?.cancel()
        viewModelScope.launch { performSave(showToast = true) }
    }

    private suspend fun performSave(showToast: Boolean) {
        val scene = sceneInternal ?: return
        val id = projectId ?: return
        isSaving = true
        try {
            val json = SceneSerializer.serialize(scene)
            withContext(Dispatchers.IO) {
                repository.saveSceneJson(id, entryScenePath, json)
                projectRef?.let { repository.saveMetadata(it) }
            }
            isDirty = false
            if (showToast) {
                toastMessage = appContext.getString(R.string.toast_scene_saved)
            }
        } catch (e: Exception) {
            errorMessage = appContext.getString(R.string.error_scene_save, e.message ?: "unknown")
        } finally {
            isSaving = false
        }
    }

    // ------------------------------------------------------------------
    // Rendering (Phase 1 behavior + live scene payload)
    // ------------------------------------------------------------------

    fun attachRenderer(renderer: GLRenderer) {
        rendererRef = renderer
    }

    fun pollRenderStats() {
        fps = frameStats.fps
        frameMs = frameStats.averageFrameMs
        glSummary = GLCapabilities.summary
        val renderer = rendererRef
        if (renderer != null && renderInitError == null && renderer.initializationError != null) {
            renderInitError = renderer.initializationError
        }
    }

    fun toggleFpsOverlay() {
        fpsOverlayVisible = !fpsOverlayVisible
    }

    fun toggleHierarchy() {
        hierarchyVisible = !hierarchyVisible
    }

    fun toggleInspector() {
        inspectorVisible = !inspectorVisible
    }

    fun onRenderResumed() {
        frameStats.reset()
    }

    fun dismissError() {
        errorMessage = null
    }

    fun dismissToast() {
        toastMessage = null
    }

    /** Publishes the render payload consumed by the GL thread. */
    private fun publishRenderData() {
        val scene = sceneInternal ?: run {
            publishDemoFrame()
            return
        }
        val registry = scene.registry
        val items = ArrayList<RenderFrameData.Item>()
        var selectionMatrix: FloatArray? = null
        var selectionKey: String? = null
        val world = Matrix4()
        val scratch = Matrix4()

        fun visit(id: NinkId, parentActive: Boolean) {
            val nink = registry.nink(id) ?: return
            val isActive = parentActive && nink.active
            if (isActive && nink.visible && nink.type.isRenderable) {
                registry.worldMatrix(id, world, scratch)
                items.add(
                    RenderFrameData.Item(
                        meshKind = MeshKind.fromNinkType(nink.type.name),
                        modelMatrix = world.values.copyOf(),
                        colorRgba = NinkRenderColors.colorForType(nink.type.name).copyOf()
                    )
                )
                if (id == selection) {
                    selectionMatrix = world.values.copyOf()
                    selectionKey = id.value
                }
            }
            for (child in registry.childrenOf(id)) visit(child, isActive)
        }

        for (root in registry.rootIds()) visit(root, true)

        RenderFramePublisher.current = RenderFrameData(items, selectionKey, selectionMatrix)
        RenderFramePublisher.gridVisible = true
    }

    /** Fallback payload before a scene finishes loading. */
    private fun publishDemoFrame() {
        val cubeMatrix = Matrix4()
        Matrix4.trs(cubeMatrix, 1.6f, 0.5f, -0.4f, 0f, 0.44f, 0f, 1f, 1f, 1f)
        RenderFramePublisher.current = RenderFrameData(
            listOf(
                RenderFrameData.Item(
                    meshKind = MeshKind.CUBE,
                    modelMatrix = cubeMatrix.values.copyOf(),
                    colorRgba = NinkRenderColors.colorForType("MESH_CUBE").copyOf()
                )
            ),
            null,
            null
        )
        RenderFramePublisher.gridVisible = true
    }

    /**
     * Best-effort save invoked by the editor screen right before leaving
     * (back button, exit button, fatal-load screen). Mirrors [onCleared];
     * guarded against double-execution. Never blocks the exit: the write is
     * atomic and happens on IO.
     */
    fun flushOnExit() {
        if (flushOnExitStarted) return
        val scene = sceneInternal ?: return
        val id = projectId ?: return
        if (!isDirty) return
        flushOnExitStarted = true
        autosaveJob?.cancel()
        val json = SceneSerializer.serialize(scene)
        val path = entryScenePath
        isSaving = true
        GlobalScope.launch(Dispatchers.IO) {
            try {
                repository.saveSceneJson(id, path, json)
                isDirty = false
                EngineLogger.i(LOG_TAG, "Save-on-exit flush completed for $id")
            } catch (e: Exception) {
                EngineLogger.e(LOG_TAG, "Save-on-exit flush failed for $id", e)
            } finally {
                isSaving = false
            }
        }
    }

    /** Final flush when the editor closes; best-effort, never blocks exit. */
    override fun onCleared() {
        if (!flushOnExitStarted) {
            flushOnExit()
        }
        sceneInternal?.removeListener(sceneListener)
        super.onCleared()
    }
}
