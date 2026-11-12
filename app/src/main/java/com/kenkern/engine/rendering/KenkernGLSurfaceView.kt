package com.kenkern.engine.rendering

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.kenkern.engine.core.CrashDiag
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.input.ViewportInputHandler
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLDisplay

/**
 * GLSurfaceView specialization hosting the KENKERN viewport.
 *
 *  - requests an OpenGL ES 3.0 context,
 *  - resolves the EGL config through a fallback chain instead of a single
 *    rigid spec: a driver that cannot match the preferred 8/8/8/8 + 24/8
 *    configuration must degrade gracefully, never kill the process,
 *  - keeps the EGL context on pause when possible (renderer still rebuilds
 *    resources on context loss),
 *  - forwards touch events to the [ViewportInputHandler].
 *
 * Note: EGL failures inside GLSurfaceView's GL thread are process-fatal by
 * design of the platform. Two layers above this view keep that from reaching
 * users: [EGLPreflight] validates config availability on the UI thread before
 * this view is created, and the editor composable wraps creation in a guard
 * that turns any residual failure into a UI error state.
 */
class KenkernGLSurfaceView(
    context: Context,
    private val inputHandler: ViewportInputHandler
) : GLSurfaceView(context) {

    /** Set when [attachRenderer] could not complete; consumed by the editor UI. */
    @Volatile
    var setupError: String? = null
        private set

    init {
        CrashDiag.step("GLSURFACEVIEW_INIT", "es3 context + fallback chooser + behind-window")
        setEGLContextClientVersion(3)
        setEGLConfigChooser(FallbackConfigChooser(SPECS))
        preserveEGLContextOnPause = true
        // The GL surface sits BEHIND the opaque window surface; the editor
        // leaves the viewport region unpainted so the framework punches it
        // transparent (standard SurfaceView contract — most driver-safe
        // configuration; the alternative translucent-window path is a known
        // low-end driver hazard and was reverted in 0.1.2).
        setZOrderOnTop(false)
    }

    fun attachRenderer(renderer: GLRenderer) {
        CrashDiag.step("GL_SET_RENDERER", "before")
        try {
            setRenderer(renderer)
            renderMode = RENDERMODE_CONTINUOUSLY
            CrashDiag.step("GL_SET_RENDERER", "attached, mode=CONTINUOUSLY")
        } catch (t: Throwable) {
            setupError = t.message ?: t.javaClass.simpleName
            CrashDiag.stepFailed("GL_SET_RENDERER", t)
            EngineLogger.e(SUBSYSTEM, "setRenderer failed", t)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(null)
        return inputHandler.onTouchEvent(event)
    }

    override fun onPause() {
        inputHandler.cancel()
        super.onPause()
    }

    /**
     * EGL config chooser with a graceful degradation chain. Every spec is
     * tried in order; the final fallback accepts any window-capable config so
     * chooser failure ("no configs match") cannot originate here.
     */
    private class FallbackConfigChooser(
        private val specs: List<IntArray>
    ) : GLSurfaceView.EGLConfigChooser {

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): javax.microedition.khronos.egl.EGLConfig {
            for ((specIndex, spec) in specs.withIndex()) {
                val numConfigs = IntArray(1)
                if (egl.eglChooseConfig(display, spec, null, 0, numConfigs) && numConfigs[0] > 0) {
                    val configs = arrayOfNulls<javax.microedition.khronos.egl.EGLConfig>(numConfigs[0])
                    if (egl.eglChooseConfig(display, spec, configs, configs.size, numConfigs) &&
                        numConfigs[0] > 0
                    ) {
                        CrashDiag.step("EGL_CHOOSE_CONFIG", "matched spec#$specIndex configs=${numConfigs[0]}")
                        return configs[0]!!
                    }
                }
                CrashDiag.step("EGL_CHOOSE_CONFIG", "spec#$specIndex no match (${egl.eglGetError()})")
            }
            // Last resort: first available config. EGLPreflight guarantees at
            // least one exists; this branch is a safety net, not a code path.
            val any = arrayOfNulls<javax.microedition.khronos.egl.EGLConfig>(1)
            val num = IntArray(1)
            egl.eglChooseConfig(display, LAST_RESORT_SPEC, any, any.size, num)
            if (num[0] > 0 && any[0] != null) {
                EngineLogger.w(SUBSYSTEM, "EGL degraded to last-resort config")
                return any[0]!!
            }
            throw IllegalArgumentException("No EGLConfig available on this device")
        }

        companion object {
            private val LAST_RESORT_SPEC = intArrayOf(EGL10.EGL_NONE)
        }
    }

    private companion object {
        const val SUBSYSTEM = "GLSurfaceView"

        /** ES3 renderable bit (EGL 1.5 spec value; not exposed by EGL10 constants). */
        const val EGL_OPENGL_ES3_BIT: Int = 0x0040

        val SPECS: List<IntArray> = listOf(
            // Preferred: RGBA8 + 24-bit depth + 8-bit stencil, ES3-capable.
            intArrayOf(
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_NONE
            ),
            // Same buffers, without the ES3 renderable constraint (drivers
            // that under-report the bit still deliver working ES3 contexts).
            intArrayOf(
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_NONE
            ),
            // RGBA8 + 16-bit depth, no stencil.
            intArrayOf(
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_NONE
            ),
            // RGB565 fallback for constrained low-end drivers.
            intArrayOf(
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_RED_SIZE, 5,
                EGL10.EGL_GREEN_SIZE, 6,
                EGL10.EGL_BLUE_SIZE, 5,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_NONE
            )
        )
    }
}
