package com.meapet.mobile.chat

import android.util.Log
import com.meapet.mobile.client.OpenAiCompatibleClient
import com.meapet.mobile.client.model.ApiRequest
import com.meapet.mobile.client.model.ApiResponse
import com.meapet.mobile.framework.AppConfig
import com.meapet.mobile.memory.MemoryManager
import com.meapet.mobile.settings.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聊天业务服务。
 *
 * ## 职责
 * - 发送消息 → 调用 [OpenAiCompatibleClient] → 解析响应；
 * - 与 [MemoryManager] 协作注入记忆上下文；
 * - 将对话记录交给 [ConversationManager] 管理；
 * - 每次对话后触发记忆提取。
 *
 * ## 低耦合
 * - 不依赖任何 UI 组件；
 * - 通过 [MemoryManager] 与记忆系统交互（而非直接操作 MemoryRepository）；
 * - 通过 [SettingsManager] 获取配置（而非硬编码）。
 *
 * @param clientProvider 提供 OpenAI 兼容 HTTP 客户端的 provider（每次请求重新获取，
 *   以便 API Key / Base URL 变更重建客户端后立即生效）
 * @param conversationManager 会话管理器
 * @param memoryManager 记忆管理器（null = 禁用记忆）
 * @param settingsManager 设置管理器
 * @param postProcessScope 事后处理（记忆提取/摘要）用的应用级作用域；
 *   摘要可能触发额外网络请求，不能阻塞 sendMessage 返回
 * @param config 应用配置
 */
class ChatService(
    private val clientProvider: () -> OpenAiCompatibleClient,
    private val conversationManager: ConversationManager,
    private val memoryManager: MemoryManager?,
    private val settingsManager: SettingsManager,
    private val postProcessScope: CoroutineScope,
    private val config: AppConfig = AppConfig.DEFAULT
) {
    companion object {
        private const val TAG = "ChatService"
    }

    /**
     * 发送消息并获取 AI 回复。
     *
     * @param content 用户消息文本
     * @return 包含用户消息与 AI 回复的 Pair
     */
    suspend fun sendMessage(content: String): Result<Pair<ChatMessage, ChatMessage>> {
        return withContext(Dispatchers.IO) {
            try {
                // 1) 构建用户消息
                val userMessage = ChatMessage(
                    role = ChatRole.user,
                    content = content
                )
                conversationManager.addMessage(userMessage)

                // 2) 获取记忆上下文
                val contextMemory = memoryManager?.buildContext(content) ?: ""

                // 3) 获取设置
                val systemPrompt = settingsManager.getSystemPrompt()
                val model = settingsManager.getModel()
                val temperature = settingsManager.getTemperature()
                val maxTokens = settingsManager.getMaxTokens()

                // 4) 构建 API 请求
                val apiMessages = conversationManager.buildApiMessages(
                    systemPrompt = systemPrompt,
                    contextMemory = contextMemory,
                    maxMessages = config.maxHistoryMessages
                )

                val jsonMessages = apiMessages.map { msg ->
                    ApiRequest.textMessage(msg.role.name, msg.content)
                }

                val requestBody = ApiRequest.chatCompletion(
                    model = model,
                    messages = jsonMessages,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    stream = false
                )

                Log.d(TAG, "Sending request to $model (${apiMessages.size} messages)")

                // 5) 发送请求
                val responseJson = clientProvider().chatCompletion(requestBody)

                // 6) 解析响应（choices 为空或 content 缺失视为失败，不入史）
                val assistantContent = ApiResponse.chatCompletionContent(responseJson)
                    ?.takeIf { it.isNotBlank() }
                if (assistantContent == null) {
                    Log.w(TAG, "Unexpected API response: missing choices or content")
                    return@withContext Result.failure(
                        IllegalStateException("API 响应中没有有效的回复内容")
                    )
                }
                val assistantMessage = ChatMessage(
                    role = ChatRole.assistant,
                    content = assistantContent
                )
                conversationManager.addMessage(assistantMessage)

                // 7) 事后处理：记忆提取/摘要转后台，不阻塞本次回复返回
                //   （每 N 轮的自动摘要是一次额外 LLM 请求，同步等它会让 UI
                //     在拿到回复后仍长时间显示加载中）
                memoryManager?.let { mm ->
                    postProcessScope.launch {
                        try {
                            mm.onExchangeComplete(content, assistantContent)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Memory post-processing failed", e)
                        }
                    }
                }

                Log.d(TAG, "Response received (${assistantContent.length} chars)")
                Result.success(userMessage to assistantMessage)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 重新发送上一条消息（用于失败重试）。
     */
    suspend fun retryLastMessage(): Result<Pair<ChatMessage, ChatMessage>> {
        val lastUserMsg = conversationManager.lastUserMessage()
            ?: return Result.failure(IllegalStateException("No message to retry"))

        // 把该条 user 之后的 assistant 回复（若有）连同 user 本身一并移除，
        // 重发时由 sendMessage 统一重新入史，避免消息重复
        val history = conversationManager.getMessages()
        val userIdx = history.indexOfLast { it.id == lastUserMsg.id }
        history.drop(userIdx + 1)
            .filter { it.role == ChatRole.assistant }
            .forEach { conversationManager.removeMessage(it.id) }
        conversationManager.removeMessage(lastUserMsg.id)

        return sendMessage(lastUserMsg.content)
    }

    /** 获取当前会话历史。 */
    fun getHistory(): List<ChatMessage> = conversationManager.getMessages()

    /** 获取最近的消息交换对（用于记忆系统）。 */
    fun getRecentExchanges(count: Int = 10): List<Pair<String, String>> =
        conversationManager.getRecentExchanges(count)

    /** 清除历史。 */
    fun clearHistory() {
        conversationManager.clear()
        Log.i(TAG, "Chat history cleared")
    }
}
