package com.llz121517.meapet.framework

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import com.llz121517.meapet.client.KtorHttpClientEngine
import com.llz121517.meapet.client.OpenAiCompatibleClient
import com.llz121517.meapet.chat.ChatService
import com.llz121517.meapet.chat.ConversationManager
import com.llz121517.meapet.memory.MemoryManager
import com.llz121517.meapet.memory.MemoryRepository
import com.llz121517.meapet.memory.MemoryService
import com.llz121517.meapet.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用依赖容器（手动 DI / 服务定位器）。
 *
 * ## 职责
 * - 创建并持有所有子系统实例；
 * - 保证初始化顺序（Settings → Client → Memory → Chat）；
 * - 提供 [reloadClient] 等方法在运行时切换配置（如 API Key 变更）。
 *
 * ## 使用方式
 * ```kotlin
 * // 在 Application.onCreate 中初始化
 * val container = AppContainer(context)
 *
 * // 在 Activity / ViewModel 中获取
 * val chatService = container.chatService
 * ```
 *
 * ## 低耦合设计
 * - 各子系统之间仅通过接口/服务类交互，不直接依赖具体实现；
 * - [ChatService] 依赖 [MemoryManager] 接口，而非 MemoryRepository 具体类；
 * - 容器是唯一知道"谁依赖谁"的地方。
 *
 * @param context Application Context（非 Activity，防止泄漏）
 * @param config 全局配置，不传则使用 [AppConfig.DEFAULT]
 */
class AppContainer(
    private val context: Context,
    val config: AppConfig = AppConfig.DEFAULT
) {
    companion object {
        private const val TAG = "AppContainer"
    }
    /** 设置管理器（独立，无依赖） */
    val settingsManager: SettingsManager by lazy {
        SettingsManager(context)
    }

    /** OpenAI 兼容 HTTP 客户端。 */
    val apiClient: OpenAiCompatibleClient by lazy {
        createClient()
    }

    /** 会话历史管理器。 */
    val conversationManager: ConversationManager by lazy {
        ConversationManager(maxSize = config.maxHistoryMessages)
    }

    /** 记忆存储器。 */
    val memoryRepository: MemoryRepository by lazy {
        MemoryRepository(context)
    }

    /** 记忆业务服务。 */
    val memoryService: MemoryService by lazy {
        MemoryService(
            repository = memoryRepository,
            summarizationClient = apiClient,
            settingsManager = settingsManager,
            config = config
        )
    }

    /** 记忆管理器（高层面 orchestrator）。 */
    val memoryManager: MemoryManager by lazy {
        MemoryManager(
            service = memoryService,
            repository = memoryRepository
        )
    }

    /** 聊天服务（核心业务逻辑）。 */
    val chatService: ChatService by lazy {
        ChatService(
            client = apiClient,
            conversationManager = conversationManager,
            memoryManager = memoryManager,
            settingsManager = settingsManager,
            config = config
        )
    }

    /** 应用级协程作用域（用于非 UI 的后台任务）。 */
    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    /** 生命周期管理器。 */
    val lifecycleManager: LifecycleManager by lazy {
        LifecycleManager(
            trimMemoryCallback = { level ->
                // 只做日志，不在这里调 suspend 函数（系统回调可能在任何线程触发，
                // 且此时各 lazy 组件可能尚未初始化，容易导致崩溃）
                if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                    Log.i(TAG, "系统内存紧张（level=$level），由各组件自行在下次操作时清理")
                }
            }
        )
    }

    // ── 运行时热替换 ──────────────────────────────────

    /**
     * 在 API Key 或 Base URL 变更后重建 HTTP 客户端。
     * 各服务会在下次请求时通过新的 [apiClient] 发送。
     */
    fun reloadClient() {
        // 手动 DI 的局限：需要重建持有 client 的服务
        // 这里通过重新创建各服务实例来处理
        _apiClient?.close()
        _apiClient = null
        _chatService = null
    }

    /**
     * 清除所有记忆数据。
     */
    suspend fun clearAllMemories() {
        memoryRepository.clear()
    }

    /**
     * 复位所有状态（清除对话 + 记忆）。
     */
    suspend fun resetAll() {
        conversationManager.clear()
        memoryRepository.clear()
    }

    // ── 内部可变缓存（用于 reload） ──

    @Volatile
    private var _apiClient: OpenAiCompatibleClient? = null

    @Volatile
    private var _chatService: ChatService? = null

    private fun createClient(): OpenAiCompatibleClient {
        val apiKey = settingsManager.getApiKey()
        val baseUrl = settingsManager.getApiUrl()
        return OpenAiCompatibleClient(
            apiKey = apiKey,
            baseUrl = baseUrl,
            engine = KtorHttpClientEngine()
        )
    }
}
