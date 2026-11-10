package com.kenkern.engine.rendering.mesh

import android.opengl.GLES30
import com.kenkern.engine.core.EngineLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Static GPU mesh: interleaved vertex buffer + 16-bit index buffer + VAO.
 *
 * Vertex layout (interleaved, stride [FLOATS_PER_VERTEX] floats):
 *   position: 3 floats (offset 0)
 *   color:    4 floats (offset 3)  — RGBA 0..1
 *
 * Attribute locations match the convention in [com.kenkern.engine.rendering.shader.BuiltinShaders].
 */
class Mesh(
    private val debugName: String,
    vertices: FloatArray,
    indices: ShortArray,
    private val drawMode: Int = GLES30.GL_TRIANGLES
) {

    private val vertexData: FloatBuffer = toDirectFloatBuffer(vertices)
    private val indexData: ShortBuffer = toDirectShortBuffer(indices)
    private val indexCount: Int = indices.size

    private var vaoHandle: Int = 0
    private var vboHandle: Int = 0
    private var iboHandle: Int = 0

    @Volatile
    var isUploaded: Boolean = false
        private set

    /** Uploads buffers to the current GL context (idempotent per context). */
    fun upload() {
        if (isUploaded) return

        val vao = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        val ibo = IntArray(1)
        GLES30.glGenBuffers(1, ibo, 0)

        if (vao[0] == 0 || vbo[0] == 0 || ibo[0] == 0) {
            EngineLogger.e(SUBSYSTEM, "$debugName: buffer generation failed")
            return
        }

        GLES30.glBindVertexArray(vao[0])

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        vertexData.rewind()
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexData.capacity() * FLOAT_SIZE_BYTES,
            vertexData,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        indexData.rewind()
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indexData.capacity() * SHORT_SIZE_BYTES,
            indexData,
            GLES30.GL_STATIC_DRAW
        )

        // position
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, FLOATS_PER_VERTEX * FLOAT_SIZE_BYTES, 0)
        // color
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, FLOATS_PER_VERTEX * FLOAT_SIZE_BYTES, 3 * FLOAT_SIZE_BYTES)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        vaoHandle = vao[0]
        vboHandle = vbo[0]
        iboHandle = ibo[0]
        isUploaded = true
        EngineLogger.i(SUBSYSTEM, "$debugName uploaded (vao=${vao[0]}, indices=$indexCount)")
    }

    fun bind() {
        GLES30.glBindVertexArray(vaoHandle)
    }

    fun draw() {
        GLES30.glDrawElements(drawMode, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
    }

    fun drawAndUnbind() {
        draw()
        GLES30.glBindVertexArray(0)
    }

    /**
     * Marks the mesh as needing re-upload. Called when the GL context was
     * lost: old handles are invalid and simply replaced on next upload.
     */
    fun invalidate() {
        isUploaded = false
    }

    fun release() {
        if (vaoHandle != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vaoHandle), 0)
        if (vboHandle != 0) GLES30.glDeleteBuffers(1, intArrayOf(vboHandle), 0)
        if (iboHandle != 0) GLES30.glDeleteBuffers(1, intArrayOf(iboHandle), 0)
        vaoHandle = 0; vboHandle = 0; iboHandle = 0
        isUploaded = false
    }

    private fun toDirectFloatBuffer(array: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(array.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(array)

    private fun toDirectShortBuffer(array: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(array.size * SHORT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(array)

    companion object {
        private const val SUBSYSTEM = "Mesh"
        const val FLOATS_PER_VERTEX = 7
        const val FLOAT_SIZE_BYTES = 4
        const val SHORT_SIZE_BYTES = 2
    }
}
