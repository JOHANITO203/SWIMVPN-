package com.swimvpn.app.ui.orb3d

import android.opengl.GLES30

internal class Orb3DShaderProgram {
    var programId: Int = 0
        private set
    var mvpLocation: Int = -1
        private set
    var pointModeLocation: Int = -1
        private set

    fun create() {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VertexShader)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FragmentShader)
        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val status = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            GLES30.glDeleteProgram(programId)
            error("Unable to link orb shader program: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        mvpLocation = GLES30.glGetUniformLocation(programId, "uMvp")
        pointModeLocation = GLES30.glGetUniformLocation(programId, "uPointMode")
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Unable to compile orb shader: $log")
        }
        return shader
    }

    private companion object {
        private const val VertexShader = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec4 aColor;
            layout(location = 2) in float aPointSize;

            uniform mat4 uMvp;

            out vec4 vColor;

            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vColor = aColor;
                gl_PointSize = aPointSize;
            }
        """

        private const val FragmentShader = """
            #version 300 es
            precision mediump float;

            in vec4 vColor;
            uniform int uPointMode;
            out vec4 fragColor;

            void main() {
                float alpha = vColor.a;
                if (uPointMode == 1) {
                    vec2 p = gl_PointCoord * 2.0 - 1.0;
                    float d = dot(p, p);
                    if (d > 1.0) {
                        discard;
                    }
                    alpha *= smoothstep(1.0, 0.10, d);
                }
                fragColor = vec4(vColor.rgb, alpha);
            }
        """
    }
}
