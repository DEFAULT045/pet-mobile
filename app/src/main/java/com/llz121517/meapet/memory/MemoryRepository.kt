package com.llz121517.meapet.memory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * 记忆存储器。
 *
 * ## 存储策略
 * - 主存储：内存 `MutableList`（高速读写）；
 * - 持久化：JSON 文件（应用退出不丢失）；
 * - 淘汰策略：超过 [maxItems] 时淘汰 LRU + 低重要性条目。
 *
 * ## 低耦合
 * - 不依赖任何业务模块，仅操作 [MemoryItem] 数据；
 * - 可替换为 Room / SQLite 实现。
 *
 * @param context 用于获取文件存储目录
 * @param maxItems 最大条目数，超限自动淘汰
 */
class MemoryRepository(
    private val context: Context,
    private val maxItems: Int = 500
) {
    private val mutex = Mutex()
    private val memories = mutableListOf<MemoryItem>()

    companion object {
        private const val TAG = "MemoryRepository"
        private const val FILE_NAME = "meapet_memories.json"
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    // ── CRUD ──────────────────────────────────────────

    /** 保存一条记忆（若 id 已存在则覆盖，不存在则新增）。 */
    suspend fun save(item: MemoryItem): MemoryItem {
        mutex.withLock {
            val idx = memories.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                memories[idx] = item
            } else {
                memories.add(item)
            }
            enforceCapacity()
        }
        persist()
        return item
    }

    /** 批量保存。 */
    suspend fun saveAll(items: List<MemoryItem>) {
        mutex.withLock {
            items.forEach { item ->
                val idx = memories.indexOfFirst { it.id == item.id }
                if (idx >= 0) memories[idx] = item
                else memories.add(item)
            }
            enforceCapacity()
        }
        persist()
    }

    /** 根据 ID 查询。 */
    suspend fun findById(id: String): MemoryItem? = mutex.withLock {
        memories.find { it.id == id }?.let { it.also { _ -> /* access tracking handled by caller */ } }
    }

    /** 获取所有记忆（按重要性降序）。 */
    suspend fun getAll(): List<MemoryItem> = mutex.withLock {
        memories.sortedByDescending { it.importance }.toList()
    }

    /** 按类型过滤。 */
    suspend fun getByType(type: MemoryType): List<MemoryItem> = mutex.withLock {
        memories.filter { it.type == type }
            .sortedByDescending { it.importance }
    }

    /** 删除指定记忆。 */
    suspend fun delete(id: String) {
        mutex.withLock { memories.removeAll { it.id == id } }
        persist()
    }

    /** 清除所有记忆。 */
    suspend fun clear() {
        mutex.withLock { memories.clear() }
        persist()
        Log.i(TAG, "All memories cleared")
    }

    // ── 查询 ──────────────────────────────────────────

    /**
     * 语义搜索记忆（简单关键词匹配，未来可替换为向量搜索）。
     */
    suspend fun search(query: String): List<MemoryItem> = mutex.withLock {
        val q = query.lowercase()
        memories.filter {
            it.content.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
        }.sortedByDescending { it.importance }
    }

    /**
     * 获取与当前上下文最相关的几条记忆。
     *
     * @param contextText 当前对话上下文
     * @param maxCount 最大返回条数
     */
    suspend fun getRelevant(contextText: String, maxCount: Int = 5): List<MemoryItem> = mutex.withLock {
        val q = contextText.lowercase()
        val keywords = q.split(Regex("[\\s,，。！？、；：]+"))
            .filter { it.length > 1 }
            .toSet()

        if (keywords.isEmpty()) return@withLock emptyList()

        val scored = memories.map { item ->
            val matchCount = keywords.count { kw ->
                item.content.lowercase().contains(kw) ||
                    item.tags.any { tag -> tag.lowercase().contains(kw) }
            }
            val score = matchCount.toFloat() / max(keywords.size, 1) * item.importance
            item to score
        }

        scored
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(maxCount)
            .map { (item, _) ->
                item.accessed().also { updated ->
                    val idx = memories.indexOfFirst { m -> m.id == item.id }
                    if (idx >= 0) memories[idx] = updated
                }
            }
    }

    /** 获取统计数据。 */
    suspend fun getStats(): MemoryStats = mutex.withLock {
        if (memories.isEmpty()) return MemoryStats()
        MemoryStats(
            totalCount = memories.size,
            shortTermCount = memories.count { it.type == MemoryType.SHORT_TERM },
            longTermCount = memories.count { it.type == MemoryType.LONG_TERM },
            coreTraitsCount = memories.count { it.type == MemoryType.CORE_TRAIT },
            factualsCount = memories.count { it.type == MemoryType.FACTUAL },
            averageImportance = memories.map { it.importance }.let { scores ->
                if (scores.isEmpty()) 0f else scores.sum() / scores.size
            }
        )
    }

    /** 清除低价值缓存（内存压力时调用）。 */
    suspend fun trimCache() {
        mutex.withLock {
            // 只保留下限 100 条 + 高重要性条目
            val keepCount = max(100, maxItems / 3)
            if (memories.size <= keepCount) return
            val sorted = memories.sortedByDescending { it.importance * (it.accessCount.toFloat()) }
            memories.clear()
            memories.addAll(sorted.take(keepCount))
        }
        persist()
        Log.d(TAG, "Memory cache trimmed")
    }

    // ── 持久化 ────────────────────────────────────────

    /** 从磁盘加载。应在初始化时调用。 */
    suspend fun loadFromDisk() {
        mutex.withLock {
            if (!file.exists()) {
                Log.d(TAG, "No persisted memories found")
                return
            }
            try {
                val json = file.readText()
                val items = parseJson(json)
                memories.clear()
                memories.addAll(items)
                Log.i(TAG, "Loaded ${items.size} memories from disk")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load memories from disk", e)
            }
        }
    }

    private fun persist() {
        try {
            val json = toJson()
            file.parentFile?.mkdirs()
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist memories", e)
        }
    }

    // ── 容量控制 ──────────────────────────────────────

    private fun enforceCapacity() {
        if (memories.size <= maxItems) return
        // 淘汰策略：重要性 * 访问次数 最低的条目
        val sorted = memories.sortedByDescending { it.importance * (it.accessCount.toFloat()) }
        memories.clear()
        memories.addAll(sorted.take(maxItems))
        Log.d(TAG, "Memory capacity enforced: ${memories.size}/$maxItems")
    }

    // ── JSON 序列化（轻量，不引入 kotlinx.serialization 依赖） ──

    private fun toJson(): String {
        val sb = StringBuilder()
        sb.appendLine("[")
        memories.forEachIndexed { i, item ->
            sb.appendLine("  {")
            sb.appendLine("    \"id\": \"${escape(item.id)}\",")
            sb.appendLine("    \"content\": \"${escape(item.content)}\",")
            sb.appendLine("    \"type\": \"${item.type.name}\",")
            sb.appendLine("    \"importance\": ${item.importance},")
            sb.appendLine("    \"createdAt\": ${item.createdAt},")
            sb.appendLine("    \"lastAccessedAt\": ${item.lastAccessedAt},")
            sb.appendLine("    \"accessCount\": ${item.accessCount},")
            sb.appendLine("    \"sourceMessageId\": ${item.sourceMessageId?.let { "\"${escape(it)}\"" } ?: null},")
            sb.appendLine("    \"tags\": [${item.tags.joinToString(", ") { "\"${escape(it)}\"" }}]")
            if (i < memories.size - 1) sb.appendLine("  },") else sb.appendLine("  }")
        }
        sb.appendLine("]")
        return sb.toString()
    }

    private fun parseJson(json: String): List<MemoryItem> {
        val items = mutableListOf<MemoryItem>()
        // 简易 JSON 解析（无第三方依赖）
        val itemBlocks = json.split(Regex("\\}\\s*,\\s*\\{"))
        for (block in itemBlocks) {
            try {
                val clean = block.replace(Regex("[\\[\\]{}]"), "").trim()
                if (clean.isBlank()) continue
                val map = mutableMapOf<String, String>()
                val regex = Regex("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|[0-9.]+|true|false|null|\\[.*?\\])")
                regex.findAll(clean).forEach { match ->
                    val key = match.groupValues[1]
                    var value = match.groupValues[2]
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length - 1)
                    }
                    map[key] = value
                }

                if (map.containsKey("id") && map.containsKey("content")) {
                    val tagsStr = map["tags"] ?: "[]"
                    val tags = if (tagsStr.startsWith("[")) {
                        Regex("\"([^\"]+)\"").findAll(tagsStr).map { it.groupValues[1] }.toList()
                    } else emptyList()

                    items.add(
                        MemoryItem(
                            id = map["id"] ?: UUID.randomUUID().toString(),
                            content = unescape(map["content"] ?: ""),
                            type = try { MemoryType.valueOf(map["type"] ?: "SHORT_TERM") } catch (_: Exception) { MemoryType.SHORT_TERM },
                            importance = (map["importance"]?.toFloatOrNull() ?: 0.5f).coerceIn(0f, 1f),
                            createdAt = map["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                            lastAccessedAt = map["lastAccessedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                            accessCount = map["accessCount"]?.toIntOrNull() ?: 1,
                            sourceMessageId = map["sourceMessageId"]?.takeIf { it != "null" },
                            tags = tags
                        )
                    )
                }
            } catch (_: Exception) { /* skip malformed entry */ }
        }
        return items
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun unescape(s: String): String = s
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
