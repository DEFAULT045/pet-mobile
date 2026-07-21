package com.llz121517.meapet.live2d

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Simple quad sprite for rendering a texture via GL.
 */
class Live2dSprite(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    private val textureId: Int,
    programId: Int
) {
    private val rect = Rect().apply {
        left = x - width * 0.5f
        right = x + width * 0.5f
        up = y + height * 0.5f
        down = y - height * 0.5f
    }

    private val positionLocation: Int = GLES20.glGetAttribLocation(programId, "position")
    private val uvLocation: Int = GLES20.glGetAttribLocation(programId, "uv")
    private val textureLocation: Int = GLES20.glGetUniformLocation(programId, "texture")
    private val colorLocation: Int = GLES20.glGetUniformLocation(programId, "baseColor")

    /** Whether the sprite shader has valid attribute locations */
    private val valid: Boolean = positionLocation >= 0 && uvLocation >= 0

    private val spriteColor = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
    private var maxWidth = 0
    private var maxHeight = 0

    fun renderImmediate(textureId: Int, uvVertex: FloatArray) {
        if (!valid || maxWidth <= 0 || maxHeight <= 0) return

        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glEnableVertexAttribArray(uvLocation)

        GLES20.glUniform1i(textureLocation, 0)

        // Normalized device coordinates for the quad
        val w2 = maxWidth * 0.5f
        val h2 = maxHeight * 0.5f
        val positionVertex = floatArrayOf(
            (rect.right - w2) / w2, (rect.up - h2) / h2,
            (rect.left - w2) / w2, (rect.up - h2) / h2,
            (rect.left - w2) / w2, (rect.down - h2) / h2,
            (rect.right - w2) / w2, (rect.down - h2) / h2
        )

        val posBuf = ByteBuffer.allocateDirect(positionVertex.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(positionVertex); position(0) }
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, posBuf)

        val uvBuf = ByteBuffer.allocateDirect(uvVertex.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(uvVertex); position(0) }
        GLES20.glVertexAttribPointer(uvLocation, 2, GLES20.GL_FLOAT, false, 0, uvBuf)

        GLES20.glUniform4f(
            colorLocation,
            spriteColor[0], spriteColor[1], spriteColor[2], spriteColor[3]
        )

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
    }

    fun resize(x: Float, y: Float, width: Float, height: Float) {
        rect.left = x - width * 0.5f
        rect.right = x + width * 0.5f
        rect.up = y + height * 0.5f
        rect.down = y - height * 0.5f
    }

    fun setColor(r: Float, g: Float, b: Float, a: Float) {
        spriteColor[0] = r
        spriteColor[1] = g
        spriteColor[2] = b
        spriteColor[3] = a
    }

    fun setWindowSize(width: Int, height: Int) {
        maxWidth = width
        maxHeight = height
    }

    private class Rect {
        var left = 0f
        var right = 0f
        var up = 0f
        var down = 0f
    }
}
