package com.meapet.mobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meapet.mobile.chat.ChatEvent
import com.meapet.mobile.chat.ChatMessage
import com.meapet.mobile.chat.ChatRole
import com.meapet.mobile.chat.ChatService
import com.meapet.mobile.chat.ChatUiState
import com.meapet.mobile.chat.UpdateNoticeUi
import com.meapet.mobile.framework.MeaPetApplication
import com.meapet.mobile.live2d.Live2dManager
import com.meapet.mobile.memory.MemoryManager
import com.meapet.mobile.update.UpdateCheckResult
import com.meapet.mobile.update.UpdateChecker
import kotlinx.coroutines.Job
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
    private val updateChecker: UpdateChecker = container.updateChecker

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** 在途发送任务；清空会话时取消，避免回复回写到已清空的会话。 */
    private var sendJob: Job? = null

    /** 关于页手动检测任务。 */
    private var checkUpdateJob: Job? = null

    init {
        // 初始化时从 ConversationManager 加载已有消息
        val existingMessages = chatService.getHistory()
        if (existingMessages.isNotEmpty()) {
            _state.update { it.copy(messages = existingMessages) }
        }

        // 监听 Live2D 触摸分区事件→添加系统消息气泡，超时后自动移除
        viewModelScope.launch {
            Live2dManager.tapMessageEvent.collect { text ->
                val newMsg = ChatMessage(role = ChatRole.system, content = text)
                _state.update { it.copy(messages = it.messages + newMsg) }

                // 按"当前系统消息总数"决定此条消息的存活时长
                val sysCount = _state.value.messages.count { it.role == ChatRole.system }
                val timeout = when {
                    sysCount > 5 -> 2000L
                    sysCount > 3 -> 4000L
                    else -> 7000L
                }
                // 延时后移除（新消息推旧→消失前一直可见，自然积累后加速淘汰）
                launch {
                    kotlinx.coroutines.delay(timeout)
                    _state.update {
                        it.copy(messages = it.messages.filterNot { m -> m.id == newMsg.id })
                    }
                }
            }
        }

        // 启动时静默检测一次：仅在有新版本时提示，失败不打扰
        viewModelScope.launch {
            when (val result = updateChecker.check()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _state.update {
                        it.copy(
                            updateNotice = UpdateNoticeUi(
                                message = "发现新版本 v${result.release.versionName}",
                                url = result.release.htmlUrl
                            )
                        )
                    }
                }
                is UpdateCheckResult.UpToDate,
                is UpdateCheckResult.Failed -> Unit
            }
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
            is ChatEvent.DismissUpdateNotice -> dismissUpdateNotice()
            is ChatEvent.CheckForUpdate -> checkForUpdate()
            is ChatEvent.DismissAboutUpdateMessage -> dismissAboutUpdateMessage()
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

        sendJob = viewModelScope.launch {
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

        sendJob = viewModelScope.launch {
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
        // 取消在途发送，否则回复到达后会把消息回写进已清空的会话
        sendJob?.cancel()
        sendJob = null
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

    private fun dismissUpdateNotice() {
        _state.update { it.copy(updateNotice = null) }
    }

    private fun dismissAboutUpdateMessage() {
        _state.update { it.copy(aboutUpdateMessage = null, aboutReleaseUrl = null) }
    }

    /** 关于页手动检测：始终反馈结果（有更新 / 已是最新 / 失败）。 */
    private fun checkForUpdate() {
        if (_state.value.isCheckingUpdate) return
        checkUpdateJob?.cancel()
        _state.update {
            it.copy(
                isCheckingUpdate = true,
                aboutUpdateMessage = null,
                aboutReleaseUrl = null
            )
        }
        checkUpdateJob = viewModelScope.launch {
            when (val result = updateChecker.check()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _state.update {
                        it.copy(
                            isCheckingUpdate = false,
                            aboutUpdateMessage = "发现新版本 v${result.release.versionName}",
                            aboutReleaseUrl = result.release.htmlUrl
                        )
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _state.update {
                        it.copy(
                            isCheckingUpdate = false,
                            aboutUpdateMessage = "当前已是最新版本（v${result.currentVersion}）",
                            aboutReleaseUrl = null
                        )
                    }
                }
                is UpdateCheckResult.Failed -> {
                    _state.update {
                        it.copy(
                            isCheckingUpdate = false,
                            aboutUpdateMessage = result.message,
                            aboutReleaseUrl = null
                        )
                    }
                }
            }
        }
    }
}
