package com.meapet.mobile.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeContextTest {

    private val fixed = ZonedDateTime.of(2026, 7, 26, 15, 4, 32, 0, ZoneId.of("Asia/Shanghai"))

    @Test
    fun `包含日期、时刻与星期`() {
        val text = TimeContext.describe(fixed)
        assertTrue(text, text.contains("2026-07-26 15:04"))
        assertTrue(text, text.contains("星期日"))
    }

    @Test
    fun `包含时区 id 与 UTC 偏移`() {
        val text = TimeContext.describe(fixed)
        assertTrue(text, text.contains("Asia/Shanghai"))
        assertTrue(text, text.contains("UTC+08:00"))
    }

    @Test
    fun `UTC 时区偏移写作 UTC+00 而非 Z`() {
        val text = TimeContext.describe(fixed.withZoneSameInstant(ZoneId.of("UTC")))
        assertTrue(text, text.contains("UTC+00:00"))
        assertTrue(text, !text.contains("UTCZ"))
    }

    @Test
    fun `压成单行以省 token`() {
        val text = TimeContext.describe(fixed)
        assertTrue(text, !text.contains("\n"))
        assertTrue(text, text.length < 60)
    }
}
