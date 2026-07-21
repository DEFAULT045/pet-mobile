package com.llz121517.meapet.framework

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log

/**
 * 应用级生命周期管理器。
 *
 * 接收 [ComponentCallbacks2] 回调，用于统一处理低内存、前后台切换
 * 等全局事件，避免各子系统各自注册监听器。
 *
 * 由 [MeaPetApplication] 注册，非侵入——各模块需监听时在此集中处理。
 */
class LifecycleManager(
    private val onTrimMemory: (level: Int) -> Unit = {}
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "LifecycleManager"
    }

    override fun onTrimMemory(level: Int) {
        Log.d(TAG, "onTrimMemory level=$level")
        onTrimMemory(level)

        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "严重内存不足，各子系统应释放缓存")
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.i(TAG, "中度内存压力，建议释放非关键缓存")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // 无操作（Compose 自动处理配置变更）
    }

    override fun onLowMemory() {
        Log.w(TAG, "系统低内存通知")
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
