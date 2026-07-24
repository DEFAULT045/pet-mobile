package com.meapet.mobile.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var showAbout by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    // 延迟移除 Dialog，为退出动画留出时间
    LaunchedEffect(showAbout) {
        if (showAbout) {
            showDialog = true
        } else if (showDialog) {
            delay(250)
            showDialog = false
        }
    }

    // BackHandler：关于浮层优先拦截
    BackHandler(enabled = showAbout) {
        showAbout = false
    }

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
            onAbout = { showAbout = true },
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

        // ── Layer 5: 关于卡片 ──
        if (showDialog) {
            AboutDialog(
                visible = showAbout,
                onDismiss = { showAbout = false }
            )
        }
    }
}

/**
 * 关于悬浮卡片——使用系统 Dialog 窗口，真正浮于所有内容之上。
 *
 * @param visible 控制动画：true=入场，false=退场
 * @param onDismiss 关闭回调（退场动画由外部 [showDialog] 延迟移除保证完整播放）
 */
@Composable
private fun AboutDialog(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            animProgress.animateTo(1f, animationSpec = tween(200))
        } else {
            animProgress.animateTo(0f, animationSpec = tween(200))
        }
    }

    Dialog(
        onDismissRequest = {
            if (visible) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
        val uriHandler = LocalUriHandler.current

        Card(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = animProgress.value
                    scaleX = 0.85f + 0.15f * animProgress.value
                    scaleY = 0.85f + 0.15f * animProgress.value
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("MeaPet —— 梅尔桌宠", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "一只基于 Live2D 的 AI 梅尔 非常不完善 但是初版花了我 0.14B Tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "版本 $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                Text(
                    "借助 Claude Code CLI，由 DeepSeek V4 Flash 强力赋能辅助开发",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))

                val linkStyle = MaterialTheme.typography.bodySmall
                LinkItem(
                    text = "Live2D 模型来源",
                    url = "https://www.bilibili.com/video/BV1AoX7BXEaN",
                    uriHandler = uriHandler,
                    style = linkStyle
                )
                Spacer(Modifier.height(2.dp))
                LinkItem(
                    text = "GitHub 仓库",
                    url = "https://github.com/llz121517/mea-pet-mobile",
                    uriHandler = uriHandler,
                    style = linkStyle
                )
                Spacer(Modifier.height(2.dp))
                LinkItem(
                    text = "交流 QQ 群",
                    url = "https://qm.qq.com/q/pD9vpN6zKg",
                    uriHandler = uriHandler,
                    style = linkStyle
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "技术栈：Live2D Cubism · Jetpack Compose · Ktor · Coroutines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { /* TODO: 检查更新 */ }
                    ) {
                        Text("检查更新")
                    }
                    TextButton(
                        onClick = {
                            if (visible) onDismiss()
                        }
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

/**
 * 可点击的超链接文本（浮层内使用）。
 */
@Composable
private fun LinkItem(
    text: String,
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall
) {
    val annotatedString = buildAnnotatedString {
        pushStringAnnotation(tag = "URL", annotation = url)
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(text)
        }
        pop()
    }

    ClickableText(
        text = annotatedString,
        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        onClick = { offset ->
            annotatedString.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { uriHandler.openUri(it.item) }
        }
    )
}
