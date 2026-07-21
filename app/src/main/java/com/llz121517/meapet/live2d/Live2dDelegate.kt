package com.llz121517.meapet.live2d

import android.app.Activity
import android.opengl.GLES20
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.rendering.android.CubismShaderAndroid

/**
 * Singleton application delegate — manages Cubism SDK lifecycle,
 * OpenGL state, and owns the View + TextureManager.
 */
class Live2dDelegate private constructor() {

    companion object {
        private const val TAG = "Live2dDelegate"

        @Volatile
        private var instance: Live2dDelegate? = null

        fun getInstance(): Live2dDelegate {
            return instance ?: synchronized(this) {
                instance ?: Live2dDelegate().also { instance = it }
            }
        }
    }

    @Volatile
    private var _activity: Activity? = null
    val activity: Activity get() = _activity!!
    // 暴露给其他 Live2D 模块（非空，但在 GL 线程安全地用 try-catch 保护）

    val textureManager = Live2dTextureManager()
    val view = Live2dView()

    var windowWidth = 0
        private set
    var windowHeight = 0
        private set

    @Volatile
    private var isActive = false
    private var isCaptured = false
    private var mouseX = 0.0f
    private var mouseY = 0.0f

    /** 背景色 RGBA（0~1），跟随主题变化。默认浅色。 */
    @Volatile
    var bgR = 0.98f
    var bgG = 0.98f
    var bgB = 0.98f
    var bgA = 1.0f

    private val cubismOption = CubismFramework.Option()

    init {
        cubismOption.logFunction = Live2dPal.PrintLogFunction()
        cubismOption.loggingLevel = Live2dDefine.CUBISM_LOGGING_LEVEL
        cubismOption.loadFileFunction = Live2dPal.LoadFileFunction()

        CubismFramework.cleanUp()
        CubismFramework.startUp(cubismOption)
        Log.d(TAG, "CubismFramework started")
    }

    fun onStart(activity: Activity) {
        this._activity = activity
        isActive = true
    }

    fun onStop() { /* no-op */ }

    fun onDestroy() {
        view.close()
        CubismShaderAndroid.getInstance().releaseInvalidShaderProgram()
        CubismShaderAndroid.deleteInstance()
        Live2dManager.releaseInstance()
        CubismFramework.dispose()
        instance = null
        Log.d(TAG, "CubismFramework disposed")
    }

    fun onSurfaceCreated() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        textureManager.releaseInvalidTextures()
        view.close()

        Live2dPal.updateTime()

        // Initialize Cubism Framework (must happen in GL thread)
        if (!CubismFramework.isInitialized()) {
            CubismFramework.initialize()
            Log.d(TAG, "CubismFramework initialized")
        }

        CubismShaderAndroid.getInstance().releaseInvalidShaderProgram()
        CubismShaderAndroid.deleteInstance()

        // Load Live2D model (must happen after CubismFramework.initialize)
        Live2dManager.getInstance().loadModel()

        // Reload renderer for GL context
        Live2dManager.getInstance().model?.reloadRenderer()
        Log.d(TAG, "onSurfaceCreated complete")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        windowWidth = width
        windowHeight = height

        view.initialize()
        view.initializeSprite()

        Live2dManager.getInstance().setRenderTargetSize(width, height)
        Log.d(TAG, "onSurfaceChanged: ${width}x$height")
    }

    fun run() {
        // If overlay just closed, reset the CubismShaderAndroid singleton
        // (it has overlay's shader programs from a different GL context)
        if (FloatingLive2dService.wasActive && !FloatingLive2dService.overlayActive) {
            CubismShaderAndroid.getInstance().releaseInvalidShaderProgram()
            CubismShaderAndroid.deleteInstance()
            FloatingLive2dService.wasActive = false
            Log.d(TAG, "Shader state reset after overlay closed")
        }

        // Skip rendering when floating overlay is active
        if (FloatingLive2dService.overlayActive) return

        Live2dPal.updateTime()

        GLES20.glClearColor(bgR, bgG, bgB, bgA)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glClearDepthf(1.0f)

        try {
            view.render()
        } catch (e: Exception) {
            Log.e(TAG, "Render error: ${e.message}")
        }

        if (!isActive) {
            _activity?.finishAndRemoveTask()
        }
    }

    fun onTouchBegan(x: Float, y: Float) {
        mouseX = x
        mouseY = y
        isCaptured = true
        view.onTouchesBegan(mouseX, mouseY)
    }

    fun onTouchEnd(x: Float, y: Float) {
        mouseX = x
        mouseY = y
        isCaptured = false
        view.onTouchesEnded(mouseX, mouseY)
    }

    fun onTouchMoved(x: Float, y: Float) {
        mouseX = x
        mouseY = y
        if (isCaptured) {
            view.onTouchesMoved(mouseX, mouseY)
        }
    }
}
