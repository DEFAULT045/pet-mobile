package com.llz121517.meapet.chat

import android.util.Log

/**
 * 会话历史管理器。
 *
 * ## 职责
 * - 维护消息列表的添加、查询、清空；
 * - 实现滑动窗口：超出 [maxSize] 时丢弃最早的非 system 消息；
 * - 为 API 请求组装消息列表（含 system prompt）。
 *
 * ## 低耦合
 * - 纯内存操作，不依赖任何 Android 组件或外部存储；
 * - 不关心消息是用户还是助手发送的，仅按顺序维护。
 *
 * @param maxSize 最大保留消息数
 */
class ConversationManager(
    private val maxSize: Int = 50
) {
    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val TAG = "ConversationManager"
    }

    /** 当前消息数量。 */
    val size: Int get() = messages.size

    /** 添加一条消息。自动触发窗口裁剪。 */
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        trimWindow()
        Log.d(TAG, "Message added [${message.role}]: ${message.content.take(50)}... (total: $size)")
    }

    /** 批量添加。 */
    fun addMessages(newMessages: List<ChatMessage>) {
        messages.addAll(newMessages)
        trimWindow()
    }

    /** 获取所有消息（不可变快照）。 */
    fun getMessages(): List<ChatMessage> = messages.toList()

    /**
     * 获取用于 API 请求的消息列表（含 system prompt）。
     *
     * @param systemPrompt 系统提示词
     * @param contextMemory 记忆上下文文本（拼入 system message）
     * @param maxMessages 最大消息数（滑动窗口，从末尾取）
     */
    fun buildApiMessages(
        systemPrompt: String,
        contextMemory: String = "",
        maxMessages: Int = 30
    ): List<ChatMessage> {
        val systemContent = buildString {
            append(systemPrompt)
            if (contextMemory.isNotBlank()) {
                append("\n\n$contextMemory")
            }
        }

        val systemMsg = ChatMessage(role = ChatRole.system, content = systemContent)
        val recentMessages = messages
            .filter { it.role != ChatRole.system }
            .takeLast(maxMessages)

        return listOf(systemMsg) + recentMessages
    }

    /**
     * 获取当前上下文的纯文本摘要（用于记忆提取等场景）。
     */
    fun getContextText(maxMessages: Int = 10): String {
        return messages
            .filter { it.role != ChatRole.system }
            .takeLast(maxMessages)
            .joinToString("\n") { "${it.role.name}: ${it.content}" }
    }

    /** 获取最近的用户-助手交换对。 */
    fun getRecentExchanges(count: Int = 10): List<Pair<String, String>> {
        val nonSystem = messages.filter { it.role != ChatRole.system }
        val exchanges = mutableListOf<Pair<String, String>>()
        var i = nonSystem.size - 1
        while (i >= 0 && exchanges.size < count) {
            if (nonSystem[i].role == ChatRole.assistant && i > 0 && nonSystem[i - 1].role == ChatRole.user) {
                exchanges.add(nonSystem[i - 1].content to nonSystem[i].content)
                i -= 2
            } else {
                i--
            }
        }
        return exchanges.reversed()
    }

    /** 清除所有消息。 */
    fun clear() {
        messages.clear()
        Log.i(TAG, "Conversation cleared")
    }

    /** 获取最后一条非 system 消息。 */
    fun lastUserMessage(): ChatMessage? =
        messages.lastOrNull { it.role == ChatRole.user }

    /** 获取最后一条助手消息。 */
    fun lastAssistantMessage(): ChatMessage? =
        messages.lastOrNull { it.role == ChatRole.assistant }

    // ── 内部 ──────────────────────────────────────────

    private fun trimWindow() {
        if (messages.size <= maxSize) return
        val systemMessages = messages.filter { it.role == ChatRole.system }
        val nonSystem = messages.filter { it.role != ChatRole.system }
        val excess = nonSystem.size - (maxSize - systemMessages.size)
        if (excess > 0) {
            val trimmed = nonSystem.drop(excess)
            messages.clear()
            messages.addAll(systemMessages + trimmed)
            Log.d(TAG, "Window trimmed: removed $excess messages")
        }
    }
}
