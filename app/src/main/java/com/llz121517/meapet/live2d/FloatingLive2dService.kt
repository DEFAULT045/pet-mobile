package com.llz121517.meapet.live2d

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import com.live2d.sdk.cubism.framework.rendering.android.CubismOffscreenManagerAndroid
import com.live2d.sdk.cubism.framework.rendering.android.CubismRendererAndroid
import com.live2d.sdk.cubism.framework.rendering.android.CubismShaderAndroid
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * Foreground service that renders the Live2D model in a transparent floating window.
 * - Drag to move
 * - Pinch to resize
 * - Tap to close
 */
class FloatingLive2dService : Service() {

    companion object {
        private const val TAG = "FloatingLive2d"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "live2d_overlay"

        /** Whether the overlay is currently active. */
        @Volatile
        var overlayActive = false

        /** Whether the overlay was active; checked to reset GL state after close. */
        @Volatile
        var wasActive = false

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingLive2dService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingLive2dService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private lateinit var glSurfaceView: GLSurfaceView
    private var layoutParams: WindowManager.LayoutParams? = null

    // ----- touch state -----
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f

    private var pinchStartDist = 0f
    private var pinchStartW = 0
    private var pinchStartH = 0

    /** Screen-density conversion cache */
    private var _density = 0f
    private val density: Float get() {
        if (_density == 0f) _density = resources.displayMetrics.density
        return _density
    }
    private val minWinPx: Int get() = (100 * density).toInt()
    private val maxWinPx: Int get() = (600 * density).toInt()
    private val baseAspect: Float get() = 150f / 218f  // width / height

    // ----- offset to convert getX → rawX (works on all API levels) -----
    private var rawOffsetX = 0
    private var rawOffsetY = 0

    override fun onCreate() {
        super.onCreate()
        wasActive = false
        overlayActive = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWindow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wasActive = true
        overlayActive = false
        windowManager?.removeView(glSurfaceView)
        windowManager = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Live2D Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows Live2D model floating over other apps" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Live2D Overlay")
            .setContentText("Drag to move · Pinch to resize · Tap to close")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun createFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val winWidth = (150 * density).toInt()
        val winHeight = (218 * density).toInt()

        glSurfaceView = object : GLSurfaceView(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouch(event)
                return true
            }
        }.apply {
            // CRITICAL: explicit EGL config with 8-bit alpha for transparency
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setZOrderOnTop(true)
            setEGLContextClientVersion(2)
            setRenderer(FloatingRenderer(this@FloatingLive2dService, this@apply))
            // WHEN_DIRTY reduces GPU load — requestRender() called per frame
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            setPreserveEGLContextOnPause(false)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        layoutParams = WindowManager.LayoutParams(
            winWidth, winHeight,
            flags,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 160
        }

        windowManager?.addView(glSurfaceView, layoutParams)
        Log.d(TAG, "Floating window created: ${winWidth}x$winHeight")
    }

    // ================ Multi-touch handling ================

    private fun handleTouch(event: MotionEvent) {
        val params = layoutParams ?: return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // First finger down → prepare for drag or tap
                rawOffsetX = (event.rawX - event.x).toInt()
                rawOffsetY = (event.rawY - event.y).toInt()
                dragStartX = params.x
                dragStartY = params.y
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger down → prepare for pinch
                if (event.pointerCount == 2) {
                    pinchStartDist = calcPointerDistance(event)
                    pinchStartW = params.width
                    pinchStartH = params.height
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && pinchStartDist > 0f) {
                    // --- PINCH: resize window ---
                    val curDist = calcPointerDistance(event)
                    val ratio = curDist / pinchStartDist
                    var newW = (pinchStartW * ratio).toInt().coerceIn(minWinPx, maxWinPx)
                    // Preserve aspect ratio
                    var newH = (newW / baseAspect).toInt().coerceIn(minWinPx, maxWinPx)
                    // Re-derive width from height to keep exact aspect
                    newW = (newH * baseAspect).toInt().coerceIn(minWinPx, maxWinPx)
                    params.width = newW
                    params.height = newH
                    windowManager?.updateViewLayout(glSurfaceView, params)
                } else {
                    // --- DRAG: move window ---
                    params.x = dragStartX + (event.rawX - dragStartRawX).toInt()
                    params.y = dragStartY + (event.rawY - dragStartRawY).toInt()
                    windowManager?.updateViewLayout(glSurfaceView, params)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // When all fingers are up, check if it was a tap
                if (event.pointerCount <= 1) {
                    pinchStartDist = 0f
                    val dx = event.rawX - dragStartRawX
                    val dy = event.rawY - dragStartRawY
                    if (sqrt((dx * dx + dy * dy).toDouble()) < 15.0) {
                        stopSelf()  // tap to close
                    }
                }
            }
        }
    }

    /** Distance between the two pointers using getX/Y (works without rawX pointer overload). */
    private fun calcPointerDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    // ================ Renderer ================

    private class FloatingRenderer(
        private val service: FloatingLive2dService,
        private val surface: GLSurfaceView
    ) : GLSurfaceView.Renderer {

        private var model: Live2dModel? = null
        private var textureManager: Live2dTextureManager? = null
        private val projection = CubismMatrix44.create()
        private val viewMatrix = CubismMatrix44.create()
        private var winWidth = 0
        private var winHeight = 0
        private var modelLoaded = false

        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            Log.d(TAG, "Overlay onSurfaceCreated")

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            // Re-initialize shader manager for THIS GL context
            CubismShaderAndroid.getInstance().releaseInvalidShaderProgram()
            CubismShaderAndroid.deleteInstance()

            if (!CubismFramework.isInitialized()) {
                CubismFramework.initialize()
                Log.d(TAG, "CubismFramework initialized for overlay")
            }

            textureManager = Live2dTextureManager()
            modelLoaded = false
            model = null
        }

        override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            winWidth = width
            winHeight = height
            Log.d(TAG, "Overlay onSurfaceChanged: ${width}x$height")
        }

        override fun onDrawFrame(unused: GL10?) {
            if (!modelLoaded) {
                loadModel()
                modelLoaded = true
            }

            // Black background so the window border/shape is visible
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            val m = model ?: return
            if (winWidth <= 0 || winHeight <= 0) return

            Live2dPal.updateTime()

            val aspect = winWidth.toFloat() / winHeight.toFloat()
            val displayRatio = winHeight.toFloat() / winWidth.toFloat()

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
            m.update()
            m.draw(projection)

            CubismOffscreenManagerAndroid.getInstance().endFrameProcess()
            CubismOffscreenManagerAndroid.getInstance().releaseStaleRenderTextures()

            // Request next frame (WHEN_DIRTY mode)
            surface.requestRender()
        }

        private fun loadModel() {
            try {
                val dir = "live2d/mea_live2d/"
                val fileName = "mea.model3.json"
                model = Live2dModel(dir)
                model!!.loadAssets(dir, fileName)
                model!!.bindTextures(textureManager!!)
                Log.d(TAG, "Overlay model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load overlay model", e)
            }
        }
    }
}
