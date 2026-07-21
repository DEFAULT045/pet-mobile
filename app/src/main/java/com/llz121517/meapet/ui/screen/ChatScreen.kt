package com.llz121517.meapet.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llz121517.meapet.chat.ChatEvent
import com.llz121517.meapet.ui.component.ChatBubble
import com.llz121517.meapet.ui.component.ChatInputBar
import com.llz121517.meapet.ui.component.OverlayMenu
import com.llz121517.meapet.viewmodel.ChatViewModel

/** 内部页面导航。 */
private enum class Page { CHAT, SETTINGS }

/**
 * 聊天界面 Composable 入口。
 *
 * ## 职责
 * - 管理页面导航（聊天/设置）；
 * - 通过 [ChatViewModel] 获取状态并分发事件；
 * - 组装消息列表、输入栏、菜单等 UI 组件；
 * - 显示 About 对话框。
 *
 * @param onToggleOverlay 悬浮窗切换回调
 * @param chatViewModel 可注入的 ViewModel
 */
@Composable
fun ChatScreenContent(
    onToggleOverlay: () -> Unit = {},
    chatViewModel: ChatViewModel = viewModel()
) {
    var currentPage by remember { mutableStateOf(Page.CHAT) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // 在设置页时拦截返回键 → 回到聊天页，而非直接退出应用
    BackHandler(enabled = currentPage == Page.SETTINGS) {
        currentPage = Page.CHAT
    }

    when (currentPage) {
        Page.SETTINGS -> {
            SettingsScreen(
                onBack = { currentPage = Page.CHAT }
            )
        }

        Page.CHAT -> {
            ChatPage(
                chatViewModel = chatViewModel,
                onToggleOverlay = onToggleOverlay,
                onOpenSettings = { currentPage = Page.SETTINGS },
                onShowAbout = { showAboutDialog = true }
            )
        }
    }

    // ── About 对话框 ──────────────────────────────
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("关于 MeaPet", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Column {
                    Text("版本 1.0.0")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "MeaPet 是一个基于 Live2D + AI 的虚拟宠物聊天应用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "技术栈",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "• Live2D Cubism\n• Jetpack Compose\n• Ktor Client\n• Kotlin Coroutines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

/**
 * 聊天主页面（消息列表 + 菜单 + 输入栏）。
 */
@Composable
private fun ChatPage(
    chatViewModel: ChatViewModel,
    onToggleOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowAbout: () -> Unit
) {
    val state by chatViewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 错误提示
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            chatViewModel.onEvent(ChatEvent.DismissError)
        }
    }

    // 新消息时自动滚动到底部
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // 清除记忆/对话后回显 Snackbar
    LaunchedEffect(state.memoryContextInfo) {
        state.memoryContextInfo?.let {
            snackbarHostState.showSnackbar(it)
            chatViewModel.onEvent(ChatEvent.DismissMemoryInfo)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Layer 1: 消息列表 ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (state.messages.isEmpty()) {
                // 空状态提示
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "开始和 Mea 对话吧！\n发送一条消息开始聊天 🐾",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            items(
                items = state.messages,
                key = { it.id }
            ) { message ->
                ChatBubble(message = message)
            }

            // loading 指示器
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mea 正在思考...",
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // ── Layer 2: 顶部菜单 ──
        OverlayMenu(
            onToggleOverlay = onToggleOverlay,
            onClearMemory = {
                chatViewModel.onEvent(ChatEvent.ClearMemory)
            },
            onClearConversation = {
                chatViewModel.onEvent(ChatEvent.ClearConversation)
            },
            onSettings = onOpenSettings,
            onAbout = onShowAbout,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // ── Layer 3: 底部输入栏 ──
        ChatInputBar(
            inputText = state.inputText,
            onInputChange = { chatViewModel.onEvent(ChatEvent.UpdateInput(it)) },
            onSend = { chatViewModel.onEvent(ChatEvent.SendMessage(state.inputText)) },
            isLoading = state.isLoading,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── Layer 4: Snackbar ──
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        )
    }
}
