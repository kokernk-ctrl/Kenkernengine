package com.kenkern.engine.rendering

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.kenkern.engine.input.ViewportInputHandler

/**
 * GLSurfaceView specialization hosting the KENKERN viewport.
 *
 *  - requests an OpenGL ES 3.0 context,
 *  - keeps the EGL context on pause when possible (renderer still rebuilds
 *    resources on context loss),
 *  - forwards touch events to the [ViewportInputHandler].
 */
class KenkernGLSurfaceView(
    context: Context,
    private val inputHandler: ViewportInputHandler
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 8)
        preserveEGLContextOnPause = true
        // The surface renders behind the Compose window surface; Compose
        // panels float above it and the viewport area stays transparent.
        setZOrderOnTop(false)
    }

    fun attachRenderer(renderer: GLRenderer) {
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(null)
        return inputHandler.onTouchEvent(event)
    }

    override fun onPause() {
        inputHandler.cancel()
        super.onPause()
    }
}
