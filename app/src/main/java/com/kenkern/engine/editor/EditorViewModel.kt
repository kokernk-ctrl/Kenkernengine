package com.kenkern.engine.editor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kenkern.engine.core.EngineConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder of the editor screen.
 *
 * Phase 1 scope: project identity, editor camera, frame statistics and a
 * demo render payload (triangle + cube + reference grid) proving the GL
 * pipeline end to end. Phase 2 replaces the demo payload with live scene
 * data and adds the Nink panels.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)

    var projectId by mutableStateOf<String?>(null)
        private set
    var projectName by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    val frameStats = FrameStats()
    val camera = CameraController()

    var fpsOverlayVisible by mutableStateOf(EngineConfig.showFpsByDefault)

    /** Non-null when the GL context failed to initialize (shader errors etc). */
    var renderInitError by mutableStateOf<String?>(null)
        private set

    var glSummary by mutableStateOf("")
        private set
    var fps by mutableStateOf(0)
        private set
    var frameMs by mutableStateOf(0f)
        private set

    private var rendererRef: GLRenderer? = null
    private var ensureLoadedCalled = false

    /** Idempotent project load; safe to call from composition on every recomposition. */
    fun ensureLoaded(id: String) {
        if (ensureLoadedCalled) return
        ensureLoadedCalled = true
        projectId = id
        viewModelScope.launch {
            try {
                val project = withContext(Dispatchers.IO) { repository.loadProject(id) }
                projectName = project.name
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                loadFailed = true
            }
        }
    }

    fun attachRenderer(renderer: GLRenderer) {
        rendererRef = renderer
    }

    /** Polls render statistics into Compose state (called ~4x/second). */
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

    fun onRenderResumed() {
        frameStats.reset()
    }

    init {
        publishDemoFrame()
    }

    // ------------------------------------------------------------------
    // Phase 1 demo payload (replaced by live scene data in Phase 2)
    // ------------------------------------------------------------------

    private fun publishDemoFrame() {
        val triangleMatrix = Matrix4()
        Matrix4.trs(triangleMatrix, -2.4f, 0.9f, 0.4f, 0f, 0.6f, 0f, 1.3f, 1.3f, 1.3f)
        val cubeMatrix = Matrix4()
        Matrix4.trs(cubeMatrix, 1.6f, 0.5f, -0.4f, 0f, 0.44f, 0f, 1f, 1f, 1f)

        val items = listOf(
            RenderFrameData.Item(
                meshKind = MeshKind.TRIANGLE,
                modelMatrix = triangleMatrix.values.copyOf(),
                colorRgba = NinkRenderColors.colorForType("MESH_TRIANGLE").copyOf()
            ),
            RenderFrameData.Item(
                meshKind = MeshKind.CUBE,
                modelMatrix = cubeMatrix.values.copyOf(),
                colorRgba = NinkRenderColors.colorForType("MESH_CUBE").copyOf()
            )
        )
        RenderFramePublisher.current = RenderFrameData(items, null, null)
        RenderFramePublisher.gridVisible = true
    }
}
