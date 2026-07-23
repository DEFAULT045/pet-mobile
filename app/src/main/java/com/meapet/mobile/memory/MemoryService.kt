package com.meapet.mobile.memory

import android.util.Log
import com.meapet.mobile.client.OpenAiCompatibleClient
import com.meapet.mobile.framework.AppConfig
import com.meapet.mobile.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 记忆业务服务。
 *
 * ## 职责
 * - 从对话中提取记忆（调用 AI 总结）；
 * - 合并 / 去重 / 压缩记忆；
 * - 判断记忆重要性。
 *
 * ## 依赖关系
 * - [MemoryRepository] — 数据存储（必需）；
 * - [OpenAiCompatibleClient] — 用于 AI 摘要（可选，无此 client 时降级为纯规则提取）；
 * - [SettingsManager] — 读取记忆开关配置。
 *
 * ## 低耦合
 * - 不依赖 Chat 模块中的任何类型，仅操作 [MemoryItem]；
 * - 调用方（如 ChatService）决定何时触发记忆提取。
 *
 * @param repository 记忆存储器
 * @param summarizationClient 用于 AI 总结的 HTTP 客户端（null = 降级）
 * @param settingsManager 设置管理器
 * @param config 应用配置
 */
class MemoryService(
    private val repository: MemoryRepository,
    private val summarizationClient: OpenAiCompatibleClient?,
    private val settingsManager: SettingsManager,
    private val config: AppConfig = AppConfig.DEFAULT
) {
    companion object {
        private const val TAG = "MemoryService"
    }

    /**
     * 从一条对话中提取短期记忆。
     *
     * @param userMessage 用户消息
     * @param assistantMessage 助手回复
     * @return 提取出的记忆条目（可能为空）
     */
    suspend fun extractFromExchange(
        userMessage: String,
        assistantMessage: String
    ): List<MemoryItem> = withContext(Dispatchers.Default) {
        if (!settingsManager.isMemoryEnabled()) return@withContext emptyList()

        val items = mutableListOf<MemoryItem>()

        // 规则 1：较长的用户消息可能包含重要信息
        if (userMessage.length > 50) {
            items.add(
                MemoryItem(
                    id = UUID.randomUUID().toString(),
                    content = "[对话] 用户提到: ${userMessage.take(200)}",
                    type = MemoryType.SHORT_TERM,
                    importance = calculateImportance(userMessage),
                    tags = extractTags(userMessage)
                )
            )
        }

        // 规则 2：助手的较长回复可能包含重要回应
        if (assistantMessage.length > 100) {
            items.add(
                MemoryItem(
                    id = UUID.randomUUID().toString(),
                    content = "[回应] 助手回复: ${assistantMessage.take(150)}",
                    type = MemoryType.SHORT_TERM,
                    importance = 0.4f,
                    tags = extractTags(assistantMessage)
                )
            )
        }

        Log.d(TAG, "Extracted ${items.size} short-term memories from exchange")
        items
    }

    /**
     * 使用 AI 对近期对话进行摘要，生成长时记忆。
     *
     * @param recentMessages 近期对话列表（用户 + 助手交替）
     * @return 生成的长期记忆条目，若失败则返回 null
     */
    suspend fun summarizeToLongTerm(
        recentMessages: List<Pair<String, String>>
    ): MemoryItem? {
        if (!settingsManager.isAutoSummaryEnabled() || summarizationClient == null) return null
        if (recentMessages.size < 3) return null  // 对话太短不摘要

        return withContext(Dispatchers.IO) {
            try {
                val conversationText = recentMessages.joinToString("\n") { (u, a) ->
                    "用户: $u\n助手: $a"
                }

                val prompt = buildString {
                    appendLine("请对以下对话进行摘要，提取需要长期记住的重要信息。")
                    appendLine("格式要求：用一句话概括最重要的事实或偏好。")
                    appendLine()
                    appendLine("对话：")
                    appendLine(conversationText)
                }

                val requestBody = """{
                    "model": "${config.memorySummaryModel ?: settingsManager.getModel()}",
                    "messages": [
                        {"role": "system", "content": "你是一个记忆提取助手。请从对话中提取需要长期记住的关键信息，输出简洁的一句话摘要。"},
                        {"role": "user", "content": ${jsonEncode(prompt)}}
                    ],
                    "temperature": 0.3,
                    "max_tokens": 200
                }"""

                val response = summarizationClient.chatCompletion(requestBody)

                // 简单解析 response 提取 content（完整 JSON 解析在 ChatService 中进行）
                val summary = extractContentFromResponse(response) ?: return@withContext null
                if (summary.length < 10) return@withContext null

                val item = MemoryItem(
                    id = UUID.randomUUID().toString(),
                    content = summary,
                    type = MemoryType.LONG_TERM,
                    importance = 0.7f,
                    tags = listOf("auto-summary")
                )

                repository.save(item)
                Log.i(TAG, "Long-term memory created: ${summary.take(80)}...")
                item
            } catch (e: Exception) {
                Log.w(TAG, "Failed to summarize conversation", e)
                null
            }
        }
    }

    /**
     * 合并相似 / 重复的记忆条目。
     */
    suspend fun consolidate() {
        val all = repository.getAll()
        if (all.size < 2) return

        val groups = mutableMapOf<String, MutableList<MemoryItem>>()

        // 按标签分组
        for (item in all) {
            val key = item.tags.sorted().joinToString(",")
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }

        for ((_, items) in groups) {
            if (items.size <= 1) continue

            // 内容相似度检测（简单版：前缀 / 包含匹配）
            val merged = mutableListOf<MemoryItem>()
            val used = mutableSetOf<String>()

            for (i in items.indices) {
                if (items[i].id in used) continue
                val similar = mutableListOf(items[i])
                for (j in i + 1 until items.size) {
                    if (items[j].id in used) continue
                    if (isSimilar(items[i].content, items[j].content)) {
                        similar.add(items[j])
                        used.add(items[j].id)
                    }
                }
                if (similar.size > 1) {
                    // 合并：保留最重要的属性
                    val mergedItem = MemoryItem(
                        id = UUID.randomUUID().toString(),
                        content = similar.maxByOrNull { it.content.length }?.content ?: items[i].content,
                        type = MemoryType.LONG_TERM,
                        importance = (similar.map { it.importance }.average()).toFloat().coerceIn(0f, 1f),
                        accessCount = similar.sumOf { it.accessCount },
                        tags = similar.flatMap { it.tags }.distinct(),
                        sourceMessageId = similar.firstOrNull { it.sourceMessageId != null }?.sourceMessageId
                    )
                    // 删除旧条目，保存合并后的
                    similar.forEach { repository.delete(it.id) }
                    repository.save(mergedItem)
                    Log.d(TAG, "Consolidated ${similar.size} memories into one")
                }
                used.add(items[i].id)
            }
        }
    }

    // ── 内部工具 ──────────────────────────────────────

    private fun calculateImportance(text: String): Float {
        // 关键词加分
        val importantKeywords = listOf(
            "名字", "叫", "喜欢", "讨厌", "最爱", "家住", "工作",
            "生日", "年龄", "name", "like", "love", "hate", "live"
        )
        val matchCount = importantKeywords.count { text.contains(it, ignoreCase = true) }
        val base = 0.3f
        val bonus = (matchCount.toFloat() * 0.15f).coerceAtMost(0.6f)
        return (base + bonus).coerceIn(0f, 1f)
    }

    private fun extractTags(text: String): List<String> {
        val tags = mutableListOf<String>()
        val tagKeywords = mapOf(
            "名字" to "name", "叫" to "name",
            "喜欢" to "preference", "讨厌" to "preference",
            "工作" to "work", "家住" to "location",
            "生日" to "birthday", "年龄" to "age",
            "宠物" to "pet", "游戏" to "game",
            "音乐" to "music", "电影" to "movie"
        )
        for ((keyword, tag) in tagKeywords) {
            if (text.contains(keyword, ignoreCase = true) && tag !in tags) {
                tags.add(tag)
            }
        }
        return tags
    }

    private fun isSimilar(a: String, b: String): Boolean {
        val short = if (a.length <= b.length) a else b
        val long = if (a.length > b.length) a else b
        return short.length > 10 && long.contains(short.take(30))
    }

    private fun extractContentFromResponse(json: String): String? {
        // 简易 JSON 解析：查找 "content":"..." 或 "content": "..."
        val regex = Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val match = regex.find(json)
        return match?.groupValues?.getOrNull(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")
    }

    private fun jsonEncode(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
