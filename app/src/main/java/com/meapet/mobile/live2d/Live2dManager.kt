package com.meapet.mobile.live2d

import android.util.Log
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenManagerAndroid
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton manager that owns the Live2D model and drives update/draw.
 */
class Live2dManager private constructor() {
    
    // 控制模型缩放与偏移的变量
    var modelScale: Float = 1.0f
    var modelOffsetX: Float = 0.0f
    var modelOffsetY: Float = 0.0f
    
    companion object {
        private const val TAG = "Live2dManager"

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

        /** 触摸分区触发的消息事件流。ChatViewModel 收集此流显示气泡。 */
        private val _tapMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val tapMessageEvent = _tapMessage.asSharedFlow()

        /** 发送分区消息。可由任意线程调用。 */
        fun emitTapMessage(text: String) {
            _tapMessage.tryEmit(text)
        }
    }

    var model: Live2dModel? = null
        private set

    private var modelLoaded = false

    private val viewMatrix = CubismMatrix44.create()
    private val projection = CubismMatrix44.create()

    /** 在 GL 上下文重建时强制重新加载模型（旧上下文的纹理已失效）。 */
    fun resetModel() {
        modelLoaded = false
        model?.deleteModel()
        model = null
        loadModel()
    }

    fun loadModel() {
        if (modelLoaded) return
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

        // 应用自定义缩放比例
        val baseSize = 2.4f * modelScale

        if (canvasRatio < displayRatio) {
            m.modelMatrix!!.setWidth(baseSize)
            projection.scale(1.0f, aspect)
        } else {
            m.modelMatrix!!.setHeight(baseSize)
            projection.scale(1.0f / aspect, 1.0f)
        }

        // 使用 setPosition 避免每一帧 translate 发生偏移累加
        m.modelMatrix!!.setPosition(modelOffsetX, modelOffsetY)

        viewMatrix.multiplyByMatrix(projection)

        delegate.view.preModelDraw(m)
        m.update()
        m.draw(projection)
        delegate.view.postModelDraw(m)

        CubismOffscreenManagerAndroid.getInstance().endFrameProcess()
        CubismOffscreenManagerAndroid.getInstance().releaseStaleRenderTextures()
    }

    /** 视角跟随：直接设 model 字段，update 内部会读取并应用。 */
    fun onDrag(x: Float, y: Float) {
        model?.let { m ->
            m.dragX = x.coerceIn(-1f, 1f)
            m.dragY = y.coerceIn(-1f, 1f)
            // 也通知 Cubism SDK 的 dragManager（保留原有 Look 系统兼容）
            try { m.setDragging(x, y) } catch (_: Exception) {}
        }
    }

    fun setRenderTargetSize(width: Int, height: Int) {
        model?.setRenderTargetSize(width, height)
    }
}
