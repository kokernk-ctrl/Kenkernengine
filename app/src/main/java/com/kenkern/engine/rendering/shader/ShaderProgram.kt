package com.kenkern.engine.rendering.shader

import android.opengl.GLES30
import com.kenkern.engine.core.EngineLogger
import com.kenkern.engine.math.Matrix4

/**
 * A compiled and linked GLSL ES program with cached uniform locations.
 *
 * Compilation/linking failures are logged with the full info log and reported
 * through [lastError] so the editor diagnostics can surface them.
 */
class ShaderProgram(
    private val debugName: String,
    private val vertexSource: String,
    private val fragmentSource: String
) {

    private var programHandle: Int = 0
    private val uniformCache = HashMap<String, Int>()

    /** True when compile+link succeeded and the program is usable. */
    @Volatile
    var isUsable: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** Compiles and links. Safe to call again after context loss. */
    fun create(): Boolean {
        release()
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource, "$debugName.vertex")
        if (vertexShader == 0) return false
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource, "$debugName.fragment")
        if (fragmentShader == 0) {
            GLES30.glDeleteShader(vertexShader)
            return false
        }

        val handle = GLES30.glCreateProgram()
        if (handle == 0) {
            recordError("$debugName: glCreateProgram returned 0")
            return false
        }
        GLES30.glAttachShader(handle, vertexShader)
        GLES30.glAttachShader(handle, fragmentShader)
        GLES30.glLinkProgram(handle)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(handle, GLES30.GL_LINK_STATUS, linkStatus, 0)
        val programLog = GLES30.glGetProgramInfoLog(handle)
        GLES30.glDetachShader(handle, vertexShader)
        GLES30.glDetachShader(handle, fragmentShader)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)

        if (linkStatus[0] != GLES30.GL_TRUE) {
            GLES30.glDeleteProgram(handle)
            recordError("$debugName: link failed. Log: $programLog")
            return false
        }
        if (programLog.isNotBlank()) {
            EngineLogger.w(SUBSYSTEM, "$debugName linked with log: $programLog")
        }

        programHandle = handle
        uniformCache.clear()
        isUsable = true
        EngineLogger.i(SUBSYSTEM, "$debugName linked successfully (handle=$handle)")
        return true
    }

    fun use() {
        if (isUsable) GLES30.glUseProgram(programHandle)
    }

    fun uniformLocation(name: String): Int {
        val cached = uniformCache[name]
        if (cached != null) return cached
        val location = GLES30.glGetUniformLocation(programHandle, name)
        uniformCache[name] = location
        return location
    }

    fun setMat4(name: String, matrix: Matrix4) {
        val location = uniformLocation(name)
        if (location >= 0) {
            GLES30.glUniformMatrix4fv(location, 1, false, matrix.values, 0)
        }
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val location = uniformLocation(name)
        if (location >= 0) {
            GLES30.glUniform4f(location, x, y, z, w)
        }
    }

    fun setFloat(name: String, value: Float) {
        val location = uniformLocation(name)
        if (location >= 0) {
            GLES30.glUniform1f(location, value)
        }
    }

    fun release() {
        if (programHandle != 0) {
            GLES30.glDeleteProgram(programHandle)
            programHandle = 0
        }
        isUsable = false
        uniformCache.clear()
    }

    private fun compileShader(type: Int, source: String, name: String): Int {
        val handle = GLES30.glCreateShader(type)
        if (handle == 0) {
            recordError("$name: glCreateShader returned 0")
            return 0
        }
        GLES30.glShaderSource(handle, source)
        GLES30.glCompileShader(handle)
        val status = IntArray(1)
        GLES30.glGetShaderiv(handle, GLES30.GL_COMPILE_STATUS, status, 0)
        val log = GLES30.glGetShaderInfoLog(handle)
        if (status[0] != GLES30.GL_TRUE) {
            GLES30.glDeleteShader(handle)
            recordError("$name: compile failed. Log: $log\nSource:\n$source")
            return 0
        }
        if (log.isNotBlank()) {
            EngineLogger.w(SUBSYSTEM, "$name compiled with log: $log")
        }
        return handle
    }

    private fun recordError(message: String) {
        lastError = message
        EngineLogger.e(SUBSYSTEM, message)
    }

    private companion object {
        const val SUBSYSTEM = "Shader"
    }
}
