package com.meapet.mobile.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 顶部右上角菜单按钮组件。
 */
@Composable
fun OverlayMenu(
    onSettings: () -> Unit = {},
    onClearConversation: () -> Unit = {},
    onToggleOverlay: () -> Unit = {},
    onAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(menuExpanded) {
        if (menuExpanded) {
            showPopup = true
            animProgress.animateTo(1f, animationSpec = tween(200))
        } else if (showPopup) {
            animProgress.animateTo(0f, animationSpec = tween(200))
            showPopup = false
        }
    }

    Box(
        modifier = modifier
            .padding(top = 48.dp, end = 12.dp)
            .onGloballyPositioned { anchorHeight = it.size.height }
            .background(
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多选项",
                tint = Color.White
            )
        }

        if (showPopup) {
            Popup(
                onDismissRequest = { menuExpanded = false },
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = 0, y = anchorHeight + 8),
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 130.dp)
                        .graphicsLayer {
                            alpha = animProgress.value
                            scaleX = 0.85f + 0.15f * animProgress.value
                            scaleY = 0.85f + 0.15f * animProgress.value
                            transformOrigin = TransformOrigin(1f, 0f) // 右上角锚点缩放
                        },
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        DropdownMenuItem(
                            text = { Text("设置", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { menuExpanded = false; onSettings() },
                            leadingIcon = { Text("⚙", style = MaterialTheme.typography.bodyMedium) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("清除对话", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { menuExpanded = false; onClearConversation() },
                            leadingIcon = { Text("🗨", style = MaterialTheme.typography.bodyMedium) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("悬浮窗", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { menuExpanded = false; onToggleOverlay() },
                            leadingIcon = { Text("🪟", style = MaterialTheme.typography.bodyMedium) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("关于", style = MaterialTheme.typography.bodyMedium) },
                            onClick = { menuExpanded = false; onAbout() },
                            leadingIcon = { Text("ℹ", style = MaterialTheme.typography.bodyMedium) }
                        )
                    }
                }
            }
        }
    }
}
