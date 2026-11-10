package com.kenkern.engine.input

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.rendering.camera.CameraController

/**
 * Translates viewport touch gestures into editor camera motion.
 *
 *  - One finger drag: orbit
 *  - Two finger drag: pan
 *  - Pinch: zoom
 *
 * Attached to the GLSurfaceView; runs on the Android UI thread.
 */
class ViewportInputHandler(private val camera: CameraController, viewContext: android.content.Context) {

    private enum class Mode { IDLE, ORBIT, PAN }

    private var mode = Mode.IDLE
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        viewContext,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                camera.zoom(detector.scaleFactor)
                return true
            }
        }
    )

    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = Mode.ORBIT
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    mode = Mode.PAN
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    lastX = midX
                    lastY = midY
                }
            }

            MotionEvent.ACTION_MOVE -> {
                when {
                    scaleDetector.isInProgress -> {
                        // Pinch owns the gesture; keep anchors fresh.
                        lastX = event.x
                        lastY = event.y
                    }

                    mode == Mode.ORBIT && event.pointerCount == 1 -> {
                        camera.orbit(event.x - lastX, event.y - lastY)
                        lastX = event.x
                        lastY = event.y
                    }

                    mode == Mode.PAN && event.pointerCount >= 2 -> {
                        val midX = (event.getX(0) + event.getX(1)) / 2f
                        val midY = (event.getY(0) + event.getY(1)) / 2f
                        camera.pan(midX - lastX, midY - lastY)
                        lastX = midX
                        lastY = midY
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // When one of two fingers lifts, fall back to orbit with a
                // fresh anchor to avoid a jump.
                if (event.pointerCount <= 2 && mode == Mode.PAN) {
                    mode = Mode.ORBIT
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    if (remaining < event.pointerCount) {
                        lastX = event.getX(remaining)
                        lastY = event.getY(remaining)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = Mode.IDLE
            }
        }
        return true
    }

    fun cancel() {
        mode = Mode.IDLE
        EngineLogger.i(SUBSYSTEM, "Viewport input cancelled")
    }

    private companion object {
        const val SUBSYSTEM = "ViewportInput"
    }
}
