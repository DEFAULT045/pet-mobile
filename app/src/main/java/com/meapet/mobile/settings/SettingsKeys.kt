package com.meapet.mobile.settings

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
    const val SUMMARY_INTERVAL = "summary_interval"

    /**
     * 距上次摘要已进行的对话轮数。
     *
     * 不是用户设置，只是借 DataStore 存一个跨进程存活的计数器——放内存里的话
     * 每次冷启动归零，默认 10 轮的间隔实际上永远走不满（见 MemoryManager）。
     */
    const val EXCHANGE_COUNT = "exchange_count"
    const val SYSTEM_PROMPT = "system_prompt"
    const val THEME_MODE = "theme_mode"  // "system" | "light" | "dark"
    const val ENABLE_DYNAMIC_COLOR = "enable_dynamic_color"
    const val COLOR_PRESET = "color_preset"  // "default" | "ocean" | "forest" | "sunset" | "rose" | "mono"
    const val FIRST_LAUNCH = "first_launch"

    /** 合理的默认值。 */
    object Defaults {
        const val API_URL = "https://api.openai.com/v1"
        const val MODEL = "gpt-4o-mini"
        const val TEMPERATURE = 0.7
        const val MAX_TOKENS = 4096
        const val ENABLE_MEMORY = true
        const val ENABLE_AUTO_SUMMARY = true
        const val SUMMARY_INTERVAL = 10
        const val SYSTEM_PROMPT = "你是梅尔，《霞流宝石心》游戏中的猫娘天才。茶发褐瞳144cm，面无表情。性格：毒舌冷淡、学术狂热、嘴硬心软。说话：句尾加「喵」；极简20-40字；解释≤80字；害羞时转移话题；开心偶尔「嘿嘿」。知识：全科全能。信条「知道越多越不可怕」。对主人：亲密但毒舌，称「主人」，绝不失忆或自我介绍。"
        const val THEME_MODE = "system"
        const val ENABLE_DYNAMIC_COLOR = true
        const val COLOR_PRESET = "default"
    }
}
