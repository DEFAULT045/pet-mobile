package com.meapet.mobile.memory

import kotlinx.serialization.Serializable

/**
 * 记忆类型枚举。
 *
 * - SHORT_TERM: 短期记忆，当前对话上下文中提取的即时信息。
 * - LONG_TERM:   长期记忆，经摘要压缩后的持久化知识。
 * - CORE_TRAIT:  用户核心特质（如偏好、性格习惯）。
 * - FACTUAL:     事实性信息（如用户姓名、宠物名字）。
 */
enum class MemoryType {
    SHORT_TERM,
    LONG_TERM,
    CORE_TRAIT,
    FACTUAL
}

/**
 * 记忆条目——记忆系统的最小数据单元。
 *
 * @property id 唯一标识
 * @property content 记忆文本内容（自然语言表述）
 * @property type 记忆类型
 * @property importance 重要性评分 0.0~1.0（越高越不易被遗忘）
 * @property createdAt 创建时间戳
 * @property lastAccessedAt 最后访问时间戳（用于 LRU 淘汰）
 * @property accessCount 访问次数
 * @property sourceMessageId 来源消息 ID（关联回对话）
 * @property tags 标签
 */
@Serializable
data class MemoryItem(
    val id: String,
    val content: String,
    val type: MemoryType = MemoryType.SHORT_TERM,
    val importance: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = createdAt,
    val accessCount: Int = 1,
    val sourceMessageId: String? = null,
    val tags: List<String> = emptyList()
) {
    /** 标记访问（更新访问时间与计数）。 */
    fun accessed(): MemoryItem = copy(
        lastAccessedAt = System.currentTimeMillis(),
        accessCount = accessCount + 1
    )

    /** 提升/降低重要性。 */
    fun withImportance(delta: Float): MemoryItem =
        copy(importance = (importance + delta).coerceIn(0f, 1f))
}

/**
 * 记忆系统统计信息。
 */
data class MemoryStats(
    val totalCount: Int = 0,
    val shortTermCount: Int = 0,
    val longTermCount: Int = 0,
    val coreTraitsCount: Int = 0,
    val factualsCount: Int = 0,
    val averageImportance: Float = 0f
)
