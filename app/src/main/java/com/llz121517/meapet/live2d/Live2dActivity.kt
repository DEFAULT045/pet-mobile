package com.llz121517.meapet.live2d

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.llz121517.meapet.framework.AppContainer
import com.llz121517.meapet.framework.MeaPetApplication
import com.llz121517.meapet.ui.screen.ChatScreenContent
import com.llz121517.meapet.ui.theme.MeaPetTheme

/**
 * Main Live2D activity — uses a view-based GLSurfaceView for the Live2D
 * rendering layer, with a ComposeView overlay for the chat UI.
 *
 * Hybrid approach avoids potential GL context issues when embedding
 * GLSurfaceView inside Compose's AndroidView.
 */
class Live2dActivity : ComponentActivity() {

    private lateinit var glSurfaceView: GLSurfaceView

    /** 依赖容器。 */
    private val container: AppContainer by lazy {
        MeaPetApplication.from(application)
    }

    private var insetsController: WindowInsetsControllerCompat? = null

    companion object {
        private const val TAG = "Live2dActivity"
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        insetsController = WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        hideSystemBars()

        // Layer 1: Live2D rendering (view-based, unchanged from original)
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(Live2dRenderer())
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setPreserveEGLContextOnPause(true)

            // Forward touches to Live2D delegate
            setOnTouchListener { _: View, event: MotionEvent ->
                val px = event.x
                val py = event.y
                queueEvent {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN ->
                            Live2dDelegate.getInstance().onTouchBegan(px, py)
                        MotionEvent.ACTION_UP ->
                            Live2dDelegate.getInstance().onTouchEnd(px, py)
                        MotionEvent.ACTION_MOVE ->
                            Live2dDelegate.getInstance().onTouchMoved(px, py)
                    }
                }
                true
            }
        }

        // Layer 2: Compose UI overlay with theme from settings
        val composeView = ComposeView(this).apply {
            setContent {
                // 响应式订阅主题设置
                val themeMode by container.settingsManager.themeModeFlow
                    .collectAsState(initial = "system")

                MeaPetTheme(themeMode = themeMode) {
                    ChatScreenContent(
                        onToggleOverlay = { toggleOverlay() }
                    )
                }
            }
        }

        val root = FrameLayout(this).apply {
            addView(glSurfaceView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(composeView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        Live2dDelegate.getInstance().onStart(this)
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onStop() {
        super.onStop()
        Live2dDelegate.getInstance().onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Live2dDelegate.getInstance().onDestroy()
    }

    @SuppressLint("InlinedApi")
    @Deprecated("Use registerForActivityResult API instead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                FloatingLive2dService.start(this)
            } else {
                Log.w(TAG, "Overlay permission not granted")
            }
        }
    }

    // ---- Overlay management ----

    private fun toggleOverlay() {
        if (FloatingLive2dService.overlayActive) {
            FloatingLive2dService.overlayActive = false
            FloatingLive2dService.stop(this)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                @Suppress("DEPRECATION")
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                return
            }

            FloatingLive2dService.overlayActive = true
            FloatingLive2dService.start(this)
        }
    }

    private fun hideSystemBars() {
        insetsController?.hide(
            WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.statusBars()
        )
    }
}
