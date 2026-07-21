package com.llz121517.meapet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llz121517.meapet.chat.ChatEvent
import com.llz121517.meapet.chat.ChatMessage
import com.llz121517.meapet.chat.ChatRole
import com.llz121517.meapet.chat.ChatService
import com.llz121517.meapet.chat.ChatUiState
import com.llz121517.meapet.framework.MeaPetApplication
import com.llz121517.meapet.memory.MemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 聊天界面 ViewModel。
 *
 * ## 职责
 * - 持有 [ChatUiState] 并通过 StateFlow 暴露给 UI；
 * - 处理 [ChatEvent] 用户交互事件；
 * - 调用 [ChatService] 发送消息；
 * - 调用 [MemoryManager] 管理记忆。
 *
 * ## 生命周期
 * - 通过 AndroidViewModel 获取 Application Context；
 * - 从 [MeaPetApplication.container] 获取依赖。
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val container = MeaPetApplication.from(application)
    private val chatService: ChatService = container.chatService
    private val memoryManager: MemoryManager? = container.memoryManager

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        // 初始化时从 ConversationManager 加载已有消息
        val existingMessages = chatService.getHistory()
        if (existingMessages.isNotEmpty()) {
            _state.update { it.copy(messages = existingMessages) }
        }
    }

    /**
     * 处理 UI 事件。
     */
    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendMessage -> sendMessage(event.content)
            is ChatEvent.UpdateInput -> updateInput(event.text)
            is ChatEvent.ClearConversation -> clearConversation()
            is ChatEvent.ClearMemory -> clearMemory()
            is ChatEvent.RetryLastMessage -> retryLastMessage()
            is ChatEvent.DismissError -> dismissError()
            is ChatEvent.DismissMemoryInfo -> dismissMemoryInfo()
        }
    }

    // ── 事件处理 ──────────────────────────────────────

    private fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMessage = ChatMessage(role = ChatRole.user, content = content)
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                isLoading = true,
                error = null,
                inputText = ""
            )
        }

        viewModelScope.launch {
            val result = chatService.sendMessage(content)
            result.fold(
                onSuccess = { (userMsg, assistantMsg) ->
                    _state.update { current ->
                        // 按 ViewModel 的 ID 移除乐观消息，再换上 ChatService 的正式消息
                        val updatedMessages = current.messages
                            .filterNot { it.id == userMessage.id }
                            .let { list -> list + listOf(userMsg, assistantMsg) }
                        current.copy(
                            messages = updatedMessages,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "发送失败，请检查网络和 API Key"
                        )
                    }
                }
            )
        }
    }

    private fun retryLastMessage() {
        val lastUserMsg = _state.value.messages.lastOrNull { it.isUser }
            ?: return

        _state.update {
            it.copy(
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            val result = chatService.retryLastMessage()
            result.fold(
                onSuccess = { (userMsg, assistantMsg) ->
                    _state.update { current ->
                        val updated = current.messages
                            .filterNot { msg ->
                                msg.id == lastUserMsg.id || (
                                    msg.isAssistant && current.messages.indexOf(msg) >
                                        current.messages.indexOfLast { it.id == lastUserMsg.id }
                                )
                            }
                        current.copy(
                            messages = updated + listOf(userMsg, assistantMsg),
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "重试失败"
                        )
                    }
                }
            )
        }
    }

    private fun updateInput(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    private fun clearConversation() {
        chatService.clearHistory()
        _state.update {
            ChatUiState(memoryContextInfo = "对话已清除")
        }
    }

    private fun clearMemory() {
        viewModelScope.launch {
            memoryManager?.clearAll()
            _state.update {
                it.copy(memoryContextInfo = "记忆已全部清除")
            }
        }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun dismissMemoryInfo() {
        _state.update { it.copy(memoryContextInfo = null) }
    }
}
