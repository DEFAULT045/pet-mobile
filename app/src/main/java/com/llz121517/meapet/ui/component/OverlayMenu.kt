package com.llz121517.meapet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 顶部右上角菜单按钮组件。
 */
@Composable
fun OverlayMenu(
    onSettings: () -> Unit = {},
    onClearConversation: () -> Unit = {},
    onToggleOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .padding(top = 48.dp, end = 12.dp)
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
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("设置") },
                onClick = {
                    menuExpanded = false
                    onSettings()
                },
                leadingIcon = {
                    Text("⚙", style = MaterialTheme.typography.bodyLarge)
                }
            )
            DropdownMenuItem(
                text = { Text("清除对话") },
                onClick = {
                    menuExpanded = false
                    onClearConversation()
                },
                leadingIcon = {
                    Text("🗨", style = MaterialTheme.typography.bodyLarge)
                }
            )
            DropdownMenuItem(
                text = { Text("悬浮窗") },
                onClick = {
                    menuExpanded = false
                    onToggleOverlay()
                },
                leadingIcon = {
                    Text("🪟", style = MaterialTheme.typography.bodyLarge)
                }
            )
        }
    }
}
