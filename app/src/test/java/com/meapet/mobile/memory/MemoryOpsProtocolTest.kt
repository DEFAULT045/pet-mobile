package com.meapet.mobile.memory

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryOpsProtocolTest {

    // ── 正常解析 ──────────────────────────────────────

    @Test
    fun extractsAndStripsWellFormedBlock() {
        val raw = """
            你好呀主人~

            ```memory-ops
            [{"op":"create","type":"FACTUAL","content":"用户叫小明","importance":0.9,"keywords":["小明","名字"]}]
            ```
        """.trimIndent()

        val result = MemoryOpsProtocol.extract(raw)

        assertEquals("你好呀主人~", result.visibleReply)
        assertEquals(1, result.ops.size)
        val op = result.ops.single() as MemoryOpsProtocol.MemoryOp.Create
        assertEquals(MemoryType.FACTUAL, op.type)
        assertEquals("用户叫小明", op.content)
        assertEquals(0.9f, op.importance)
        assertEquals(listOf("小明", "名字"), op.keywords)
    }

    @Test
    fun extractsBlockWithNoNewlinesAtAll() {
        // 实测复现：回复正文与围栏、JSON 全挤在一行，中间没有任何换行
        val raw = "喵```memory-ops" +
            """[{"op":"create","type":"FACTUAL","content":"用户喜欢猫娘","importance":0.9,"keywords":["猫娘","喜欢"]}]""" +
            "```"

        val result = MemoryOpsProtocol.extract(raw)

        assertEquals("喵", result.visibleReply)
        assertEquals(1, result.ops.size, "紧贴一行的格式也必须能解析出操作")
        val op = result.ops.single() as MemoryOpsProtocol.MemoryOp.Create
        assertEquals("用户喜欢猫娘", op.content)
        assertEquals(MemoryType.FACTUAL, op.type)
    }

    @Test
    fun inlineMentionOfFenceBeforeRealBlockDoesNotBreakParsing() {
        // 回归：模型在正文里先提到了 ```memory-ops，真正的块在后面。
        // 旧实现用 findAll(整块正则).last()，首个匹配会一路懒惰匹配到真块的开头围栏，
        // 捕获组里装的是散文 → JSON 解析失败 → 0 op。
        val raw = """
            我会把这件事记到 ```memory-ops 里喵~

            ```memory-ops
            [{"op":"create","type":"FACTUAL","content":"用户喜欢猫娘","importance":0.9,"keywords":["猫娘","喜欢"]}]
            ```
        """.trimIndent()

        val result = MemoryOpsProtocol.extract(raw)

        assertEquals(1, result.ops.size, "正文提及围栏不应导致解析失败")
        assertEquals("用户喜欢猫娘", (result.ops.single() as MemoryOpsProtocol.MemoryOp.Create).content)
        assertFalse(result.visibleReply.contains("\"op\""), "JSON 不应残留在可见回复里")
    }

    @Test
    fun doublyEscapedJsonIsRecovered() {
        // 部分中转/调试工具会把 JSON 转义两遍，解码后块内残留反斜杠
        val raw = "喵```memory-ops" +
            """[{\"op\":\"create\",\"type\":\"FACTUAL\",\"content\":\"用户喜欢猫娘\",\"importance\":0.9,\"keywords\":[\"猫娘\"]}]""" +
            "```"

        val result = MemoryOpsProtocol.extract(raw)

        assertEquals(1, result.ops.size, "二次转义的 JSON 应能兜底恢复")
        assertEquals("用户喜欢猫娘", (result.ops.single() as MemoryOpsProtocol.MemoryOp.Create).content)
    }

    @Test
    fun blockBeforeProseIsStrippedFromBothSides() {
        val raw = "```memory-ops\n[]\n```\n\n后面才是正文喵"
        val result = MemoryOpsProtocol.extract(raw)
        assertEquals("后面才是正文喵", result.visibleReply)
        assertTrue(result.ops.isEmpty())
    }

    @Test
    fun opFieldDefaultsToCreate() {
        val raw = """
            喵~

            ```memory-ops
            [{"type":"SHORT_TERM","content":"今天吃了火锅","importance":0.4,"keywords":["火锅"]}]
            ```
        """.trimIndent()

        val ops = MemoryOpsProtocol.extract(raw).ops
        assertTrue(ops.single() is MemoryOpsProtocol.MemoryOp.Create)
    }

    @Test
    fun parsesUpdateAndDelete() {
        val raw = """
            好的喵

            ```memory-ops
            [
              {"op":"update","targetId":"mem_a1b2","type":"FACTUAL","content":"用户叫大明","importance":0.9,"keywords":["大明"]},
              {"op":"delete","targetId":"mem_old1"}
            ]
            ```
        """.trimIndent()

        val ops = MemoryOpsProtocol.extract(raw).ops
        assertEquals(2, ops.size)
        val update = ops[0] as MemoryOpsProtocol.MemoryOp.Update
        assertEquals("mem_a1b2", update.targetId)
        assertEquals("用户叫大明", update.content)
        val delete = ops[1] as MemoryOpsProtocol.MemoryOp.Delete
        assertEquals("mem_old1", delete.targetId)
    }

    @Test
    fun emptyArrayYieldsNoOps() {
        val raw = "闲聊回复\n\n```memory-ops\n[]\n```"
        val result = MemoryOpsProtocol.extract(raw)
        assertEquals("闲聊回复", result.visibleReply)
        assertTrue(result.ops.isEmpty())
    }

    @Test
    fun singleObjectWithoutArrayWrapperIsTolerated() {
        // 模型有时会在只有一条操作时忘记套数组
        val raw = "喵\n\n```memory-ops\n" +
            """{"op":"create","type":"SHORT_TERM","content":"内容","importance":0.5,"keywords":["a"]}""" +
            "\n```"
        val ops = MemoryOpsProtocol.extract(raw).ops
        assertEquals(1, ops.size)
    }

    @Test
    fun keywordsAreTruncatedToEight() {
        val kws = (1..12).map { "kw$it" }
        val raw = "喵\n\n```memory-ops\n" +
            """[{"op":"create","type":"SHORT_TERM","content":"c","importance":0.5,"keywords":${
                kws.joinToString(",", "[", "]") { "\"$it\"" }
            }}]""" +
            "\n```"
        val op = MemoryOpsProtocol.extract(raw).ops.single() as MemoryOpsProtocol.MemoryOp.Create
        assertEquals(8, op.keywords.size)
    }

    // ── 容错：不能因为解析失败破坏聊天回复 ─────────────

    @Test
    fun missingBlockReturnsWholeTextWithNoOps() {
        val raw = "这是一段普通回复，完全没有记忆块。"
        val result = MemoryOpsProtocol.extract(raw)
        assertEquals(raw, result.visibleReply)
        assertTrue(result.ops.isEmpty())
    }

    @Test
    fun malformedJsonInsideClosedBlockStillStripsBlockButYieldsNoOps() {
        val raw = "正常回复内容\n\n```memory-ops\n这不是合法JSON{{{\n```"
        val result = MemoryOpsProtocol.extract(raw)
        assertEquals("正常回复内容", result.visibleReply)
        assertTrue(result.ops.isEmpty())
        assertFalse(result.visibleReply.contains("memory-ops"))
    }

    @Test
    fun invalidOpItemIsSkippedButOthersSurvive() {
        val raw = "喵\n\n```memory-ops\n" +
            """[
                {"op":"update","content":"缺 targetId，应被丢弃"},
                {"op":"create","type":"SHORT_TERM","content":"有效","importance":0.5,"keywords":["a"]}
            ]""" +
            "\n```"
        val ops = MemoryOpsProtocol.extract(raw).ops
        assertEquals(1, ops.size)
        assertEquals("有效", (ops.single() as MemoryOpsProtocol.MemoryOp.Create).content)
    }

    @Test
    fun blankVisibleTextAfterStripFallsBackToRawText() {
        // 整段回复就是记忆块本身，剥离后没有可见内容——保守起见展示原文，不解析
        val raw = "```memory-ops\n[{\"op\":\"create\",\"type\":\"SHORT_TERM\",\"content\":\"c\"}]\n```"
        val result = MemoryOpsProtocol.extract(raw)
        assertEquals(raw, result.visibleReply)
        assertTrue(result.ops.isEmpty())
    }

    @Test
    fun unclosedFenceIsCutFromVisibleReply() {
        val raw = "正常聊天内容\n\n```memory-ops\n[{\"op\":\"create\""
        val result = MemoryOpsProtocol.extract(raw)
        assertEquals("正常聊天内容", result.visibleReply)
        assertTrue(result.ops.isEmpty())
    }

    // ── rawBlock（供下一轮贴回历史当格式范例）────────────

    @Test
    fun rawBlockKeepsFencedBlockVerbatim() {
        val block = "```memory-ops\n[{\"op\":\"create\",\"type\":\"SHORT_TERM\",\"content\":\"c\",\"keywords\":[\"k\"]}]\n```"
        val result = MemoryOpsProtocol.extract("正常聊天内容\n\n$block")

        assertEquals(block, result.rawBlock)
        assertEquals("正常聊天内容", result.visibleReply)
    }

    @Test
    fun rawBlockIsKeptEvenWhenOpsAreEmpty() {
        // 空数组也要贴回去：模型漏的往往不是内容而是「块本身」，格式范例比内容更重要
        val block = "```memory-ops\n[]\n```"
        assertEquals(block, MemoryOpsProtocol.extract("在的喵\n\n$block").rawBlock)
    }

    @Test
    fun rawBlockIsNullWhenNoUsableBlock() {
        assertNull(MemoryOpsProtocol.extract("就是一句普通回复").rawBlock)
        assertNull(
            MemoryOpsProtocol.extract("正常聊天内容\n\n```memory-ops\n[{\"op\":\"create\"").rawBlock,
            "未闭合的块不该被当范例贴回去"
        )
    }
}
