package com.kenkern.engine.rendering

import android.opengl.EGL14
import com.kenkern.engine.core.EngineLogger

/**
 * UI-thread pre-flight validation of the EGL configuration the viewport needs.
 *
 * GLSurfaceView performs EGL config selection and window-surface creation on
 * the GL thread. A failure there is an *uncaught exception on that thread*,
 * which terminates the whole process — exactly the class of hard crash seen
 * on some drivers (low-end Mali/Unisoc) when a requested config cannot be
 * matched or the window surface cannot be created.
 *
 * This probe answers the same question ("is there at least one window-capable
 * EGL config matching our fallback chain?") on the UI thread FIRST, so a
 * broken/unsupported driver surfaces as a localized UI error state instead of
 * a crash. It never throws and never mutates process-global EGL state beyond
 * a thread-scoped initialize/release pair.
 */
object EGLPreflight {

    private const val SUBSYSTEM = "EGLPreflight"

    data class Result(val ok: Boolean, val detail: String)

    /**
     * Fallback chain, mirrors [KenkernGLSurfaceView]'s GL-thread chooser.
     * Size attributes match "at least", so each spec accepts stronger configs.
     * RENDERABLE_TYPE is intentionally NOT constrained (some drivers report
     * the ES3 bit incorrectly); context creation still requests ES 3.
     */
    private val SPECS: List<IntArray> = listOf(
        // Preferred: RGBA8 + 24-bit depth + 8-bit stencil.
        intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 24,
            EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE
        ),
        // Same buffers without stencil.
        intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_NONE
        ),
        // RGB565, no alpha/stencil, 16-bit depth (common low-end config).
        intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 5,
            EGL14.EGL_GREEN_SIZE, 6,
            EGL14.EGL_BLUE_SIZE, 5,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_NONE
        )
    )

    fun validate(): Result {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                EngineLogger.e(SUBSYSTEM, "eglGetDisplay returned EGL_NO_DISPLAY")
                return Result(false, "eglGetDisplay returned EGL_NO_DISPLAY")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                val detail = "eglInitialize failed (EGL error 0x${Integer.toHexString(EGL14.eglGetError())})"
                EngineLogger.e(SUBSYSTEM, detail)
                return Result(false, detail)
            }
            try {
                for (spec in SPECS) {
                    val numConfigs = IntArray(1)
                    // configs=null + config_size=0 -> count-only query.
                    val queried = EGL14.eglChooseConfig(display, spec, 0, null, 0, 0, numConfigs, 0)
                    if (queried && numConfigs[0] > 0) {
                        EngineLogger.i(SUBSYSTEM, "Compatible EGL config found — ${numConfigs[0]} match(es)")
                        return Result(true, "EGL config OK (${numConfigs[0]} match(es))")
                    }
                }
                val detail = "No compatible EGL window config on this driver"
                EngineLogger.e(SUBSYSTEM, detail)
                Result(false, detail)
            } finally {
                // Thread-scoped cleanup only; the default display stays valid
                // for the GL thread's own EGL usage.
                EGL14.eglReleaseThread()
            }
        } catch (t: Throwable) {
            EngineLogger.e(SUBSYSTEM, "EGL pre-flight probe failed", t)
            Result(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
