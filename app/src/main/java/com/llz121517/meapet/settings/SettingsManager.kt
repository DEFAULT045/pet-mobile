package com.llz121517.meapet.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * 应用设置管理器。
 *
 * 基于 DataStore 的键值持久化，所有操作通过 Flow 暴露，
 * UI 层可响应式订阅变更。
 *
 * 该模块**不依赖**任何其他模块，可独立测试。
 *
 * @param context Application Context
 */
class SettingsManager(context: Context) {

    private val dataStore = context.appDataStore

    companion object {
        private const val TAG = "SettingsManager"
    }

    // ── Keys ──────────────────────────────────────────

    private val KEY_API_KEY = stringPreferencesKey(SettingsKeys.API_KEY)
    private val KEY_API_URL = stringPreferencesKey(SettingsKeys.API_URL)
    private val KEY_MODEL = stringPreferencesKey(SettingsKeys.MODEL)
    private val KEY_TEMPERATURE = doublePreferencesKey(SettingsKeys.TEMPERATURE)
    private val KEY_MAX_TOKENS = intPreferencesKey(SettingsKeys.MAX_TOKENS)
    private val KEY_ENABLE_MEMORY = booleanPreferencesKey(SettingsKeys.ENABLE_MEMORY)
    private val KEY_ENABLE_AUTO_SUMMARY = booleanPreferencesKey(SettingsKeys.ENABLE_AUTO_SUMMARY)
    private val KEY_SYSTEM_PROMPT = stringPreferencesKey(SettingsKeys.SYSTEM_PROMPT)
    private val KEY_THEME_MODE = stringPreferencesKey(SettingsKeys.THEME_MODE)
    private val KEY_FIRST_LAUNCH = booleanPreferencesKey(SettingsKeys.FIRST_LAUNCH)

    // ── Flows (响应式订阅) ────────────────────────────

    /** API Key 流。 */
    val apiKeyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_API_KEY] ?: ""
    }

    /** API URL 流。 */
    val apiUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_API_URL] ?: SettingsKeys.Defaults.API_URL
    }

    /** 模型流。 */
    val modelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MODEL] ?: SettingsKeys.Defaults.MODEL
    }

    /** Temperature 流。 */
    val temperatureFlow: Flow<Double> = dataStore.data.map { prefs ->
        prefs[KEY_TEMPERATURE] ?: SettingsKeys.Defaults.TEMPERATURE
    }

    /** Max tokens 流。 */
    val maxTokensFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_MAX_TOKENS] ?: SettingsKeys.Defaults.MAX_TOKENS
    }

    /** 记忆开关流。 */
    val enableMemoryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_MEMORY] ?: SettingsKeys.Defaults.ENABLE_MEMORY
    }

    /** 自动摘要开关流。 */
    val enableAutoSummaryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_AUTO_SUMMARY] ?: SettingsKeys.Defaults.ENABLE_AUTO_SUMMARY
    }

    /** System prompt 流。 */
    val systemPromptFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_PROMPT] ?: SettingsKeys.Defaults.SYSTEM_PROMPT
    }

    /** 主题模式流。 */
    val themeModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: SettingsKeys.Defaults.THEME_MODE
    }

    /** 首次启动标记流。 */
    val isFirstLaunchFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }

    // ── 同步 getter（非 Flow 场景使用，如 Client 构造） ──

    fun getApiKey(): String = runBlocking { dataStore.data.first()[KEY_API_KEY] ?: "" }
    fun getApiUrl(): String = runBlocking { dataStore.data.first()[KEY_API_URL] ?: SettingsKeys.Defaults.API_URL }
    fun getModel(): String = runBlocking { dataStore.data.first()[KEY_MODEL] ?: SettingsKeys.Defaults.MODEL }
    fun getTemperature(): Double = runBlocking { dataStore.data.first()[KEY_TEMPERATURE] ?: SettingsKeys.Defaults.TEMPERATURE }
    fun getMaxTokens(): Int = runBlocking { dataStore.data.first()[KEY_MAX_TOKENS] ?: SettingsKeys.Defaults.MAX_TOKENS }
    fun getSystemPrompt(): String = runBlocking { dataStore.data.first()[KEY_SYSTEM_PROMPT] ?: SettingsKeys.Defaults.SYSTEM_PROMPT }
    fun isMemoryEnabled(): Boolean = runBlocking { dataStore.data.first()[KEY_ENABLE_MEMORY] ?: SettingsKeys.Defaults.ENABLE_MEMORY }
    fun isAutoSummaryEnabled(): Boolean = runBlocking { dataStore.data.first()[KEY_ENABLE_AUTO_SUMMARY] ?: SettingsKeys.Defaults.ENABLE_AUTO_SUMMARY }

    // ── 写入方法 ──────────────────────────────────────

    suspend fun setApiKey(key: String) {
        dataStore.edit { prefs -> prefs[KEY_API_KEY] = key }
        Log.d(TAG, "API Key updated")
    }

    suspend fun setApiUrl(url: String) {
        dataStore.edit { prefs -> prefs[KEY_API_URL] = url }
        Log.d(TAG, "API URL updated: $url")
    }

    suspend fun setModel(model: String) {
        dataStore.edit { prefs -> prefs[KEY_MODEL] = model }
    }

    suspend fun setTemperature(temp: Double) {
        dataStore.edit { prefs -> prefs[KEY_TEMPERATURE] = temp }
    }

    suspend fun setMaxTokens(tokens: Int) {
        dataStore.edit { prefs -> prefs[KEY_MAX_TOKENS] = tokens }
    }

    suspend fun setEnableMemory(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLE_MEMORY] = enabled }
    }

    suspend fun setEnableAutoSummary(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ENABLE_AUTO_SUMMARY] = enabled }
    }

    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { prefs -> prefs[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode }
    }

    suspend fun markFirstLaunchDone() {
        dataStore.edit { prefs -> prefs[KEY_FIRST_LAUNCH] = false }
    }

    /** 清除所有设置（恢复出厂）。 */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
        Log.i(TAG, "All settings cleared")
    }
}
