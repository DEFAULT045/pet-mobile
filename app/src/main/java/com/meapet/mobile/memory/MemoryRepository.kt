package com.meapet.mobile.memory

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.max

/**
 * 记忆存储器。
 *
 * ## 存储策略
 * - 主存储：内存 `MutableList`（高速读写）；
 * - 持久化：JSON 文件（应用退出不丢失），写入采用临时文件 + rename 保证原子性；
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
            persistLocked()
        }
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
            persistLocked()
        }
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
        mutex.withLock {
            memories.removeAll { it.id == id }
            persistLocked()
        }
    }

    /** 清除所有记忆。 */
    suspend fun clear() {
        mutex.withLock {
            memories.clear()
            persistLocked()
        }
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

        val result = scored
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(maxCount)
            .map { (item, _) ->
                item.accessed().also { updated ->
                    val idx = memories.indexOfFirst { m -> m.id == item.id }
                    if (idx >= 0) memories[idx] = updated
                }
            }
        // 命中记忆的 accessCount/lastAccessedAt 已更新，落盘以免重启后 LRU 权重回退
        if (result.isNotEmpty()) persistLocked()
        result
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
            persistLocked()
        }
        Log.d(TAG, "Memory cache trimmed")
    }

    // ── 持久化 ────────────────────────────────────────

    /**
     * 从磁盘加载。应在初始化时调用（异步，不阻塞主线程）。
     *
     * 加载前产生的新写入按 id 去重保留，磁盘数据排在前面；
     * 文件损坏时备份为 `.corrupt` 并丢弃，不影响后续使用。
     */
    suspend fun loadFromDisk() {
        mutex.withLock {
            if (!file.exists()) {
                Log.d(TAG, "No persisted memories found")
                return
            }
            try {
                val items = MemorySerialization.decode(file.readText())
                val existingIds = memories.map { it.id }.toSet()
                val loaded = items.filter { it.id !in existingIds }
                memories.addAll(0, loaded)
                enforceCapacity()
                Log.i(TAG, "Loaded ${loaded.size} memories from disk")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load memories from disk, backing up corrupted file", e)
                backupCorruptedFileLocked()
            }
        }
    }

    /** 持久化当前列表。必须在持有 [mutex] 时调用，保证写入内容与内存状态一致。 */
    private fun persistLocked() {
        try {
            val json = MemorySerialization.encode(memories.toList())
            file.parentFile?.mkdirs()
            // 先写临时文件再 rename，避免写入中途崩溃导致文件截断
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) {
                    Log.e(TAG, "Failed to replace memories file")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist memories", e)
        }
    }

    /** 将损坏的持久化文件挪到 `.corrupt` 备份，防止每次启动重复解析失败。 */
    private fun backupCorruptedFileLocked() {
        try {
            val backup = File(context.filesDir, "$FILE_NAME.corrupt")
            if (backup.exists()) backup.delete()
            if (!file.renameTo(backup)) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to back up corrupted memories file", e)
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
}
