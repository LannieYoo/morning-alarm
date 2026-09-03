package com.lannie.morningalarm

import com.lannie.morningalarm.alarm.AlarmScheduler
import com.lannie.morningalarm.data.Alarm
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 다음 울림 시각 계산 (자녀 폰 현지 시간 기준) */
class AlarmTimeTest {

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    // 2026-09-02 (수요일) 06:30:15 KST
    private val wedMorning: ZonedDateTime = ZonedDateTime.of(2026, 9, 2, 6, 30, 15, 0, seoul)

    @Test
    fun `daily alarm later today fires today`() {
        val alarm = Alarm(hour = 7, minute = 0)
        val next = AlarmScheduler.nextTrigger(alarm, wedMorning)
        assertEquals(ZonedDateTime.of(2026, 9, 2, 7, 0, 0, 0, seoul), next)
    }

    @Test
    fun `daily alarm already passed today fires tomorrow`() {
        val alarm = Alarm(hour = 6, minute = 0)
        val next = AlarmScheduler.nextTrigger(alarm, wedMorning)
        assertEquals(ZonedDateTime.of(2026, 9, 3, 6, 0, 0, 0, seoul), next)
    }

    @Test
    fun `alarm at the exact current minute does not fire again today`() {
        // 06:30 알람이 06:30:15에 계산되면(방금 울린 직후) 내일로 넘어가야 한다
        val alarm = Alarm(hour = 6, minute = 30)
        val next = AlarmScheduler.nextTrigger(alarm, wedMorning)
        assertEquals(ZonedDateTime.of(2026, 9, 3, 6, 30, 0, 0, seoul), next)
    }

    @Test
    fun `weekday-only alarm skips the weekend`() {
        // 금요일 20:00에 계산, 월~금 07:00 알람 → 다음 주 월요일
        val friEvening = ZonedDateTime.of(2026, 9, 4, 20, 0, 0, 0, seoul)
        val alarm = Alarm(hour = 7, minute = 0, days = listOf(1, 2, 3, 4, 5))
        val next = AlarmScheduler.nextTrigger(alarm, friEvening)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(ZonedDateTime.of(2026, 9, 7, 7, 0, 0, 0, seoul), next)
    }

    @Test
    fun `single-day alarm on the same weekday but earlier time waits a full week`() {
        // 수요일 06:30에 계산, 수요일 06:00 알람 → 다음 주 수요일
        val alarm = Alarm(hour = 6, minute = 0, days = listOf(3))
        val next = AlarmScheduler.nextTrigger(alarm, wedMorning)
        assertEquals(ZonedDateTime.of(2026, 9, 9, 6, 0, 0, 0, seoul), next)
    }

    @Test
    fun `result always has zero seconds and is in the future`() {
        val alarm = Alarm(hour = 23, minute = 59, days = listOf(7))
        val next = AlarmScheduler.nextTrigger(alarm, wedMorning)
        assertEquals(0, next.second)
        assertEquals(0, next.nano)
        assertTrue(next.isAfter(wedMorning))
        assertEquals(DayOfWeek.SUNDAY, next.dayOfWeek)
    }

    @Test
    fun `next occurrence after firing is the following day not the same minute`() {
        // AlarmReceiver는 울린 직후 now+1분 기준으로 다음 회차를 잡는다
        val justFired = ZonedDateTime.of(2026, 9, 2, 7, 0, 3, 0, seoul)
        val alarm = Alarm(hour = 7, minute = 0)
        val next = AlarmScheduler.nextTrigger(alarm, justFired.plusMinutes(1))
        assertEquals(ZonedDateTime.of(2026, 9, 3, 7, 0, 0, 0, seoul), next)
    }
}
