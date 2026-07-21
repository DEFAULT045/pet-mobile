package com.llz121517.meapet.live2d

import android.opengl.GLES20
import android.util.Log

/**
 * Creates and holds the GL program for sprite rendering.
 * Shader source is embedded directly (no external shader files needed).
 */
class Live2dSpriteShader : AutoCloseable {
    companion object {
        private const val TAG = "Live2dSpriteShader"
    }

    val programId: Int = createShader()

    override fun close() {
        GLES20.glDeleteProgram(programId)
    }

    private fun createShader(): Int {
        val vertexShaderId = compileShader(
            Live2dDefine.ShaderSource.VERTEX,
            GLES20.GL_VERTEX_SHADER
        )
        if (!checkShaderCompiled(vertexShaderId, "vertex")) return 0

        val fragmentShaderId = compileShader(
            Live2dDefine.ShaderSource.FRAGMENT,
            GLES20.GL_FRAGMENT_SHADER
        )
        if (!checkShaderCompiled(fragmentShaderId, "fragment")) return 0

        val programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertexShaderId)
        GLES20.glAttachShader(programId, fragmentShaderId)
        GLES20.glLinkProgram(programId)

        if (!checkProgramLinked(programId)) return 0

        GLES20.glUseProgram(programId)
        GLES20.glDeleteShader(vertexShaderId)
        GLES20.glDeleteShader(fragmentShaderId)

        return programId
    }

    private fun compileShader(source: String, shaderType: Int): Int {
        val shaderId = GLES20.glCreateShader(shaderType)
        GLES20.glShaderSource(shaderId, source)
        GLES20.glCompileShader(shaderId)
        return shaderId
    }

    private fun checkShaderCompiled(shaderId: Int, name: String): Boolean {
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shaderId)
            Log.e(TAG, "Shader compile error [$name]: $info")
            GLES20.glDeleteShader(shaderId)
            return false
        }
        return true
    }

    private fun checkProgramLinked(programId: Int): Boolean {
        val linked = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(programId)
            Log.e(TAG, "Program link error: $info")
            GLES20.glDeleteProgram(programId)
            return false
        }
        return true
    }
}
