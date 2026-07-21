package com.llz121517.meapet.live2d

import android.util.Log
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenManagerAndroid

/**
 * Singleton manager that owns the Live2D model and drives update/draw.
 */
class Live2dManager private constructor() {

    companion object {
        private const val TAG = "Live2dManager"

        /** The model directory in assets */
        private const val MODEL_DIR_NAME = "live2d/mea_live2d"
        private const val MODEL_JSON_NAME = "mea.model3.json"

        @Volatile
        private var instance: Live2dManager? = null

        fun getInstance(): Live2dManager {
            return instance ?: synchronized(this) {
                instance ?: Live2dManager().also { instance = it }
            }
        }

        fun releaseInstance() {
            CubismOffscreenManagerAndroid.releaseInstance()
            instance = null
        }
    }

    var model: Live2dModel? = null
        private set

    private var modelLoaded = false

    private val viewMatrix = CubismMatrix44.create()
    private val projection = CubismMatrix44.create()

    /**
     * Load the model from assets. Safe to call multiple times.
     * Must be called after [CubismFramework.initialize].
     */
    fun loadModel() {
        if (modelLoaded) {
            Log.d(TAG, "Model already loaded, skipping")
            return
        }
        val dir = "$MODEL_DIR_NAME/"
        Log.d(TAG, "Loading model from: $dir$MODEL_JSON_NAME")
        try {
            model = Live2dModel(dir)
            model!!.loadAssets(dir, MODEL_JSON_NAME)
            modelLoaded = true
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
        }
    }

    fun onUpdate() {
        // Guard: need model loaded and valid dimensions
        val m = model ?: return
        val delegate = Live2dDelegate.getInstance()
        val width = delegate.windowWidth
        val height = delegate.windowHeight
        if (width <= 0 || height <= 0) return

        val aspect = width.toFloat() / height.toFloat()
        val displayRatio = height.toFloat() / width.toFloat()

        CubismOffscreenManagerAndroid.getInstance().beginFrameProcess()

        projection.loadIdentity()

        val canvasRatio = m.model!!.canvasHeight / m.model!!.canvasWidth

        if (canvasRatio < displayRatio) {
            m.modelMatrix!!.setWidth(2.0f)
            projection.scale(1.0f, aspect)
        } else {
            m.modelMatrix!!.setHeight(2.0f)
            projection.scale(1.0f / aspect, 1.0f)
        }

        viewMatrix.multiplyByMatrix(projection)

        delegate.view.preModelDraw(m)
        m.update()
        m.draw(projection)
        delegate.view.postModelDraw(m)

        CubismOffscreenManagerAndroid.getInstance().endFrameProcess()
        CubismOffscreenManagerAndroid.getInstance().releaseStaleRenderTextures()
    }

    fun onDrag(x: Float, y: Float) {
        model?.setDragging(x, y)
    }

    fun setRenderTargetSize(width: Int, height: Int) {
        model?.setRenderTargetSize(width, height)
    }
}
