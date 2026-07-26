package com.meapet.mobile.memory

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 模型 ↔ 应用的"记忆协议"。
 *
 * 大模型每轮回复末尾可附带一个 fenced JSON 块（语言标记 [FENCE_LANG]），
 * 声明本轮要创建/更新/删除哪些记忆。该块对用户不可见，解析后从可见回复中剥离。
 *
 * ## 容错原则
 * 记忆创建是锦上添花，绝不能因为解析失败影响正常聊天：
 * - 块缺失/格式错误 → 静默跳过，`ops` 为空，可见回复不受影响；
 * - 单条 op 字段不全（如 update 缺 targetId）→ 只丢弃这一条，其余正常处理；
 * - 剥离块后可见文本为空白 → 保守回退：整段原文当可见回复（不确定是不是真的记忆块时优先保聊天内容，不瞎猜）。
 */
object MemoryOpsProtocol {

    private const val TAG = "MemoryOpsProtocol"

    /** fenced 块的语言标记：` ```memory-ops ` */
    const val FENCE_LANG = "memory-ops"

    private val json = Json { ignoreUnknownKeys = true }

    /** 围栏符号。 */
    private const val FENCE = "```"

    /**
     * 起始围栏 ```memory-ops（允许围栏与语言标记之间有空格）。
     *
     * 注意：**必须锚定「最后一个起始围栏」再往后找收尾**，不能用
     * `Regex("```memory-ops...```").findAll().last()` 去匹配整块——若正文里先提到了
     * ```memory-ops（模型常这么干），那种写法的首个匹配会一路懒惰匹配到真正代码块的
     * 开头围栏就收尾，捕获组里装的是散文，且剩余文本不再含完整起始围栏导致没有第二个
     * 匹配，最终必然解析失败。
     */
    private val openFenceRegex = Regex("$FENCE[ \\t]*$FENCE_LANG", RegexOption.IGNORE_CASE)

    sealed interface MemoryOp {
        data class Create(
            val type: MemoryType,
            val content: String,
            val importance: Float,
            val keywords: List<String>
        ) : MemoryOp

        data class Update(
            val targetId: String,
            val type: MemoryType,
            val content: String,
            val importance: Float,
            val keywords: List<String>
        ) : MemoryOp

        data class Delete(val targetId: String) : MemoryOp
    }

    data class ParseResult(val visibleReply: String, val ops: List<MemoryOp>)

    /**
     * 协议说明文本，拼入 system prompt（仅记忆开关开启时调用）。
     */
    fun instructions(): String = """
        【记忆协议】
        你可以在本轮回复的最后附加一个 ```$FENCE_LANG 代码块，声明要创建/更新/删除哪些记忆。
        这个代码块不会展示给用户，只供系统解析，正文里绝不要提及它的存在。
        无论本轮有没有值得记住的内容，都必须输出这个块；没有就输出空数组 []。

        格式（JSON 数组，每个元素一条操作）：
        ```$FENCE_LANG
        [{"op":"create","type":"FACTUAL","content":"用户叫小明","importance":0.9,"keywords":["小明","名字"]}]
        ```

        字段说明：
        - op: "create"（新建，缺省默认）/ "update"（更新已有记忆，需 targetId）/ "delete"（删除已有记忆，需 targetId）
        - type: SHORT_TERM（短期，闲聊细节）/ CORE_TRAIT（性格偏好，长期有效）/ FACTUAL（事实，如姓名生日，长期有效）
        - content: 一句话概括
        - importance: 0~1 的重要性评分
        - keywords: 3~8 个用于以后检索这条记忆的关键词
        - targetId: 仅 update/delete 需要，引用下文【用户人设】/【相关回忆】里给出的 id

        什么值得记：姓名、生日、住址、职业、喜好厌恶、约定好的事情等对用户重要或以后会用到的信息。
        闲聊寒暄、临时性的一句话通常不需要记。发现之前记错或过时的信息时，用 update 或 delete 修正，不要重复创建。
    """.trimIndent()

    /**
     * 从模型原始回复中剥离记忆协议块，返回干净的可见回复与解析出的操作列表。
     */
    fun extract(rawReply: String): ParseResult {
        // 取最后一个起始围栏：正文里若先提到过 ```memory-ops，真正的块总在后面
        val open = openFenceRegex.findAll(rawReply).lastOrNull()
        if (open == null) {
            Log.i(TAG, "No memory-ops block in reply (${rawReply.length} chars) — model did not emit one")
            return ParseResult(rawReply, emptyList())
        }

        val bodyStart = open.range.last + 1
        val closeIdx = rawReply.indexOf(FENCE, startIndex = bodyStart)
        // 未闭合时把起始围栏之后的内容全部视作块（切掉，不展示碎 JSON）
        val blockEnd = if (closeIdx >= 0) closeIdx + FENCE.length else rawReply.length

        val visible = (
            rawReply.substring(0, open.range.first) + rawReply.substring(blockEnd)
            ).trim()
        if (visible.isBlank()) {
            // 剥离后没有可见内容了，保守起见把原文整体当回复，不确定这是不是真的记忆块
            return ParseResult(rawReply, emptyList())
        }

        if (closeIdx < 0) {
            Log.w(TAG, "Unclosed memory-ops fence, dropping it (no ops applied this turn)")
            return ParseResult(visible, emptyList())
        }

        val ops = parseOpsLenient(rawReply.substring(bodyStart, closeIdx))
        Log.i(TAG, "Parsed memory-ops block: ${ops.size} op(s)")
        return ParseResult(visible, ops)
    }

    /**
     * 解析块内容，并对**二次转义**做一次兜底重试。
     *
     * 部分中转 / 调试工具会把 JSON 转义两遍，解码后块内残留反斜杠
     * （`[{\"op\":\"create\"...}]`），直接解析必然失败。
     */
    private fun parseOpsLenient(body: String): List<MemoryOp> {
        try {
            return parseOps(body)
        } catch (e: Exception) {
            // 只记异常信息与块长度：对话内容一律不进 Logcat
            Log.w(TAG, "Failed to parse memory-ops block (${body.length} chars): ${e.message}")
        }
        val unescaped = body.replace("\\\"", "\"")
        if (unescaped == body) return emptyList()
        return try {
            parseOps(unescaped).also {
                Log.i(TAG, "Recovered ${it.size} op(s) after unescaping doubly-escaped JSON")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Retry after unescaping also failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseOps(arrayText: String): List<MemoryOp> {
        val root = json.parseToJsonElement(arrayText.trim())
        // 模型有时会在只有一条操作时忘记套数组，直接输出单个对象——容忍这种情况
        val array: JsonArray = when {
            root is JsonArray -> root
            root is JsonObject -> JsonArray(listOf(root))
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            try {
                parseOp(element.jsonObject)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed memory op (${element.toString().length} chars): ${e.message}")
                null
            }
        }
    }

    private fun parseOp(obj: JsonObject): MemoryOp? {
        val op = obj["op"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "create"
        return when (op) {
            "delete" -> {
                val targetId = requiredString(obj, "targetId") ?: return null
                MemoryOp.Delete(targetId)
            }
            "update" -> {
                val targetId = requiredString(obj, "targetId") ?: return null
                val content = requiredString(obj, "content") ?: return null
                MemoryOp.Update(
                    targetId = targetId,
                    type = parseType(obj),
                    content = content,
                    importance = parseImportance(obj),
                    keywords = parseKeywords(obj)
                )
            }
            else -> {
                val content = requiredString(obj, "content") ?: return null
                MemoryOp.Create(
                    type = parseType(obj),
                    content = content,
                    importance = parseImportance(obj),
                    keywords = parseKeywords(obj)
                )
            }
        }
    }

    private fun requiredString(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseType(obj: JsonObject): MemoryType {
        val raw = obj["type"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
            ?: return MemoryType.SHORT_TERM
        return try {
            MemoryType.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            MemoryType.SHORT_TERM
        }
    }

    private fun parseImportance(obj: JsonObject): Float {
        val value = obj["importance"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.5f
        return value.coerceIn(0f, 1f)
    }

    private fun parseKeywords(obj: JsonObject): List<String> {
        val arr = obj["keywords"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { kw -> kw.isNotEmpty() } }
            .distinct()
            .take(8)
    }
}
