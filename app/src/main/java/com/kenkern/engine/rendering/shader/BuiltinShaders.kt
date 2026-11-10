package com.kenkern.engine.rendering.shader

/**
 * Built-in GLSL ES 3.00 shader sources.
 *
 * Vertex attribute locations are fixed by convention:
 *   location 0 -> aPos   (vec3)
 *   location 1 -> aColor (vec4)
 * so meshes can be bound once without querying attribute locations.
 */
object BuiltinShaders {

    /** Solid unlit color: per-draw uniform color, MVP transform. */
    const val SOLID_VERTEX: String =
        "#version 300 es\n" +
            "layout(location = 0) in vec3 aPos;\n" +
            "layout(location = 1) in vec4 aColor;\n" +
            "uniform mat4 uMVP;\n" +
            "void main() {\n" +
            "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n"

    const val SOLID_FRAGMENT: String =
        "#version 300 es\n" +
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = uColor;\n" +
            "}\n"

    /** Per-vertex color (used by the reference grid with alpha). */
    const val VERTEX_COLOR_VERTEX: String =
        "#version 300 es\n" +
            "layout(location = 0) in vec3 aPos;\n" +
            "layout(location = 1) in vec4 aColor;\n" +
            "uniform mat4 uMVP;\n" +
            "out vec4 vColor;\n" +
            "void main() {\n" +
            "    vColor = aColor;\n" +
            "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n"

    const val VERTEX_COLOR_FRAGMENT: String =
        "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec4 vColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = vColor;\n" +
            "}\n"
}
