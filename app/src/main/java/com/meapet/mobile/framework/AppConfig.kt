package com.meapet.mobile.framework

/**
 * 全局应用配置。
 *
 * 所有子系统依赖此配置决定行为，不各自定义硬编码默认值。
 * 修改此文件即可变更应用全局行为，无需逐个翻查各模块。
 *
 * @property defaultApiUrl 默认 API 基础 URL
 * @property defaultModel 默认模型名
 * @property maxHistoryMessages 单次请求送的最大历史消息数（滑动窗口）
 * @property memorySummaryModel 用于记忆总结的模型名（null = 使用 defaultModel）
 * @property maxMemoryItems 记忆库最多保留条目数
 * @property maxContextMemories 注入上下文时最多取几条记忆
 * @property enableMemory 是否启用记忆系统
 * @property enableAutoSummary 是否自动摘要对话为长期记忆
 * @property appVersion 应用版本名
 */
data class AppConfig(
    val defaultApiUrl: String = "https://api.openai.com",
    val defaultModel: String = "gpt-4o-mini",
    val maxHistoryMessages: Int = 40,
    val memorySummaryModel: String? = null,
    val maxMemoryItems: Int = 500,
    val maxContextMemories: Int = 5,
    val enableMemory: Boolean = true,
    val enableAutoSummary: Boolean = true,
    val appVersion: String = "1.0.0"
) {
    companion object {
        /** 合理的生产默认值。各模块可通过 [AppContainer.config] 访问。 */
        val DEFAULT = AppConfig()
    }
}
