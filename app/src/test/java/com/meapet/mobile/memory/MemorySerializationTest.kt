package com.meapet.mobile.memory

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MemorySerializationTest {

    @Test
    fun roundTripPreservesTrickyContent() {
        val items = listOf(
            MemoryItem(
                id = "id-1",
                content = "用户说: \"}, {\" 是老解析器的分隔符, 还有 []{} 这种",
                type = MemoryType.FACTUAL,
                importance = 0.9f,
                createdAt = 1234567890L,
                lastAccessedAt = 1234567999L,
                accessCount = 3,
                sourceMessageId = "msg-42",
                keywords = listOf("name", "带\"引号\"的关键词")
            ),
            MemoryItem(
                id = "id-2",
                content = "多行内容\n第二行\r\n\t带制表符\\反斜杠 \\uCAFE 也不能被转义吞掉",
                type = MemoryType.LONG_TERM,
                importance = 0.5f
            ),
            MemoryItem(
                id = "id-3",
                content = "emoji 测试 🐱🎉 猫娘喵~ ㊙️",
                type = MemoryType.SHORT_TERM,
                sourceMessageId = null,
                keywords = emptyList()
            )
        )

        val json = MemorySerialization.encode(items)
        val decoded = MemorySerialization.decode(json)

        assertEquals(items, decoded)
    }

    @Test
    fun roundTripEmptyList() {
        val decoded = MemorySerialization.decode(MemorySerialization.encode(emptyList()))
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun decodeMalformedJsonThrows() {
        assertFailsWith<Exception> {
            MemorySerialization.decode("not a json at all }{")
        }
    }

    @Test
    fun decodeIgnoresUnknownKeys() {
        val json = """[{"id":"x","content":"c","type":"CORE_TRAIT","importance":0.8,
            "createdAt":1,"lastAccessedAt":2,"accessCount":5,"sourceMessageId":null,
            "keywords":["a"],"futureField":"ignored"}]"""
        val decoded = MemorySerialization.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(MemoryType.CORE_TRAIT, decoded[0].type)
        assertEquals("c", decoded[0].content)
    }
}
