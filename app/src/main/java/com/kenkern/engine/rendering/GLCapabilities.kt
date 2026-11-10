package com.kenkern.engine.rendering

import android.opengl.GLES30
import com.kenkern.engine.core.EngineConfig
import com.kenkern.engine.core.EngineLogger

/**
 * Runtime OpenGL capability probe.
 *
 * Validates that the context actually delivers the required OpenGL ES 3.x
 * (some drivers report GLES strings that differ from the requested EGL
 * client version) and records useful diagnostic information.
 */
object GLCapabilities {

    private const val SUBSYSTEM = "GLCaps"

    @Volatile
    var summary: String = ""
        private set

    @Volatile
    var glesMajorVersion: Int = 0
        private set

    @Volatile
    var isRequiredVersionSatisfied: Boolean = false
        private set

    @Volatile
    var rendererName: String = ""
        private set

    fun probe() {
        val versionString = GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"
        val rendererString = GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"
        val vendorString = GLES30.glGetString(GLES30.GL_VENDOR) ?: "unknown"
        val major = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, major, 0)
        val maxTexture = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexture, 0)

        glesMajorVersion = if (major[0] > 0) major[0] else parseMajorFromVersionString(versionString)
        rendererName = "$rendererString ($vendorString)"
        isRequiredVersionSatisfied = glesMajorVersion >= EngineConfig.REQUIRED_GLES_MAJOR_VERSION
        summary = "OpenGL ES ${glesMajorVersion} · $rendererString"

        EngineLogger.i(
            SUBSYSTEM,
            "GL_VERSION=$versionString | GL_RENDERER=$rendererString | GL_VENDOR=$vendorString | " +
                "GLES_MAJOR=$glesMajorVersion | MAX_TEXTURE_SIZE=${maxTexture[0]} | " +
                "required(>=${EngineConfig.REQUIRED_GLES_MAJOR_VERSION}) satisfied=$isRequiredVersionSatisfied"
        )

        if (!isRequiredVersionSatisfied) {
            EngineLogger.e(
                SUBSYSTEM,
                "Device reported GLES $glesMajorVersion but KENKERN requires ES ${EngineConfig.REQUIRED_GLES_MAJOR_VERSION}+"
            )
        }
    }

    private fun parseMajorFromVersionString(version: String): Int {
        // Typical format: "OpenGL ES 3.2 NVIDIA 530.41" or "OpenGL ES 3.0 V@..."
        val match = Regex("OpenGL ES (\\d+)\\.").find(version)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
