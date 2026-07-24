package com.meapet.mobile.memory

import android.util.Log
import com.meapet.mobile.settings.SettingsManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 记忆管理器——记忆系统的高层入口。
 *
 * ## 职责
 * - 对外暴露记忆系统功能（ChatService、ViewModel 等调用方仅需与此类交互）；
 * - 编排 [MemoryService] 与 [MemoryRepository] 的调用顺序；
 * - 统计与报告。
 *
 * ## 用法示例
 * ```kotlin
 * val memoryManager = MemoryManager(memoryService, memoryRepository, settingsManager)
 *
 * // 对话后调用
 * memoryManager.onExchangeComplete("你好", "你好！我是 Mea～")
 *
 * // 注入上下文
 * val context = memoryManager.buildContext("用户问了我的名字")
 * ```
 *
 * @param service 记忆业务服务
 * @param repository 记忆存储器
 * @param settingsManager 设置管理器（读取记忆开关）
 */
class MemoryManager(
    private val service: MemoryService,
    private val repository: MemoryRepository,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "MemoryManager"
        /** 触发自动摘要的对话轮数。 */
        private const val SUMMARY_INTERVAL = 10
        /** 近期对话缓存上限（超出丢弃最早的，防止无界增长）。 */
        private const val MAX_RECENT_EXCHANGES = 50
    }

    private val stateMutex = Mutex()
    private var exchangeCount = 0
    private val recentExchanges = mutableListOf<Pair<String, String>>()

    // ── 对外 API ──────────────────────────────────────

    /**
     * 在一次对话交换完成后调用。
     *
     * 内部处理：
     * 1. 提取短期记忆；
     * 2. 定期触发长期记忆摘要；
     * 3. 定期触发记忆合并。
     */
    suspend fun onExchangeComplete(userMessage: String, assistantResponse: String) {
        // 记忆总开关关闭时，整条事后链路（提取/摘要/合并）都不得触发，
        // 对话内容不落盘、不外发给摘要模型
        if (!isMemoryEnabled()) return

        // 计数与缓存的读写都在锁内完成，锁外只拿快照做耗时操作
        val (count, summarySnapshot) = stateMutex.withLock {
            exchangeCount++
            recentExchanges.add(userMessage to assistantResponse)
            while (recentExchanges.size > MAX_RECENT_EXCHANGES) {
                recentExchanges.removeAt(0)
            }
            val snapshot = if (exchangeCount % SUMMARY_INTERVAL == 0 && recentExchanges.size >= 3) {
                recentExchanges.takeLast(10).toList()
            } else {
                null
            }
            exchangeCount to snapshot
        }

        // 1) 提取短期记忆
        val shortTermItems = service.extractFromExchange(userMessage, assistantResponse)
        if (shortTermItems.isNotEmpty()) {
            repository.saveAll(shortTermItems)
        }

        // 2) 每 SUMMARY_INTERVAL 轮触发一次长期摘要
        if (summarySnapshot != null) {
            Log.i(TAG, "Triggering long-term memory summary ($count exchanges)")
            service.summarizeToLongTerm(summarySnapshot)
        }

        // 3) 每 2 * SUMMARY_INTERVAL 轮触发一次合并
        if (count % (SUMMARY_INTERVAL * 2) == 0) {
            service.consolidate()
        }
    }

    /**
     * 为当前对话构建记忆上下文文本。
     *
     * @param currentInput 用户当前输入，用于搜索相关记忆
     * @return 格式化的上下文文本，直接拼入 system prompt
     */
    suspend fun buildContext(currentInput: String): String {
        if (!isMemoryEnabled()) return ""

        val relevantMemories = repository.getRelevant(currentInput)

        if (relevantMemories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("\n【记忆上下文】")

        val longTerms = relevantMemories.filter { it.type == MemoryType.LONG_TERM || it.type == MemoryType.FACTUAL }
        val shortTerms = relevantMemories.filter { it.type == MemoryType.SHORT_TERM }
        val traits = relevantMemories.filter { it.type == MemoryType.CORE_TRAIT }

        if (traits.isNotEmpty()) {
            sb.appendLine("用户特质：")
            traits.forEach { sb.appendLine("- ${it.content}") }
        }
        if (longTerms.isNotEmpty()) {
            sb.appendLine("长期记忆：")
            longTerms.forEach { sb.appendLine("- ${it.content}") }
        }
        if (shortTerms.isNotEmpty()) {
            sb.appendLine("近期相关：")
            shortTerms.forEach { sb.appendLine("- ${it.content}") }
        }

        sb.appendLine("【记忆上下文结束】")
        return sb.toString()
    }

    /**
     * 获取所有记忆（按重要性排序）。
     */
    suspend fun getAllMemories(): List<MemoryItem> = repository.getAll()

    /**
     * 获取记忆统计。
     */
    suspend fun getStats(): MemoryStats = repository.getStats()

    /**
     * 获取记忆是否启用。
     */
    fun isMemoryEnabled(): Boolean = settingsManager.isMemoryEnabled()

    /**
     * 清除所有记忆。
     */
    suspend fun clearAll() {
        repository.clear()
        stateMutex.withLock {
            recentExchanges.clear()
            exchangeCount = 0
        }
        Log.i(TAG, "Memory manager reset")
    }
}
