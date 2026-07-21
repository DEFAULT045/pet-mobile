package com.llz121517.meapet.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llz121517.meapet.ui.theme.THEME_PRESETS
import com.llz121517.meapet.ui.theme.findPreset
import com.llz121517.meapet.viewmodel.SettingsViewModel

/**
 * 设置页面。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val state by settingsViewModel.state.collectAsState()
    var apiKeyVisible by remember { mutableStateOf(false) }

    // ── 本地状态（保持光标位置） ──
    var localApiKey by remember { mutableStateOf(state.apiKey) }
    var localApiUrl by remember { mutableStateOf(state.apiUrl) }
    var localModel by remember { mutableStateOf(state.model) }
    var localSystemPrompt by remember { mutableStateOf(state.systemPrompt) }

    LaunchedEffect(state.apiKey) { if (localApiKey != state.apiKey) localApiKey = state.apiKey }
    LaunchedEffect(state.apiUrl) { if (localApiUrl != state.apiUrl) localApiUrl = state.apiUrl }
    LaunchedEffect(state.model) { if (localModel != state.model) localModel = state.model }
    LaunchedEffect(state.systemPrompt) { if (localSystemPrompt != state.systemPrompt) localSystemPrompt = state.systemPrompt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ══════════════════════════════════════════
            //  API 配置
            // ══════════════════════════════════════════
            SectionTitle("API 配置")

            OutlinedTextField(
                value = localApiKey,
                onValueChange = {
                    localApiKey = it
                    settingsViewModel.updateApiKey(it)
                },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (apiKeyVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(
                        onClick = { apiKeyVisible = !apiKeyVisible },
                        modifier = Modifier.width(56.dp)
                    ) {
                        Text(
                            text = if (apiKeyVisible) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = localApiUrl,
                onValueChange = {
                    localApiUrl = it
                    settingsViewModel.updateApiUrl(it)
                },
                label = { Text("API 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.openai.com") }
            )

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════
            //  模型参数
            // ══════════════════════════════════════════
            SectionTitle("模型参数")

            OutlinedTextField(
                value = localModel,
                onValueChange = {
                    localModel = it
                    settingsViewModel.updateModel(it)
                },
                label = { Text("模型") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("gpt-4o-mini") }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Temperature: ${"%.2f".format(state.temperature)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = state.temperature.toFloat(),
                onValueChange = { settingsViewModel.updateTemperature(it.toDouble()) },
                valueRange = 0f..2f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "最大 Token: ${state.maxTokens}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = state.maxTokens.toFloat(),
                onValueChange = { settingsViewModel.updateMaxTokens(it.toInt()) },
                valueRange = 256f..8192f,
                steps = 30,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════
            //  System Prompt
            // ══════════════════════════════════════════
            SectionTitle("System Prompt")
            OutlinedTextField(
                value = localSystemPrompt,
                onValueChange = {
                    localSystemPrompt = it
                    settingsViewModel.updateSystemPrompt(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 6
            )

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════
            //  记忆系统
            // ══════════════════════════════════════════
            SectionTitle("记忆系统")

            SettingsSwitchRow(
                label = "启用记忆",
                description = "保留对话中提取的重要信息",
                checked = state.enableMemory,
                onCheckedChange = { settingsViewModel.updateEnableMemory(it) }
            )
            SettingsSwitchRow(
                label = "自动摘要",
                description = "定期总结对话为长期记忆",
                checked = state.enableAutoSummary,
                onCheckedChange = { settingsViewModel.updateEnableAutoSummary(it) }
            )

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════
            //  主题
            // ══════════════════════════════════════════
            SectionTitle("主题")

            // ── 主题模式 ──
            ThemeModeSelector(
                current = state.themeMode,
                onSelect = { settingsViewModel.updateThemeMode(it) }
            )

            Spacer(Modifier.height(12.dp))

            // ── 动态颜色开关 ──
            SettingsSwitchRow(
                label = "使用系统动态颜色",
                description = "关闭后可选择预设主题色",
                checked = state.enableDynamicColor,
                onCheckedChange = { settingsViewModel.updateEnableDynamicColor(it) }
            )

            // ── 颜色预设选择区（关闭动态颜色时展开） ──
            AnimatedVisibility(
                visible = !state.enableDynamicColor,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ColorPresetSelector(
                    currentPreset = state.colorPreset,
                    onSelect = { settingsViewModel.updateColorPreset(it) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ══════════════════════════════════════════
            //  关于
            // ══════════════════════════════════════════
            SectionTitle("关于")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("MeaPet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "版本 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "基于 Live2D + AI 的虚拟宠物聊天应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "技术栈：Live2D Cubism · Jetpack Compose · Ktor · Coroutines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
//  子组件
// ═══════════════════════════════════════════════════

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    current: String,
    onSelect: (String) -> Unit
) {
    val options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == current }?.second ?: "跟随系统",
            onValueChange = {},
            readOnly = true,
            label = { Text("主题模式") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 颜色预设选择区——色块网格。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPresetSelector(
    currentPreset: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            "主题色预设",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            THEME_PRESETS.forEach { preset ->
                val isSelected = preset.id == currentPreset

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onSelect(preset.id) }
                        .width(56.dp)
                ) {
                    // 色块圆
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(preset.colorLight)
                            .then(
                                if (isSelected) Modifier.border(
                                    3.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ) else Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    CircleShape
                                )
                            )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
