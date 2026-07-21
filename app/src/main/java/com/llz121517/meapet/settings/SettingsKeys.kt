package com.llz121517.meapet.settings

/**
 * DataStore 键名常量。
 *
 * 集中管理所有 Preference Key，避免各模块硬编码字符串。
 */
object SettingsKeys {
    const val API_KEY = "api_key"
    const val API_URL = "api_url"
    const val MODEL = "model"
    const val TEMPERATURE = "temperature"
    const val MAX_TOKENS = "max_tokens"
    const val ENABLE_MEMORY = "enable_memory"
    const val ENABLE_AUTO_SUMMARY = "enable_auto_summary"
    const val SYSTEM_PROMPT = "system_prompt"
    const val THEME_MODE = "theme_mode"  // "system" | "light" | "dark"
    const val ENABLE_DYNAMIC_COLOR = "enable_dynamic_color"
    const val COLOR_PRESET = "color_preset"  // "default" | "ocean" | "forest" | "sunset" | "rose" | "mono"
    const val FIRST_LAUNCH = "first_launch"

    /** 合理的默认值。 */
    object Defaults {
        const val API_URL = "https://api.openai.com"
        const val MODEL = "gpt-4o-mini"
        const val TEMPERATURE = 0.7
        const val MAX_TOKENS = 4096
        const val ENABLE_MEMORY = true
        const val ENABLE_AUTO_SUMMARY = true
        const val SYSTEM_PROMPT = "你是一个名叫 Mea 的虚拟宠物，性格友好活泼。"
        const val THEME_MODE = "system"
        const val ENABLE_DYNAMIC_COLOR = true
        const val COLOR_PRESET = "default"
    }
}
