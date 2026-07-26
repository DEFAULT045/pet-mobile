package com.meapet.mobile.memory

import android.util.Log
import com.meapet.mobile.framework.AppConfig
import com.meapet.mobile.settings.SettingsManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 记忆管理器——记忆系统的高层入口。
 *
 * ## 职责
 * - 对外暴露记忆系统功能（ChatService、ViewModel 等调用方仅需与此类交互）；
 * - 编排 [MemoryService] 与 [MemoryRepository] 的调用顺序；
 * - 构建注入 system prompt 的记忆上下文（含 [MemoryOpsProtocol] 协议说明）。
 *
 * 记忆该不该记、记什么，全部由大模型在回复中通过 [MemoryOpsProtocol] 自己声明；
 * 本类只负责把声明的操作转交给 [MemoryService] 落库，以及按设置的轮次触发摘要。
 *
 * ## 用法示例
 * ```kotlin
 * val memoryManager = MemoryManager(memoryService, memoryRepository, settingsManager, config)
 *
 * // 对话后调用（ops 来自 MemoryOpsProtocol.extract）
 * memoryManager.onExchangeComplete(ops)
 *
 * // 注入上下文
 * val context = memoryManager.buildContext("用户问了我的名字")
 * ```
 *
 * @param service 记忆业务服务
 * @param repository 记忆存储器
 * @param settingsManager 设置管理器（读取记忆开关、摘要轮次）
 * @param config 应用配置（读取相关回忆注入条数上限）
 */
class MemoryManager(
    private val service: MemoryService,
    private val repository: MemoryRepository,
    private val settingsManager: SettingsManager,
    private val config: AppConfig = AppConfig.DEFAULT
) {
    companion object {
        private const val TAG = "MemoryManager"
    }

    private val stateMutex = Mutex()
    private var exchangeCount = 0

    // ── 对外 API ──────────────────────────────────────

    /**
     * 在一次对话交换完成后调用。
     *
     * @param ops 模型本轮回复中声明的记忆操作（由 [MemoryOpsProtocol.extract] 解析得到）
     */
    suspend fun onExchangeComplete(ops: List<MemoryOpsProtocol.MemoryOp>) {
        // 记忆总开关关闭时不落库、不触发摘要
        if (!isMemoryEnabled()) {
            Log.i(TAG, "onExchangeComplete skipped: 记忆开关已关闭（设置页「启用记忆」）")
            return
        }

        Log.d(TAG, "onExchangeComplete: ${ops.size} op(s) to apply")
        if (ops.isNotEmpty()) {
            service.applyOps(ops)
        }

        val count = stateMutex.withLock {
            exchangeCount++
            exchangeCount
        }

        val interval = settingsManager.getSummaryInterval().coerceAtLeast(1)
        if (count % interval == 0) {
            Log.i(TAG, "Triggering short-term memory summary ($count exchanges, interval=$interval)")
            service.summarizeShortTermMemories()
        }
    }

    /**
     * 为当前对话构建记忆上下文文本，拼入 system prompt。
     *
     * 开关开启时**必定**包含 [MemoryOpsProtocol.instructions]（哪怕暂无任何记忆，
     * 模型也要知道协议格式才能开始创建）；事实/特质全量注入（永不淘汰、数量小，
     * 相当于固定人设表）；短期/长期记忆按关键词匹配注入，数量受 [AppConfig.maxContextMemories] 限制。
     *
     * @param currentInput 用户当前输入，用于匹配相关的短期/长期记忆
     * @return 格式化的上下文文本；记忆关闭时返回空字符串
     */
    suspend fun buildContext(currentInput: String): String {
        if (!isMemoryEnabled()) {
            Log.i(TAG, "buildContext skipped: 记忆开关已关闭，本轮不会注入记忆协议说明")
            return ""
        }

        val facts = repository.getPersonaFacts()
        val recollections = repository.getRelevant(currentInput, maxCount = config.maxContextMemories)

        val sb = StringBuilder()
        sb.appendLine(MemoryOpsProtocol.instructions())

        if (facts.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("【用户人设】（永久，可引用 id 更新/删除）")
            facts.forEach { sb.appendLine("- [${it.id}] (${typeLabel(it.type)}) ${it.content}") }
        }
        if (recollections.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("【相关回忆】（关键词匹配，只有相关时才出现，可引用 id 更新/删除）")
            recollections.forEach { sb.appendLine("- [${it.id}] (${typeLabel(it.type)}) ${it.content}") }
        }

        Log.d(
            TAG,
            "Context built: ${facts.size} persona fact(s), ${recollections.size} recollection(s), " +
                "${sb.length} chars injected"
        )
        return sb.toString()
    }

    /**
     * 获取所有记忆（按重要性排序）。
     */
    suspend fun getAllMemories(): List<MemoryItem> = repository.getAll()

    /**
     * 删除单条记忆。
     */
    suspend fun delete(id: String) = repository.delete(id)

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
        stateMutex.withLock { exchangeCount = 0 }
        Log.i(TAG, "Memory manager reset")
    }

    private fun typeLabel(type: MemoryType): String = when (type) {
        MemoryType.SHORT_TERM -> "短期"
        MemoryType.LONG_TERM -> "长期"
        MemoryType.CORE_TRAIT -> "特质"
        MemoryType.FACTUAL -> "事实"
    }
}
