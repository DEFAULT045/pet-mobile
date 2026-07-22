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

    /** 触摸分区是否启用（设置页内禁用，防止穿透触发语音）。 */
    @Volatile
    var zoneTouchEnabled = true

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
        if (FloatingLive2dService.wasActive && !FloatingLive2dService.overlayActive) {
            CubismShaderAndroid.getInstance().releaseInvalidShaderProgram()
            CubismShaderAndroid.deleteInstance()
            FloatingLive2dService.wasActive = false
            Log.d(TAG, "Shader state reset after overlay closed")
        }

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

    // ── 触摸跟随 ──
    // 像素坐标 → 归一化 [-1, 1]（Y 轴翻转：Android 顶部=0，OpenGL 底部=-1）

    fun onTouchBegan(x: Float, y: Float) {
        mouseX = x; mouseY = y
        touchDownX = x; touchDownY = y; touchDownTime = System.currentTimeMillis()
        isCaptured = true
        if (windowWidth > 0 && windowHeight > 0) {
            Live2dManager.getInstance().onDrag(
                ((x / windowWidth) * 2f - 1f).coerceIn(-1f, 1f),
                (-((y / windowHeight) * 2f - 1f)).coerceIn(-1f, 1f)
            )
        }
    }

    fun onTouchEnd(x: Float, y: Float) {
        mouseX = x; mouseY = y
        isCaptured = false
        Live2dManager.getInstance().onDrag(0.0f, 0.0f)

        // 检测轻触 → 分区反馈（仅在聊天页启用，防止设置页穿透）
        if (zoneTouchEnabled && isTap(x, y)) {
            val zone = detectZone(x, y)
            if (zone != null) {
                val (text, file) = zone
                // 播放语音
                ensureVoicePlayer()?.play(file)
                // 广播消息到 ChatViewModel
                Live2dManager.emitTapMessage(text)
            }
        }
    }

    fun onTouchMoved(x: Float, y: Float) {
        mouseX = x; mouseY = y
        if (isCaptured && windowWidth > 0 && windowHeight > 0) {
            Live2dManager.getInstance().onDrag(
                ((x / windowWidth) * 2f - 1f).coerceIn(-1f, 1f),
                (-((y / windowHeight) * 2f - 1f)).coerceIn(-1f, 1f)
            )
        }
    }

    private var mouseX = 0f
    private var mouseY = 0f

    // ── 触摸分区系统 ──

    private var voicePlayer: VoicePlayer? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L

    /** 懒初始化 VoicePlayer（需要 Context，在 onSurfaceCreated 后可用）。 */
    private fun ensureVoicePlayer(): VoicePlayer? {
        if (voicePlayer == null) {
            val act = _activity ?: return null
            voicePlayer = VoicePlayer(act.applicationContext)
        }
        return voicePlayer
    }

    /** 判断是否为轻触（非拖动）。阈值：移动≤30px、时长≤400ms。 */
    private fun isTap(x: Float, y: Float): Boolean {
        val dx = x - touchDownX
        val dy = y - touchDownY
        val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val duration = System.currentTimeMillis() - touchDownTime
        return dist < 30f && duration < 400L
    }

    // ── 触摸分区定义 ──

    /** 各区域语音文件列表。文件名即文案来源（`jp_别摸了.wav` → 显示"别摸了"）。 */
    private val files_upper    = listOf("jp_别摸了.wav", "jp_别摸我头发。.wav", "jp_有事吗？.wav", "jp_哼。.wav")
    private val files_lowerL   = listOf("jp_变态。.wav", "jp_别摸了.wav", "jp_你想死一次吗？.wav", "jp_哼。.wav")
    private val files_lowerR   = listOf(
        "jp_变态。.wav", "jp_别摸了.wav", "jp_别摸我头发。.wav", "jp_哼。.wav",
        "jp_你想死一次吗？.wav", "jp_尾巴不许碰喵！！.wav", "jp_尾巴是很敏感的不知道吗。.wav"
    )

    /**
     * 检测触摸分区。
     * @return (显示文本, 语音文件名)，不在模型区域内则 null
     */
    private fun detectZone(x: Float, y: Float): Pair<String, String>? {
        if (windowWidth <= 0 || windowHeight <= 0) return null
        val nx = (x / windowWidth) * 2f - 1f
        val ny = -((y / windowHeight) * 2f - 1f)

        // 模型实际占屏区域（再上移 ~0.02，约 10px）
        if (nx < -0.45f || nx > 0.45f || ny < -0.44f || ny > 0.66f) return null

        // 从上往下 6/10 分割线
        val splitY = 0.00f

        return if (ny > splitY) {
            pickRandom(files_upper).let { textFromFilename(it) to it }
        } else if (nx < 0f) {
            pickRandom(files_lowerL).let { textFromFilename(it) to it }
        } else {
            pickRandom(files_lowerR).let { textFromFilename(it) to it }
        }
    }

    /** "jp_别摸了.wav" → "别摸了" */
    private fun textFromFilename(name: String): String =
        name.substringAfter("_").substringBeforeLast(".")

    private fun <T> pickRandom(list: List<T>): T = list[java.util.Random().nextInt(list.size)]
}
