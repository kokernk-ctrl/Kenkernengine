package com.kenkern.engine.rendering.camera

import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.math.Matrix4
import com.kenkern.engine.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Editor orbit camera.
 *
 * Touch input (UI thread) mutates the orbit state; the GL thread reads it
 * each frame to build the view matrix. All fields are volatile; compound
 * updates go through [this] monitor.
 */
class CameraController {

    /** Horizontal orbit angle, radians. */
    @Volatile
    var yawRadians: Float = DEFAULT_YAW

    /** Vertical orbit angle, radians, clamped to avoid poles. */
    @Volatile
    var pitchRadians: Float = DEFAULT_PITCH

    /** Distance from the orbit target. */
    @Volatile
    var distance: Float = DEFAULT_DISTANCE

    /** Orbit target position (world space). Guarded by `this`. */
    private val target = Vector3(0f, 0.5f, 0f)

    fun orbit(deltaXPixels: Float, deltaYPixels: Float) {
        yawRadians += deltaXPixels * ORBIT_SPEED_PER_PIXEL
        pitchRadians = (pitchRadians + deltaYPixels * ORBIT_SPEED_PER_PIXEL)
            .coerceIn(-MAX_PITCH, MAX_PITCH)
    }

    fun pan(deltaXPixels: Float, deltaYPixels: Float) {
        synchronized(this) {
            val speed = distance * PAN_SPEED_PER_PIXEL
            val cy = cos(yawRadians)
            val sy = sin(yawRadians)
            val cp = cos(pitchRadians)
            val sp = sin(pitchRadians)
            // Forward direction (eye -> target) implied by the orbit state.
            val fx = -cp * sy
            val fy = -sp
            val fz = -cp * cy
            // right = normalize(cross(forward, worldUp)) = (-fz, 0, fx) / |..|
            var rx = -fz
            var rz = fx
            val rl = kotlin.math.sqrt(rx * rx + rz * rz)
            if (rl > 1e-6f) {
                rx /= rl; rz /= rl
            } else {
                rx = 1f; rz = 0f
            }
            // up = cross(right, forward); right has no Y component.
            val ux = -rz * fy
            val uy = rz * fx - rx * fz
            val uz = rx * fy
            // Dragging right moves the scene right => target moves left.
            target.x -= rx * deltaXPixels * speed
            target.z -= rz * deltaXPixels * speed
            // Dragging down (dy > 0) moves the scene down => target moves up.
            target.x += ux * deltaYPixels * speed
            target.y += uy * deltaYPixels * speed
            target.z += uz * deltaYPixels * speed
            target.y = target.y.coerceIn(-50f, 50f)
        }
    }

    fun zoom(scaleFactor: Float) {
        distance = (distance / scaleFactor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    fun resetToDefault() {
        yawRadians = DEFAULT_YAW
        pitchRadians = DEFAULT_PITCH
        distance = DEFAULT_DISTANCE
        synchronized(this) {
            target.set(0f, 0.5f, 0f)
        }
    }

    /**
     * Builds the view matrix for the current orbit state. Called on the GL
     * thread once per frame.
     */
    fun computeView(out: Matrix4) {
        val tx: Float
        val ty: Float
        val tz: Float
        synchronized(this) {
            tx = target.x; ty = target.y; tz = target.z
        }
        val cp = cos(pitchRadians)
        val sp = sin(pitchRadians)
        val cy = cos(yawRadians)
        val sy = sin(yawRadians)
        viewEyeScratch.set(
            tx + distance * cp * sy,
            ty + distance * sp,
            tz + distance * cp * cy
        )
        viewTargetScratch.set(tx, ty, tz)
        Matrix4.lookAt(out, viewEyeScratch, viewTargetScratch, WORLD_UP)
    }

    companion object {
        private val WORLD_UP = Vector3(0f, 1f, 0f)
        private val viewEyeScratch = Vector3()
        private val viewTargetScratch = Vector3()

        private const val DEFAULT_YAW: Float = 0.6f
        private const val DEFAULT_PITCH: Float = 0.5f
        private const val DEFAULT_DISTANCE: Float = 9f
        private const val MIN_DISTANCE: Float = 1.2f
        private const val MAX_DISTANCE: Float = 80f
        private const val MAX_PITCH: Float = 1.5f
        private const val ORBIT_SPEED_PER_PIXEL: Float = 0.006f
        private const val PAN_SPEED_PER_PIXEL: Float = 0.0018f

        val defaultFovRadians: Float = Math.toRadians(EngineConfig.EDITOR_CAMERA_FOV_DEGREES.toDouble()).toFloat()
    }
}
