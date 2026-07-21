package com.llz121517.meapet.live2d

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView renderer that delegates to Live2dDelegate.
 */
class Live2dRenderer : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        Live2dDelegate.getInstance().onSurfaceCreated()
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        Live2dDelegate.getInstance().onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(unused: GL10?) {
        Live2dDelegate.getInstance().run()
    }
}
