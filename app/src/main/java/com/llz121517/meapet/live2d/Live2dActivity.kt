package com.llz121517.meapet.live2d

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

        // ── 设置窗口背景色（防止 GLSurfaceView 加载前白底闪烁） ──
        try {
            val themeMode = runBlocking {
                container.settingsManager.themeModeFlow.first()
            }
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> (resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            }
            window.setBackgroundDrawable(
                ColorDrawable(if (isDark) 0xFF141414.toInt() else 0xFFF7F7F7.toInt())
            )
        } catch (_: Exception) {
            window.setBackgroundDrawable(ColorDrawable(0xFF141414.toInt()))
        }

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
                val enableDynamicColor by container.settingsManager.enableDynamicColorFlow
                    .collectAsState(initial = true)
                val colorPreset by container.settingsManager.colorPresetFlow
                    .collectAsState(initial = "default")

                // 将主题背景色同步到 Live2D GL 层（每次主题变化时更新）
                val bgColor = remember(themeMode) {
                    val isDark = when (themeMode) {
                        "dark" -> true
                        "light" -> false
                        else -> (resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK) ==
                                Configuration.UI_MODE_NIGHT_YES
                    }
                    if (isDark) floatArrayOf(0.08f, 0.08f, 0.08f)  // 深色背景
                    else floatArrayOf(0.97f, 0.97f, 0.97f)          // 浅色背景
                }
                LaunchedEffect(bgColor) {
                    Live2dDelegate.getInstance().let { d ->
                        d.bgR = bgColor[0]; d.bgG = bgColor[1]; d.bgB = bgColor[2]; d.bgA = 1.0f
                    }
                }

                MeaPetTheme(
                    themeMode = themeMode,
                    dynamicColor = enableDynamicColor,
                    colorPreset = colorPreset
                ) {
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

        // 从桌面返回应用时自动关闭悬浮窗
        if (FloatingLive2dService.overlayActive) {
            FloatingLive2dService.overlayActive = false
            FloatingLive2dService.stop(this)
        }
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
                startOverlayAndGoBack()
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

            startOverlayAndGoBack()
        }
    }

    /** 启动悬浮窗并退到桌面。 */
    private fun startOverlayAndGoBack() {
        FloatingLive2dService.overlayActive = true
        FloatingLive2dService.start(this)
        moveTaskToBack(true)
    }

    private fun hideSystemBars() {
        insetsController?.hide(
            WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.statusBars()
        )
    }
}
