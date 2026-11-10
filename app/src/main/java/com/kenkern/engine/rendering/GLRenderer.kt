package com.kenkern.engine.rendering

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.math.Matrix4
import com.kenkern.engine.rendering.camera.CameraController
import com.kenkern.engine.rendering.mesh.Mesh
import com.kenkern.engine.rendering.mesh.MeshFactory
import com.kenkern.engine.rendering.shader.BuiltinShaders
import com.kenkern.engine.rendering.shader.ShaderProgram
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * KENKERN's OpenGL ES 3.0 renderer.
 *
 * Owns GL resources (programs + meshes) and draws whatever [RenderFramePublisher]
 * currently exposes, using the editor camera. All GL calls happen on the GL
 * thread managed by GLSurfaceView; per-frame allocations are avoided by
 * reusing scratch matrices.
 */
class GLRenderer(
    private val camera: CameraController,
    private val frameStats: FrameStats
) : GLSurfaceView.Renderer {

    private var solidProgram: ShaderProgram? = null
    private var vertexColorProgram: ShaderProgram? = null

    private val meshes = HashMap<MeshKind, Mesh>()
    private var gridMesh: Mesh? = null
    private var wireCubeMesh: Mesh? = null

    // Reusable matrices (GL thread only).
    private val projection = Matrix4()
    private val view = Matrix4()
    private val viewByModel = Matrix4()
    private val mvp = Matrix4()
    private val selectionMatrix = Matrix4()
    private val selectionScale = Matrix4()
    private val selectionFinal = Matrix4()
    private val gridModel = Matrix4()

    /** Non-null when initialization failed; surfaced by the editor UI. */
    @Volatile
    var initializationError: String? = null
        private set

    @Volatile
    private var surfaceWidth = 1

    @Volatile
    private var surfaceHeight = 1

    // ------------------------------------------------------------------
    // GLSurfaceView.Renderer
    // ------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new (or recreated) context invalidates all previous handles.
        initializationError = null
        GLCapabilities.probe()

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE) // editor meshes stay visible from both sides
        GLES30.glClearColor(EngineConfig.CLEAR_R, EngineConfig.CLEAR_G, EngineConfig.CLEAR_B, EngineConfig.CLEAR_A)

        // Programs
        solidProgram = ShaderProgram("solid", BuiltinShaders.SOLID_VERTEX, BuiltinShaders.SOLID_FRAGMENT)
        vertexColorProgram = ShaderProgram("vertexColor", BuiltinShaders.VERTEX_COLOR_VERTEX, BuiltinShaders.VERTEX_COLOR_FRAGMENT)
        if (!solidProgram!!.create()) {
            initializationError = solidProgram!!.lastError ?: "solid shader failed"
        }
        if (!vertexColorProgram!!.create()) {
            initializationError = vertexColorProgram!!.lastError ?: "vertexColor shader failed"
        }

        // Meshes (buffers upload lazily on first draw within this context).
        meshes.clear()
        buildMeshes()
        EngineLogger.i(SUBSYSTEM, "Surface created; renderer initialized. error=$initializationError")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = if (width > 0) width else 1
        surfaceHeight = if (height > 0) height else 1
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        Matrix4.perspective(
            projection,
            CameraController.defaultFovRadians,
            surfaceWidth.toFloat() / surfaceHeight.toFloat(),
            EngineConfig.NEAR_PLANE,
            EngineConfig.FAR_PLANE
        )
        EngineLogger.i(SUBSYSTEM, "Surface changed: ${surfaceWidth}x$surfaceHeight")
    }

    override fun onDrawFrame(gl: GL10?) {
        frameStats.onFrame()

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val frame = RenderFramePublisher.current
        camera.computeView(view)

        drawGrid()

        val solid = solidProgram ?: return
        if (!solid.isUsable) return
        solid.use()

        for (item in frame?.items ?: emptyList()) {
            val mesh = meshes[item.meshKind] ?: continue
            if (!mesh.isUploaded) mesh.upload()
            if (!mesh.isUploaded) continue

            viewByModel.set(item.modelMatrix)
            viewByModel.multiply(view, viewByModel)
            mvp.multiply(projection, viewByModel)
            solid.setMat4(UNIFORM_MVP, mvp)
            solid.setVec4(UNIFORM_COLOR, item.colorRgba[0], item.colorRgba[1], item.colorRgba[2], item.colorRgba[3])
            mesh.bind()
            mesh.draw()
            GLES30.glBindVertexArray(0)
        }

        drawSelection(frame)
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun drawGrid() {
        if (!RenderFramePublisher.gridVisible) return
        val program = vertexColorProgram ?: return
        if (!program.isUsable) return
        val mesh = gridMesh ?: return
        if (!mesh.isUploaded) mesh.upload()
        if (!mesh.isUploaded) return

        program.use()
        viewByModel.set(gridModel)
        viewByModel.multiply(view, viewByModel)
        mvp.multiply(projection, viewByModel)
        program.setMat4(UNIFORM_MVP, mvp)
        mesh.bind()
        mesh.draw()
        GLES30.glBindVertexArray(0)
    }

    private fun drawSelection(frame: RenderFrameData?) {
        val matrix = frame?.selectionMatrix ?: return
        val mesh = wireCubeMesh ?: return
        val program = solidProgram ?: return
        if (!program.isUsable || !mesh.isUploaded && !mesh.uploadAndCheck()) return

        // Slightly larger than the object so the outline does not z-fight.
        Matrix4.scaling(selectionScale, SELECTION_SCALE, SELECTION_SCALE, SELECTION_SCALE)
        selectionMatrix.set(matrix)
        selectionFinal.multiply(selectionMatrix, selectionScale)
        viewByModel.set(selectionFinal)
        viewByModel.multiply(view, viewByModel)
        mvp.multiply(projection, viewByModel)

        program.use()
        program.setMat4(UNIFORM_MVP, mvp)
        program.setVec4(UNIFORM_COLOR, ACCENT_R, ACCENT_G, ACCENT_B, 1f)
        GLES30.glBindVertexArray(0)
        mesh.bind()
        mesh.draw()
        GLES30.glBindVertexArray(0)
    }

    private fun Mesh.uploadAndCheck(): Boolean {
        upload()
        return isUploaded
    }

    private fun buildMeshes() {
        val builder = listOf(
            MeshKind.TRIANGLE to MeshFactory.triangle(0f, 0.70f, 0.62f, 1f),
            MeshKind.PLANE to MeshFactory.plane(),
            MeshKind.CUBE to MeshFactory.cube(),
            MeshKind.SPHERE to MeshFactory.sphere(0f, 0.482f, 1f),
            MeshKind.CYLINDER to MeshFactory.cylinder(0.78f, 0.80f, 0.84f),
            MeshKind.PYRAMID to MeshFactory.pyramid(0.95f, 0.80f, 0.20f)
        )
        builder.forEach { (kind, mesh) ->
            meshes[kind] = mesh
        }
        gridMesh = MeshFactory.grid()
        wireCubeMesh = MeshFactory.wireCube()
    }

    private companion object {
        const val SUBSYSTEM = "Renderer"
        const val UNIFORM_MVP = "uMVP"
        const val UNIFORM_COLOR = "uColor"
        const val SELECTION_SCALE = 1.08f
        const val ACCENT_R = 0f
        const val ACCENT_G = 0.482f
        const val ACCENT_B = 1f
    }
}
