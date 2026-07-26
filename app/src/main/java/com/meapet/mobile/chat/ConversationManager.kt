package com.meapet.mobile.chat

import android.util.Log

/**
 * 会话历史管理器。
 *
 * ## 职责
 * - 维护消息列表的添加、查询、清空；
 * - 实现滑动窗口：超出 [maxSize] 时丢弃最早的非 system 消息；
 * - 为 API 请求组装消息列表（含 system prompt）；
 * - 消息变更后通过 [ConversationStore] 异步持久化（可选）。
 *
 * ## 低耦合
 * - 内存操作 + 可选的存储回调，不依赖其他业务模块；
 * - 不关心消息是用户还是助手发送的，仅按顺序维护。
 *
 * ## 线程安全
 * 启动时 [restore] 在 IO 线程执行，可能与发送链路并发访问，
 * 所有读写方法用 [lock] 串行化。
 *
 * @param maxSize 最大保留消息数
 * @param store 会话持久化存储（null = 纯内存，不落盘）
 */
class ConversationManager(
    private val maxSize: Int = 50,
    private val store: ConversationStore? = null
) {
    private val lock = Any()
    private val messages = mutableListOf<ChatMessage>()

    companion object {
        private const val TAG = "ConversationManager"
    }

    /** 当前消息数量。 */
    val size: Int get() = synchronized(lock) { messages.size }

    /** 添加一条消息。自动触发窗口裁剪。 */
    fun addMessage(message: ChatMessage) {
        synchronized(lock) {
            messages.add(message)
            trimWindow()
            persistLocked()
        }
        // 只记角色与长度，对话内容不进 Logcat（隐私）
        Log.d(TAG, "Message added [${message.role}] (${message.content.length} chars, total: $size)")
    }

    /** 批量添加。 */
    fun addMessages(newMessages: List<ChatMessage>) {
        synchronized(lock) {
            messages.addAll(newMessages)
            trimWindow()
            persistLocked()
        }
    }

    /**
     * 恢复持久化的会话历史（启动加载用）。
     *
     * 与 [addMessages] 的区别：插到已有消息**前面**（加载完成前可能已产生新对话），
     * 按 id 去重，且不触发回写（避免加载即覆盖）。
     */
    fun restore(persisted: List<ChatMessage>) {
        if (persisted.isEmpty()) return
        synchronized(lock) {
            val existingIds = messages.map { it.id }.toSet()
            messages.addAll(0, persisted.filter { it.id !in existingIds })
            trimWindow()
        }
        Log.i(TAG, "Restored ${persisted.size} messages from disk (total: $size)")
    }

    /** 获取所有消息（不可变快照）。 */
    fun getMessages(): List<ChatMessage> = synchronized(lock) { messages.toList() }

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
        val recentMessages = synchronized(lock) {
            messages.filter { it.role != ChatRole.system }.takeLast(maxMessages)
        }

        return listOf(systemMsg) + recentMessages
    }

    /** 清除所有消息。 */
    fun clear() {
        synchronized(lock) {
            messages.clear()
            persistLocked()
        }
        Log.i(TAG, "Conversation cleared")
    }

    /** 按 id 移除消息。@return 是否有消息被移除 */
    fun removeMessage(id: String): Boolean = synchronized(lock) {
        val removed = messages.removeAll { it.id == id }
        if (removed) persistLocked()
        removed
    }

    /** 获取最后一条非 system 消息。 */
    fun lastUserMessage(): ChatMessage? = synchronized(lock) {
        messages.lastOrNull { it.role == ChatRole.user }
    }

    /** 获取最后一条助手消息。 */
    fun lastAssistantMessage(): ChatMessage? = synchronized(lock) {
        messages.lastOrNull { it.role == ChatRole.assistant }
    }

    // ── 内部 ──────────────────────────────────────────

    /** 必须在持有 [lock] 时调用。 */
    private fun persistLocked() {
        store?.persistAsync(messages.toList())
    }

    /** 必须在持有 [lock] 时调用。 */
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
