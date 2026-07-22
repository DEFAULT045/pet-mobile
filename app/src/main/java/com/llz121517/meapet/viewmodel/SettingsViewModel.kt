package com.llz121517.meapet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llz121517.meapet.framework.MeaPetApplication
import com.llz121517.meapet.settings.SettingsKeys
import com.llz121517.meapet.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置界面 ViewModel。
 *
 * @property apiKey API Key（不可逆，仅显示掩码）
 * @property apiUrl API 基础 URL
 * @property model 模型名
 * @property temperature Temperature 值
 * @property maxTokens 最大 Token 数
 * @property systemPrompt 系统提示词
 * @property enableMemory 是否启用记忆
 * @property enableAutoSummary 是否启用自动摘要
 * @property themeMode 主题模式
 */
data class SettingsUiState(
    val apiKey: String = "",
    val apiKeyMasked: String = "",
    val apiUrl: String = SettingsKeys.Defaults.API_URL,
    val model: String = SettingsKeys.Defaults.MODEL,
    val temperature: Double = SettingsKeys.Defaults.TEMPERATURE,
    val maxTokens: Int = SettingsKeys.Defaults.MAX_TOKENS,
    val systemPrompt: String = SettingsKeys.Defaults.SYSTEM_PROMPT,
    val enableMemory: Boolean = SettingsKeys.Defaults.ENABLE_MEMORY,
    val enableAutoSummary: Boolean = SettingsKeys.Defaults.ENABLE_AUTO_SUMMARY,
    val themeMode: String = SettingsKeys.Defaults.THEME_MODE,
    val enableDynamicColor: Boolean = SettingsKeys.Defaults.ENABLE_DYNAMIC_COLOR,
    val colorPreset: String = SettingsKeys.Defaults.COLOR_PRESET,
    val appVersion: String = ""
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = MeaPetApplication.from(application)
    private val settingsManager: SettingsManager = container.settingsManager

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // 订阅所有设置流
        viewModelScope.launch {
            settingsManager.apiKeyFlow.collect { key ->
                _state.update {
                    it.copy(
                        apiKey = key,
                        apiKeyMasked = maskApiKey(key)
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsManager.apiUrlFlow.collect { url ->
                _state.update { it.copy(apiUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsManager.modelFlow.collect { model ->
                _state.update { it.copy(model = model) }
            }
        }
        viewModelScope.launch {
            settingsManager.temperatureFlow.collect { temp ->
                _state.update { it.copy(temperature = temp) }
            }
        }
        viewModelScope.launch {
            settingsManager.maxTokensFlow.collect { tokens ->
                _state.update { it.copy(maxTokens = tokens) }
            }
        }
        viewModelScope.launch {
            settingsManager.systemPromptFlow.collect { prompt ->
                _state.update { it.copy(systemPrompt = prompt) }
            }
        }
        viewModelScope.launch {
            settingsManager.enableMemoryFlow.collect { enabled ->
                _state.update { it.copy(enableMemory = enabled) }
            }
        }
        viewModelScope.launch {
            settingsManager.enableAutoSummaryFlow.collect { enabled ->
                _state.update { it.copy(enableAutoSummary = enabled) }
            }
        }
        viewModelScope.launch {
            settingsManager.themeModeFlow.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsManager.enableDynamicColorFlow.collect { enabled ->
                _state.update { it.copy(enableDynamicColor = enabled) }
            }
        }
        viewModelScope.launch {
            settingsManager.colorPresetFlow.collect { preset ->
                _state.update { it.copy(colorPreset = preset) }
            }
        }

        // 从 PackageManager 读取版本号
        val version = try {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
        _state.update { it.copy(appVersion = version) }
    }

    // ── 更新方法 ──────────────────────────────────────

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.setApiKey(key)
            container.reloadClient()
        }
    }

    fun updateApiUrl(url: String) {
        viewModelScope.launch {
            settingsManager.setApiUrl(url)
            container.reloadClient()
        }
    }

    fun updateModel(model: String) {
        viewModelScope.launch { settingsManager.setModel(model) }
    }

    fun updateTemperature(temp: Double) {
        viewModelScope.launch { settingsManager.setTemperature(temp) }
    }

    fun updateMaxTokens(tokens: Int) {
        viewModelScope.launch { settingsManager.setMaxTokens(tokens) }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch { settingsManager.setSystemPrompt(prompt) }
    }

    fun updateEnableMemory(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setEnableMemory(enabled) }
    }

    fun updateEnableAutoSummary(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setEnableAutoSummary(enabled) }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch { settingsManager.setThemeMode(mode) }
    }

    fun updateEnableDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setEnableDynamicColor(enabled) }
    }

    fun updateColorPreset(preset: String) {
        viewModelScope.launch { settingsManager.setColorPreset(preset) }
    }

    // ── 工具 ──────────────────────────────────────────

    private fun maskApiKey(key: String): String {
        if (key.length <= 8) return "****"
        return "${key.take(4)}****${key.takeLast(4)}"
    }
}
