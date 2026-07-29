package com.meapet.mobile.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 应用设置管理器。
 */
class SettingsManager(context: Context) {

    private val dataStore = context.appDataStore

    @Volatile
    private var cachedPrefs: Preferences? = null

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        cacheScope.launch {
            dataStore.data.collect { prefs ->
                cachedPrefs = prefs
            }
        }
    }

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
    private val KEY_SUMMARY_INTERVAL = intPreferencesKey(SettingsKeys.SUMMARY_INTERVAL)
    private val KEY_EXCHANGE_COUNT = intPreferencesKey(SettingsKeys.EXCHANGE_COUNT)
    private val KEY_SYSTEM_PROMPT = stringPreferencesKey(SettingsKeys.SYSTEM_PROMPT)
    private val KEY_THEME_MODE = stringPreferencesKey(SettingsKeys.THEME_MODE)
    private val KEY_ENABLE_DYNAMIC_COLOR = booleanPreferencesKey(SettingsKeys.ENABLE_DYNAMIC_COLOR)
    private val KEY_COLOR_PRESET = stringPreferencesKey(SettingsKeys.COLOR_PRESET)
    private val KEY_FIRST_LAUNCH = booleanPreferencesKey(SettingsKeys.FIRST_LAUNCH)
    private val KEY_CUSTOM_MODEL_PATH = stringPreferencesKey(SettingsKeys.CUSTOM_MODEL_PATH)
    private val KEY_LIVE2D_SCALE = doublePreferencesKey(SettingsKeys.LIVE2D_SCALE)
    private val KEY_LIVE2D_OFFSET_X = doublePreferencesKey(SettingsKeys.LIVE2D_OFFSET_X)
    private val KEY_LIVE2D_OFFSET_Y = doublePreferencesKey(SettingsKeys.LIVE2D_OFFSET_Y)

    // ── Flows (响应式订阅) ────────────────────────────

    val customModelPathFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_MODEL_PATH] ?: SettingsKeys.Defaults.CUSTOM_MODEL_PATH
    }
    val live2dScaleFlow: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_LIVE2D_SCALE] ?: SettingsKeys.Defaults.LIVE2D_SCALE.toDouble()).toFloat()
    }
    val live2dOffsetXFlow: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_LIVE2D_OFFSET_X] ?: SettingsKeys.Defaults.LIVE2D_OFFSET_X.toDouble()).toFloat()
    }
    val live2dOffsetYFlow: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[KEY_LIVE2D_OFFSET_Y] ?: SettingsKeys.Defaults.LIVE2D_OFFSET_Y.toDouble()).toFloat()
    }

    val apiKeyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_API_KEY] ?: ""
    }
    val apiUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_API_URL] ?: SettingsKeys.Defaults.API_URL
    }
    val modelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MODEL] ?: SettingsKeys.Defaults.MODEL
    }
    val temperatureFlow: Flow<Double> = dataStore.data.map { prefs ->
        prefs[KEY_TEMPERATURE] ?: SettingsKeys.Defaults.TEMPERATURE
    }
    val maxTokensFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_MAX_TOKENS] ?: SettingsKeys.Defaults.MAX_TOKENS
    }
    val enableMemoryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_MEMORY] ?: SettingsKeys.Defaults.ENABLE_MEMORY
    }
    val enableAutoSummaryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_AUTO_SUMMARY] ?: SettingsKeys.Defaults.ENABLE_AUTO_SUMMARY
    }
    val summaryIntervalFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_SUMMARY_INTERVAL] ?: SettingsKeys.Defaults.SUMMARY_INTERVAL
    }
    val systemPromptFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SYSTEM_PROMPT] ?: SettingsKeys.Defaults.SYSTEM_PROMPT
    }
    val themeModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: SettingsKeys.Defaults.THEME_MODE
    }
    val enableDynamicColorFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_DYNAMIC_COLOR] ?: SettingsKeys.Defaults.ENABLE_DYNAMIC_COLOR
    }
    val colorPresetFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_COLOR_PRESET] ?: SettingsKeys.Defaults.COLOR_PRESET
    }
    val isFirstLaunchFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: true
    }

    // ── 同步 getter ──

    private fun currentPrefs(): Preferences =
        cachedPrefs ?: runBlocking { dataStore.data.first() }.also { cachedPrefs = it }

    fun getApiKey(): String = currentPrefs()[KEY_API_KEY] ?: ""
    fun getApiUrl(): String = currentPrefs()[KEY_API_URL] ?: SettingsKeys.Defaults.API_URL
    fun getModel(): String = currentPrefs()[KEY_MODEL] ?: SettingsKeys.Defaults.MODEL
    fun getTemperature(): Double = currentPrefs()[KEY_TEMPERATURE] ?: SettingsKeys.Defaults.TEMPERATURE
    fun getMaxTokens(): Int = currentPrefs()[KEY_MAX_TOKENS] ?: SettingsKeys.Defaults.MAX_TOKENS
    fun getSystemPrompt(): String = currentPrefs()[KEY_SYSTEM_PROMPT] ?: SettingsKeys.Defaults.SYSTEM_PROMPT
    fun isMemoryEnabled(): Boolean = currentPrefs()[KEY_ENABLE_MEMORY] ?: SettingsKeys.Defaults.ENABLE_MEMORY
    fun isAutoSummaryEnabled(): Boolean = currentPrefs()[KEY_ENABLE_AUTO_SUMMARY] ?: SettingsKeys.Defaults.ENABLE_AUTO_SUMMARY
    fun getSummaryInterval(): Int = currentPrefs()[KEY_SUMMARY_INTERVAL] ?: SettingsKeys.Defaults.SUMMARY_INTERVAL
    fun getCustomModelPath(): String = currentPrefs()[KEY_CUSTOM_MODEL_PATH] ?: SettingsKeys.Defaults.CUSTOM_MODEL_PATH
    fun getLive2dScale(): Float = (currentPrefs()[KEY_LIVE2D_SCALE] ?: SettingsKeys.Defaults.LIVE2D_SCALE.toDouble()).toFloat()
    fun getLive2dOffsetX(): Float = (currentPrefs()[KEY_LIVE2D_OFFSET_X] ?: SettingsKeys.Defaults.LIVE2D_OFFSET_X.toDouble()).toFloat()
    fun getLive2dOffsetY(): Float = (currentPrefs()[KEY_LIVE2D_OFFSET_Y] ?: SettingsKeys.Defaults.LIVE2D_OFFSET_Y.toDouble()).toFloat()
    fun getExchangeCount(): Int = currentPrefs()[KEY_EXCHANGE_COUNT] ?: 0

    // ── 写入方法 ──

    suspend fun setApiKey(key: String) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_API_KEY] = key }
        Log.d(TAG, "API Key updated")
    }

    suspend fun setApiUrl(url: String) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_API_URL] = url }
        Log.d(TAG, "API URL updated: $url")
    }

    suspend fun setModel(model: String) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_MODEL] = model }
    }

    suspend fun setTemperature(temp: Double) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_TEMPERATURE] = temp }
    }

    suspend fun setMaxTokens(tokens: Int) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_MAX_TOKENS] = tokens }
    }

    suspend fun setEnableMemory(enabled: Boolean) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_ENABLE_MEMORY] = enabled }
    }

    suspend fun setEnableAutoSummary(enabled: Boolean) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_ENABLE_AUTO_SUMMARY] = enabled }
    }

    suspend fun setSummaryInterval(interval: Int) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_SUMMARY_INTERVAL] = interval }
    }

    suspend fun setExchangeCount(count: Int) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_EXCHANGE_COUNT] = count }
    }

    suspend fun setSystemPrompt(prompt: String) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setThemeMode(mode: String) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode }
    }

    suspend fun setEnableDynamicColor(enabled: Boolean) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_ENABLE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setColorPreset(preset: String) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_COLOR_PRESET] = preset }
    }

    suspend fun markFirstLaunchDone() {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_FIRST_LAUNCH] = false }
    }

    suspend fun setCustomModelPath(path: String) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_CUSTOM_MODEL_PATH] = path }
    }

    suspend fun setLive2dScale(scale: Float) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_LIVE2D_SCALE] = scale.toDouble() }
    }

    suspend fun setLive2dOffsetX(x: Float) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_LIVE2D_OFFSET_X] = x.toDouble() }
    }

    suspend fun setLive2dOffsetY(y: Float) {
        cachedPrefs = dataStore.edit { prefs -> prefs[KEY_LIVE2D_OFFSET_Y] = y.toDouble() }
    }

    /** 清除所有设置（恢复出厂）。 */
    suspend fun clearAll() {
        cachedPrefs = dataStore.edit { it.clear() }
        Log.i(TAG, "All settings cleared")
    }
}
