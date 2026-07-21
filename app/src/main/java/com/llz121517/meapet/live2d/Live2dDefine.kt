package com.llz121517.meapet.live2d

import com.live2d.sdk.cubism.framework.CubismFrameworkConfig.LogLevel

object Live2dDefine {
    object Scale {
        const val DEFAULT = 1.0f
        const val MAX = 2.0f
        const val MIN = 0.8f
    }

    object LogicalView {
        const val LEFT = -1.0f
        const val RIGHT = 1.0f
        const val BOTTOM = -1.0f
        const val TOP = 1.0f
    }

    object MaxLogicalView {
        const val LEFT = -2.0f
        const val RIGHT = 2.0f
        const val BOTTOM = -2.0f
        const val TOP = 2.0f
    }

    object MotionGroup {
        const val IDLE = "Idle"
        const val TAP_BODY = "TapBody"
    }

    object Priority {
        const val NONE = 0
        const val IDLE = 1
        const val NORMAL = 2
        const val FORCE = 3
    }

    const val MOC_CONSISTENCY_VALIDATION_ENABLE = true
    const val DEBUG_LOG_ENABLE = true
    const val PREMULTIPLIED_ALPHA_ENABLE = true
    val CUBISM_LOGGING_LEVEL = LogLevel.VERBOSE

    /** Shader source code embedded to avoid external file dependencies */
    object ShaderSource {
        val VERTEX = """
            #version 100
            attribute vec3 position;
            attribute vec2 uv;
            varying vec2 vuv;
            void main(void) {
                gl_Position = vec4(position, 1.0);
                vuv = uv;
            }
        """.trimIndent()

        val FRAGMENT = """
            #version 100
            precision mediump float;
            varying vec2 vuv;
            uniform sampler2D texture;
            uniform vec4 baseColor;
            void main(void) {
                gl_FragColor = texture2D(texture, vuv) * baseColor;
            }
        """.trimIndent()
    }
}
