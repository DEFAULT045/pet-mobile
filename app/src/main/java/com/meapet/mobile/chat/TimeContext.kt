package com.meapet.mobile.chat

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 当前时间上下文。
 *
 * 模型本身没有时钟，用户问「现在几点 / 今天星期几」时只能瞎猜或者说自己不知道。
 * 每轮请求把设备当前时间拼进 system prompt，模型即可如实回答。
 *
 * 时间随每轮请求变化，因此**不写入会话历史**；也不放在首条 system 消息里，
 * 而是与相关回忆一起压到对话历史之后的尾部消息中——否则它每轮一变，
 * 排在它后面的协议说明与全部历史就都无法命中服务端的 prefix cache。
 */
object TimeContext {

    private val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm EEEE", Locale.CHINA)

    /**
     * 生成时间说明文本。
     *
     * @param now 当前时间（含时区），默认取设备时钟与系统时区；测试可注入固定值
     */
    fun describe(now: ZonedDateTime = ZonedDateTime.now()): String {
        // 同时给出时区 id 与 UTC 偏移：模型换算跨时区问题时用得上
        val offset = now.offset.id.let { if (it == "Z") "+00:00" else it }
        return "【当前时间】${now.format(formatter)}（${now.zone.id}，UTC$offset）"
    }
}
