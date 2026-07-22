package com.llz121517.meapet.framework

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
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
import com.llz121517.meapet.live2d.FloatingLive2dService
import com.llz121517.meapet.live2d.Live2dDelegate
import com.llz121517.meapet.live2d.Live2dRenderer
import com.llz121517.meapet.ui.screen.ChatScreenContent
import com.llz121517.meapet.ui.theme.MeaPetTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 主入口 Activity（框架层）。
 *
 * ## 设计原则
 * - `launchMode="singleTask"` 防止重复实例
 * - `isTaskRoot` 防护 Android 15+ 兼容性
 * - GLSurfaceView + ComposeView 混合渲染
 * - 所有系统回调均 try-catch 保护
 */
class MainActivity : ComponentActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var container: AppContainer
    private var insetsController: WindowInsetsControllerCompat? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }

    // ── 生命周期 ──────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility", "SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        // isTaskRoot 防护（Android 15+ 兼容）
        if (!isTaskRoot) {
            finish()
            return
        }
        super.onCreate(savedInstanceState)

        container = MeaPetApplication.from(applicationContext as android.app.Application)

        // 设置窗口背景（防白闪）
        try {
            val themeMode = runBlocking { container.settingsManager.themeModeFlow.first() }
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> (resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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

        // GLSurfaceView
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(Live2dRenderer())
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setPreserveEGLContextOnPause(true)
        }

        // ComposeView
        val composeView = ComposeView(this).apply {
            setContent {
                val themeMode by container.settingsManager.themeModeFlow
                    .collectAsState(initial = "system")
                val enableDynamicColor by container.settingsManager.enableDynamicColorFlow
                    .collectAsState(initial = true)
                val colorPreset by container.settingsManager.colorPresetFlow
                    .collectAsState(initial = "default")

                // Live2D 背景色跟随主题
                val bgColor = remember(themeMode) {
                    val isDark = when (themeMode) {
                        "dark" -> true; "light" -> false
                        else -> (resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    }
                    if (isDark) floatArrayOf(0.08f, 0.08f, 0.08f)
                    else floatArrayOf(0.97f, 0.97f, 0.97f)
                }
                LaunchedEffect(bgColor) {
                    Live2dDelegate.getInstance().let { d ->
                        d.bgR = bgColor[0]; d.bgG = bgColor[1]; d.bgB = bgColor[2]; d.bgA = 1.0f
                    }
                }

                MeaPetTheme(themeMode = themeMode, dynamicColor = enableDynamicColor, colorPreset = colorPreset) {
                    ChatScreenContent(onToggleOverlay = { toggleOverlay() })
                }
            }
        }

        // 根布局 + 触摸透传
        val root = object : FrameLayout(this) {
            override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                try {
                    if (event != null && ::glSurfaceView.isInitialized) {
                        glSurfaceView.queueEvent {
                            try {
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> Live2dDelegate.getInstance().onTouchBegan(event.x, event.y)
                                    MotionEvent.ACTION_UP -> Live2dDelegate.getInstance().onTouchEnd(event.x, event.y)
                                    MotionEvent.ACTION_MOVE -> Live2dDelegate.getInstance().onTouchMoved(event.x, event.y)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            addView(glSurfaceView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(composeView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        try {
            Live2dDelegate.getInstance().onStart(this)
            // 从桌面返回时自动关悬浮窗
            if (FloatingLive2dService.overlayActive) {
                FloatingLive2dService.overlayActive = false
                FloatingLive2dService.stop(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStart error: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            glSurfaceView.onResume()
            hideSystemBars()
        } catch (e: Exception) {
            Log.e(TAG, "onResume error: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            glSurfaceView.onPause()
        } catch (e: Exception) {
            Log.e(TAG, "onPause error: ${e.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            Live2dDelegate.getInstance().onStop()
        } catch (e: Exception) {
            Log.e(TAG, "onStop error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Live2dDelegate.getInstance().onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy error: ${e.message}")
        }
    }

    @Deprecated("Use registerForActivityResult")
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

    // ── 悬浮窗 ──────────────────────────────────────

    private fun toggleOverlay() {
        if (FloatingLive2dService.overlayActive) {
            FloatingLive2dService.overlayActive = false
            FloatingLive2dService.stop(this)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    OVERLAY_PERMISSION_REQUEST
                )
                return
            }
            startOverlayAndGoBack()
        }
    }

    private fun startOverlayAndGoBack() {
        FloatingLive2dService.overlayActive = true
        FloatingLive2dService.start(this)
        moveTaskToBack(true)
    }

    private fun hideSystemBars() {
        insetsController?.hide(
            WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars()
        )
    }
}
