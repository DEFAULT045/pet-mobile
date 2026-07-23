package com.meapet.mobile.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meapet.mobile.chat.ChatEvent
import com.meapet.mobile.live2d.Live2dDelegate
import com.meapet.mobile.ui.component.ChatBubble
import com.meapet.mobile.ui.component.ChatInputBar
import com.meapet.mobile.ui.component.OverlayMenu
import com.meapet.mobile.viewmodel.ChatViewModel

/** 内部页面导航。 */
private enum class Page { CHAT, SETTINGS }

/**
 * 聊天界面入口。
 *
 * 内部管理 CHAT / SETTINGS 页面切换，带滑动过渡动画。
 */
@Composable
fun ChatScreenContent(
    onToggleOverlay: () -> Unit = {},
    chatViewModel: ChatViewModel = viewModel()
) {
    var currentPage by remember { mutableStateOf(Page.CHAT) }

    // 在设置页时拦截系统返回键 → 回到聊天页
    BackHandler(enabled = currentPage == Page.SETTINGS) {
        currentPage = Page.CHAT
    }

    // 切换页面时同步触摸分区开关（设置页内禁止穿透）
    LaunchedEffect(currentPage) {
        Live2dDelegate.getInstance().zoneTouchEnabled = currentPage == Page.CHAT
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            if (targetState == Page.SETTINGS) {
                // 进入设置页：新页从右滑入，当前页向左滑出
                (slideInHorizontally { width -> width } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
            } else {
                // 返回聊天页：新页从左侧滑入，当前页向右滑出
                (slideInHorizontally { width -> -width } + fadeIn())
                    .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
            }
        },
        label = "pageTransition"
    ) { page ->
        when (page) {
            Page.SETTINGS -> SettingsScreen(
                onBack = { currentPage = Page.CHAT }
            )

            Page.CHAT -> ChatPage(
                chatViewModel = chatViewModel,
                onToggleOverlay = onToggleOverlay,
                onOpenSettings = { currentPage = Page.SETTINGS }
            )
        }
    }
}

/**
 * 聊天主页面。
 */
@Composable
private fun ChatPage(
    chatViewModel: ChatViewModel,
    onToggleOverlay: () -> Unit,
    onOpenSettings: () -> Unit
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
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "开始和 Mea 对话吧！\n发送一条消息开始聊天 🐾",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // ── Layer 2: 顶部菜单 ──
        OverlayMenu(
            onToggleOverlay = onToggleOverlay,
            onClearConversation = {
                chatViewModel.onEvent(ChatEvent.ClearConversation)
            },
            onSettings = onOpenSettings,
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
