package com.meapet.mobile.chat

import android.util.Log
import com.meapet.mobile.client.OpenAiCompatibleClient
import com.meapet.mobile.client.model.ApiRequest
import com.meapet.mobile.framework.AppConfig
import com.meapet.mobile.memory.MemoryManager
import com.meapet.mobile.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
 * @param client OpenAI 兼容 HTTP 客户端
 * @param conversationManager 会话管理器
 * @param memoryManager 记忆管理器（null = 禁用记忆）
 * @param settingsManager 设置管理器
 * @param config 应用配置
 */
class ChatService(
    private val client: OpenAiCompatibleClient,
    private val conversationManager: ConversationManager,
    private val memoryManager: MemoryManager?,
    private val settingsManager: SettingsManager,
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
                    stream = false
                )

                Log.d(TAG, "Sending request to $model (${apiMessages.size} messages)")

                // 5) 发送请求
                val responseJson = client.chatCompletion(requestBody)

                // 6) 解析响应
                val assistantContent = parseChatResponse(responseJson)
                val assistantMessage = ChatMessage(
                    role = ChatRole.assistant,
                    content = assistantContent
                )
                conversationManager.addMessage(assistantMessage)

                // 7) 事后处理：触发记忆提取
                memoryManager?.onExchangeComplete(content, assistantContent)

                Log.d(TAG, "Response received: ${assistantContent.take(80)}...")
                Result.success(userMessage to assistantMessage)

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

        // 移除最后一条 AI 回复（如果有）
        conversationManager.lastAssistantMessage()?.let {
            removeLastMessage()
        }

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

    /** 移除最后一条消息。 */
    fun removeLastMessage() {
        conversationManager.getMessages().lastOrNull()?.let {
            // ConversationManager 不直接提供 remove，这里通过 clear + re-add 实现
            val all = conversationManager.getMessages().dropLast(1)
            conversationManager.clear()
            conversationManager.addMessages(all)
        }
    }

    // ── API 响应解析 ──────────────────────────────────

    /**
     * 解析 OpenAI 兼容的 chat completion 响应。
     *
     * @param json 原始 JSON 响应字符串
     * @return 提取出的助手消息内容
     */
    private fun parseChatResponse(json: String): String {
        return try {
            val root = JSONObject(json)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                if (message != null) {
                    message.optString("content", "")
                } else {
                    // Stream 模式可能使用 delta
                    val delta = choice.optJSONObject("delta")
                    delta?.optString("content", "") ?: ""
                }
            } else {
                Log.w(TAG, "Unexpected API response: no choices")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse chat response", e)
            "(解析响应失败)"
        }
    }
}
