package com.llz121517.meapet.framework

import android.app.Application
import android.util.Log

/**
 * 全局 Application 入口。
 *
 * ## 职责
 * - 初始化 [AppContainer]（唯一依赖容器）；
 * - 注册 [LifecycleManager]；
 * - 提供静态 [instance] 访问入口（谨慎使用，优先通过构造注入）。
 *
 * ## 配置
 * 在 `AndroidManifest.xml` 中声明：
 * ```xml
 * <application android:name=".framework.MeaPetApplication" ... />
 * ```
 */
class MeaPetApplication : Application() {

    /** 应用级依赖容器。所有子系统均通过此容器获取。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MeaPetApplication onCreate")

        // 1) 创建依赖容器
        container = AppContainer(this)

        // 2) 注册生命周期回调
        registerComponentCallbacks(container.lifecycleManager)

        // 3) 打印启动信息
        Log.i(TAG, "App initialized: config=$container.config")
    }

    override fun onTerminate() {
        Log.i(TAG, "MeaPetApplication onTerminate")
        super.onTerminate()
    }

    companion object {
        private const val TAG = "MeaPetApp"

        /**
         * 便捷获取容器。
         *
         * 仅在无法通过构造注入的场景使用（如较旧的 Java 组件）。
         */
        fun from(application: Application): AppContainer =
            (application as MeaPetApplication).container
    }
}
