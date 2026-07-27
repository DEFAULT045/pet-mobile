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

    /**
     * @property visibleReply 剥离协议块后的可见回复
     * @property ops 解析出的记忆操作
     * @property rawBlock 协议块原文（含围栏），供组装下一轮请求时贴回历史；
     *   未找到块、块未闭合或回退为整段原文时为 null
     */
    data class ParseResult(
        val visibleReply: String,
        val ops: List<MemoryOp>,
        val rawBlock: String? = null
    )

    /**
     * 协议说明文本，拼入 system prompt（仅记忆开关开启时调用）。
     *
     * 措辞对模型的记录意愿与关键词质量影响极大，这里是踩过坑才写成现在这样的：
     * 1. 早期「闲聊寒暄通常不需要记」+ 举例全是姓名生日 → SHORT_TERM 几乎不产出。
     *    反过来「只要提到具体的人/事/物就至少记 1 条」又会把废话也记满。
     *    现在用「以后还能用到」作软门槛：拿不准时仍可记 SHORT_TERM，但不要见字就记。
     * 2. 用户的人设 prompt 往往带「日常 10-25 字 / 极简」这类字数与风格约束（默认人设就有），
     *    模型会把它一并套用到这个代码块上，于是能省则省。必须显式豁免。
     * 3. 说明写在 system prompt 靠前的位置，离生成点远。[reminder] 会在记忆上下文的末尾
     *    再顶一句，紧邻对话历史，命中率明显更高。
     * 4. keywords 若写成「从 content 里摘几个字」（如「用户喜欢炸鸡」→ 用户、炸鸡），
     *    检索几乎捞不到相关话题；要写成用户以后聊到这件事时会说出口的查找词。
     * 5. 模型会把**自己台词里的邀请/提议**写成用户已发生的事实
     *    （你说「一起去食堂吗」→ 记成「用户今天和你去了食堂」）。
     *    必须强调：只记用户自述或已确认的内容，禁止把助手提议当事实。
     */
    fun instructions(): String = """
        【记忆协议】
        你必须在每轮回复的最后附加一个 ```$FENCE_LANG 块，声明要创建/更新/删除哪些记忆。
        它不展示给用户，正文里绝不要提及；它也**不属于你的台词**——人设的字数上限与语气要求
        一概不适用，不要为了回复简短而省略它。

        ```$FENCE_LANG
        [{"op":"create","type":"SHORT_TERM","content":"主人这周在赶毕业论文，压力很大","importance":0.5,"keywords":["毕业论文","赶论文","论文","学业压力"]}]
        ```

        - op: create（默认）/ update / delete，后两者需 targetId，引用下文【用户人设】【相关回忆】里的 id
        - type: SHORT_TERM（最近经历、状态、在忙的事、提到的人/地点）
          / CORE_TRAIT（稳定的性格与喜好厌恶）/ FACTUAL（姓名、生日、职业等客观事实）
          合理选择 type
        - content 写清主语（「主人说明天要去面试」而非「要去面试」）；importance 取 0~1
        - keywords 是**查找词**（3~8 个）：用户以后聊到相关话题时会说出口、用来把这条记忆捞回来的词。
          要覆盖具体事物 + 相关话题/类别（如「主人喜欢炸鸡」→ 炸鸡、喜欢、喜好、食物），
          别只从 content 机械摘词，也别塞「主人」「用户」「记忆」「对话」这类对检索无用的词

        只能记用户侧、且明确说过/确认过的信息；禁止记猜测与可能，也禁止记你自己的回答/邀请/提议
        （你说「一起去食堂吗」≠ 用户去了食堂；用户没答应前不要记成计划）。
        优先记以后还能用到的：稳定偏好、身份事实、未完结的事、用户自己提出的计划、重要情绪与关系。
        一说完就没用的闲聊、重复已知内容、纯寒暄，通常不必记，
        也不要整轮空着；[] 只适合纯问候或用户整轮都在问你、且没有自述信息时。
        发现记错或过时的信息，用 update / delete 修正，不要重复创建。
    """.trimIndent()

    /**
     * 收尾提醒，拼在**整个请求的最末尾**（对话历史之后）。
     *
     * [instructions] 在首条 system 消息里，离生成点隔着几十条历史，模型写到末尾时
     * 注意力已经散了。这里用一句话把要求顶回视野内。
     */
    fun reminder(): String =
        "（提醒：回复正文写完后务必附加 ```$FENCE_LANG 块；" +
            "只记用户说过/确认过的，勿把你的提议当事实；" +
            "有可复用信息就记，纯寒暄可 []；keywords 写查找词。）"

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
        // 原文照搬（含围栏）：下一轮把它贴回历史，模型才有自己写过的正例可循
        return ParseResult(visible, ops, rawReply.substring(open.range.first, blockEnd))
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
