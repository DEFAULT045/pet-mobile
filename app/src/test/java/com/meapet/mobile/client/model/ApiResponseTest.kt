package com.meapet.mobile.client.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiResponseTest {

    @Test
    fun modelIdsFromOpenAiDataArray() {
        val body = """
            {
              "object": "list",
              "data": [
                {"id": "gpt-4o", "object": "model"},
                {"id": "gpt-4o-mini", "object": "model"},
                {"id": "gpt-4o", "object": "model"}
              ]
            }
        """.trimIndent()
        assertEquals(listOf("gpt-4o", "gpt-4o-mini"), ApiResponse.modelIds(body))
    }

    @Test
    fun modelIdsFromTopLevelArray() {
        val body = """[{"id":"claude-3"},{"id":"claude-3.5"}]"""
        assertEquals(listOf("claude-3", "claude-3.5"), ApiResponse.modelIds(body))
    }

    @Test
    fun modelIdsIgnoresBlankAndMissing() {
        val body = """{"data":[{"id":""},{"name":"no-id"},{"id":"  ok  "}]}"""
        assertEquals(listOf("ok"), ApiResponse.modelIds(body))
    }

    @Test
    fun modelIdsMalformedReturnsEmpty() {
        assertTrue(ApiResponse.modelIds("not-json").isEmpty())
        assertTrue(ApiResponse.modelIds("""{"data":null}""").isEmpty())
    }

    @Test
    fun chatCompletionContentExtractsMessage() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"喵"}}]}"""
        assertEquals("喵", ApiResponse.chatCompletionContent(body))
    }
}
