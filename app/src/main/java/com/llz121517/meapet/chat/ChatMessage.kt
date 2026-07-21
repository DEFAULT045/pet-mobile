package com.llz121517.meapet.chat

import java.util.UUID

/**
 * 消息角色枚举。
 */
enum class ChatRole { system, user, assistant }

/**
 * 聊天消息领域模型。
 *
 * 该模型贯穿整个聊天子系统，从 ChatService → ConversationManager → ViewModel → UI。
 *
 * @property role 消息角色
 * @property content 消息文本内容
 * @property id 唯一标识（UUID）
 * @property timestamp 消息时间戳
 * @property isStreaming 是否正在流式输出中
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
) {
    /** 是否为用户消息。 */
    val isUser: Boolean get() = role == ChatRole.user

    /** 是否为助手消息。 */
    val isAssistant: Boolean get() = role == ChatRole.assistant

    /** 是否为系统消息。 */
    val isSystem: Boolean get() = role == ChatRole.system
}
