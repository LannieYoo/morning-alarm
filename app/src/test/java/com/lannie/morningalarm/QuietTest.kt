package com.lannie.morningalarm

import com.lannie.morningalarm.data.QuietReason
import com.lannie.morningalarm.data.QuietRule
import com.lannie.morningalarm.util.Quiet
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 알람 거절 시간 규칙 판정 */
class QuietTest {

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    // 월~금 09:00~15:00 수업
    private val classRule =
        QuietRule(
            id = "c",
            days = listOf(1, 2, 3, 4, 5),
            startMin = 9 * 60,
            endMin = 15 * 60,
            reason = QuietReason.CLASS
        )

    // 매일 23:00~07:00 취침 (자정 넘김)
    private val sleepRule =
        QuietRule(id = "s", days = emptyList(), startMin = 23 * 60, endMin = 7 * 60, reason = QuietReason.SLEEP)

    @Test
    fun `inside class hours on a weekday is quiet`() {
        assertTrue(Quiet.matches(classRule, 3, 10 * 60)) // 수 10:00
        assertTrue(Quiet.matches(classRule, 1, 9 * 60)) // 월 09:00 (시작 포함)
    }

    @Test
    fun `end time is exclusive and weekend is not quiet`() {
        assertFalse(Quiet.matches(classRule, 3, 15 * 60)) // 수 15:00 (종료 미포함)
        assertFalse(Quiet.matches(classRule, 6, 10 * 60)) // 토 10:00
    }

    @Test
    fun `overnight rule covers late night and early morning`() {
        assertTrue(Quiet.matches(sleepRule, 2, 23 * 60 + 30)) // 화 23:30
        assertTrue(Quiet.matches(sleepRule, 3, 6 * 60)) // 수 06:00
        assertFalse(Quiet.matches(sleepRule, 3, 7 * 60)) // 수 07:00
        assertFalse(Quiet.matches(sleepRule, 3, 12 * 60)) // 수 12:00
    }

    @Test
    fun `overnight rule limited to friday night only applies saturday early morning`() {
        val friNight = QuietRule(days = listOf(5), startMin = 23 * 60, endMin = 7 * 60)
        assertTrue(Quiet.matches(friNight, 5, 23 * 60 + 30)) // 금 23:30
        assertTrue(Quiet.matches(friNight, 6, 3 * 60)) // 토 03:00 (금요일 밤에서 이어짐)
        assertFalse(Quiet.matches(friNight, 4, 23 * 60 + 30)) // 목 23:30
        assertFalse(Quiet.matches(friNight, 5, 3 * 60)) // 금 03:00 (목요일 밤은 규칙 아님)
    }

    @Test
    fun `sunday night wraps into monday morning`() {
        val sunNight = QuietRule(days = listOf(7), startMin = 22 * 60, endMin = 6 * 60)
        assertTrue(Quiet.matches(sunNight, 1, 5 * 60)) // 월 05:00
    }

    @Test
    fun `same start and end means whole day`() {
        val allDay = QuietRule(days = listOf(6), startMin = 0, endMin = 0)
        assertTrue(Quiet.matches(allDay, 6, 0))
        assertTrue(Quiet.matches(allDay, 6, 23 * 60 + 59))
        assertFalse(Quiet.matches(allDay, 7, 12 * 60))
    }

    @Test
    fun `find returns the rule active now`() {
        val rules = listOf(classRule, sleepRule)
        val wedClass = ZonedDateTime.of(2026, 9, 2, 10, 0, 0, 0, seoul) // 수 10:00
        val wedEvening = ZonedDateTime.of(2026, 9, 2, 19, 0, 0, 0, seoul) // 수 19:00
        val thuDawn = ZonedDateTime.of(2026, 9, 3, 2, 0, 0, 0, seoul) // 목 02:00
        assertEquals("c", Quiet.find(rules, wedClass)?.id)
        assertNull(Quiet.find(rules, wedEvening))
        assertEquals("s", Quiet.find(rules, thuDawn)?.id)
    }

    @Test
    fun `daily alarm at 10am conflicts on weekdays only`() {
        val c = Quiet.conflicts(listOf(classRule), emptyList(), 10, 0)
        assertEquals(setOf(1, 2, 3, 4, 5), c.keys)
    }

    @Test
    fun `weekend alarm at 10am has no conflict`() {
        val c = Quiet.conflicts(listOf(classRule), listOf(6, 7), 10, 0)
        assertTrue(c.isEmpty())
    }

    @Test
    fun `alarm at 6am conflicts with sleep every day`() {
        val c = Quiet.conflicts(listOf(sleepRule), emptyList(), 6, 0)
        assertEquals(7, c.size)
        assertNotNull(c[7])
    }

    @Test
    fun `labels are human readable`() {
        assertEquals("📚 수업시간 · 월~금 09:00~15:00", Quiet.label(classRule))
        assertEquals("😴 취침시간 · 매일 23:00~07:00", Quiet.label(sleepRule))
        val other =
            QuietRule(
                days = listOf(2, 4),
                startMin = 18 * 60,
                endMin = 20 * 60,
                reason = QuietReason.OTHER,
                note = "알바"
            )
        assertEquals("⛔ 기타(알바) · 화·목 18:00~20:00", Quiet.label(other))
        assertEquals("수업시간 · 월~금 09:00~15:00", Quiet.reasonText(classRule))
    }
}
