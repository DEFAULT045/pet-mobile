package com.meapet.mobile.memory

import kotlinx.serialization.json.Json

/**
 * 记忆条目的 JSON 编解码器。
 *
 * 基于 kotlinx.serialization，保证任意 content（含引号、花括号、换行、emoji 等）
 * 均可无损 round-trip。独立于 Android 组件，可直接在 JVM 单元测试中验证。
 */
internal object MemorySerialization {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 序列化为 JSON 文本。 */
    fun encode(items: List<MemoryItem>): String = json.encodeToString(items)

    /**
     * 从 JSON 文本反序列化。
     *
     * @throws Exception 文本不是合法的记忆列表 JSON 时抛出，由调用方决定如何处置
     */
    fun decode(text: String): List<MemoryItem> = json.decodeFromString(text)
}
