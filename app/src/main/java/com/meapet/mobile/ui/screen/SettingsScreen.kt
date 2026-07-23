package com.meapet.mobile.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meapet.mobile.ui.theme.THEME_PRESETS
import com.meapet.mobile.ui.theme.findPreset
import com.meapet.mobile.viewmodel.SettingsViewModel

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

    // 深色与否跟随应用内主题设置（与页面整体配色取值一致），仅"跟随系统"时看系统
    val darkTheme = when (state.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    // ── 本地编辑状态（进入页面时取一次已存值，失焦/离开页面时才写回） ──
    var localApiKey by remember { mutableStateOf(state.apiKey) }
    var localApiUrl by remember { mutableStateOf(state.apiUrl) }
    var localModel by remember { mutableStateOf(state.model) }
    var localSystemPrompt by remember { mutableStateOf(state.systemPrompt) }
    var localTemperature by remember { mutableStateOf(state.temperature.toFloat()) }
    var localMaxTokens by remember { mutableStateOf(state.maxTokens.toFloat()) }

    // 离开页面时兜底保存（焦点还留在输入框内的场景）
    DisposableEffect(Unit) {
        onDispose {
            settingsViewModel.saveApiKey(localApiKey)
            settingsViewModel.saveApiUrl(localApiUrl)
            settingsViewModel.saveModel(localModel)
            settingsViewModel.saveSystemPrompt(localSystemPrompt)
        }
    }

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

            Text(
                "需要一个 OpenAI 兼容的 API 端点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = localApiKey,
                onValueChange = { localApiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) settingsViewModel.saveApiKey(localApiKey) },
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = localApiUrl,
                onValueChange = { localApiUrl = it },
                label = { Text("API 地址") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) settingsViewModel.saveApiUrl(localApiUrl) },
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
                onValueChange = { localModel = it },
                label = { Text("模型") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) settingsViewModel.saveModel(localModel) },
                singleLine = true,
                placeholder = { Text("gpt-4o-mini") }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Temperature: ${"%.2f".format(localTemperature)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val inactiveTrackColor = if (darkTheme) Color(0xFF999999).copy(alpha = 0.3f)
                                      else Color.White.copy(alpha = 0.35f)
            Slider(
                value = localTemperature,
                onValueChange = { localTemperature = it },
                onValueChangeFinished = {
                    settingsViewModel.updateTemperature(localTemperature.toDouble())
                },
                valueRange = 0f..2f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(inactiveTrackColor = inactiveTrackColor)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "最大 Token: ${localMaxTokens.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = localMaxTokens,
                onValueChange = { localMaxTokens = it },
                onValueChangeFinished = {
                    settingsViewModel.updateMaxTokens(localMaxTokens.toInt())
                },
                valueRange = 256f..8192f,
                steps = 30,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(inactiveTrackColor = inactiveTrackColor)
            )

            Spacer(Modifier.height(16.dp))

            // ══════════════════════════════════════════
            //  System Prompt
            // ══════════════════════════════════════════
            SectionTitle("System Prompt")
            OutlinedTextField(
                value = localSystemPrompt,
                onValueChange = { localSystemPrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .onFocusChanged {
                        if (!it.isFocused) settingsViewModel.saveSystemPrompt(localSystemPrompt)
                    },
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
                darkTheme = darkTheme,
                onCheckedChange = { settingsViewModel.updateEnableMemory(it) }
            )
            SettingsSwitchRow(
                label = "自动摘要",
                description = "定期总结对话为长期记忆",
                checked = state.enableAutoSummary,
                darkTheme = darkTheme,
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
            val dynamicColorSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            SettingsSwitchRow(
                label = "使用系统动态颜色",
                description = if (dynamicColorSupported) "关闭后可选择预设主题色" else "当前系统不支持动态颜色",
                checked = state.enableDynamicColor && dynamicColorSupported,
                darkTheme = darkTheme,
                onCheckedChange = { if (dynamicColorSupported) settingsViewModel.updateEnableDynamicColor(it) },
                enabled = dynamicColorSupported
            )

            // ── 颜色预设选择区（关闭动态颜色时展开） ──
            AnimatedVisibility(
                visible = !(state.enableDynamicColor && dynamicColorSupported),
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
                    // ── 标题与简介 ──
                    Text("MeaPet —— 梅尔桌宠", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "一只基于 Live2D 的 AI 梅尔 非常不完善 但是初版花了我 0.14B Tokens\n" +
                                "目前为止共消耗 0.268B Tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "版本 ${state.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ── 开发与技术 ──
                    Text(
                        "借助 Claude Code CLI，由 DeepSeek V4 Flash 强力赋能辅助开发",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Author: llz121517 (GitHub)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "反馈/交流 QQ: 748791823",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "技术栈：Live2D Cubism · Jetpack Compose · Ktor · Coroutines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
    darkTheme: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedThumbColor = if (darkTheme) MaterialTheme.colorScheme.outline
                                      else Color.White,
            )
        )
    }
}

@Composable
private fun ThemeModeSelector(
    current: String,
    onSelect: (String) -> Unit
) {
    val options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
    var expanded by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    var boxWidthPx by remember { mutableStateOf(0) }
    var boxHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            showPopup = true
            animProgress.animateTo(1f, animationSpec = tween(200))
        } else if (showPopup) {
            animProgress.animateTo(0f, animationSpec = tween(200))
            showPopup = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                boxWidthPx = it.size.width
                boxHeightPx = it.size.height
            }
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == current }?.second ?: "跟随系统",
            onValueChange = {},
            readOnly = true,
            label = { Text("主题模式") },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                                  else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        // 透明点击层——避免与 OutlinedTextField 的内部触摸处理冲突
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = !expanded }
        )

        if (showPopup) {
            val popupWidth = with(density) { boxWidthPx.toDp().coerceAtLeast(160.dp) }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = boxHeightPx + 4),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    modifier = Modifier
                        .width(popupWidth)
                        .graphicsLayer {
                            alpha = animProgress.value
                            scaleX = 0.95f + 0.05f * animProgress.value
                            scaleY = 0.95f + 0.05f * animProgress.value
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        options.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onSelect(value); expanded = false }
                            )
                        }
                    }
                }
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
                            .background(preset.seed)
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
