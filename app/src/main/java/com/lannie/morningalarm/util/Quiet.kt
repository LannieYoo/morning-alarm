package com.lannie.morningalarm.util

import com.lannie.morningalarm.data.QuietReason
import com.lannie.morningalarm.data.QuietRule
import java.time.ZonedDateTime

/** 알람 거절 시간 규칙 판정 (순수 로직, 단위 테스트 대상) */
object Quiet {

    /** 요일(1=월..7=일)과 하루 중 분(0..1439)이 규칙에 걸리는지. 종료 시각은 포함하지 않는다. */
    fun matches(rule: QuietRule, dayOfWeek: Int, minuteOfDay: Int): Boolean {
        val s = rule.startMin
        val e = rule.endMin
        fun dayOk(d: Int) = rule.days.isEmpty() || d in rule.days
        return when {
            s == e -> dayOk(dayOfWeek) // 시작=종료면 하루 종일
            s < e -> dayOk(dayOfWeek) && minuteOfDay >= s && minuteOfDay < e
            else -> {
                // 자정을 넘기는 구간: 오늘 밤 부분 또는 전날 밤에서 이어진 새벽 부분
                val prevDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
                (dayOk(dayOfWeek) && minuteOfDay >= s) || (dayOk(prevDay) && minuteOfDay < e)
            }
        }
    }

    /** 지금 이 시각에 걸리는 첫 규칙 (없으면 null) */
    fun find(rules: List<QuietRule>, at: ZonedDateTime): QuietRule? {
        val day = at.dayOfWeek.value
        val minute = at.hour * 60 + at.minute
        return rules.firstOrNull { matches(it, day, minute) }
    }

    /**
     * 알람(요일 목록 + 시:분)이 어떤 요일에 어떤 규칙에 걸리는지.
     * alarmDays가 비어 있으면 매일(1..7) 검사. 반환: 요일 → 규칙
     */
    fun conflicts(rules: List<QuietRule>, alarmDays: List<Int>, hour: Int, minute: Int): Map<Int, QuietRule> {
        val days = if (alarmDays.isEmpty()) (1..7).toList() else alarmDays.sorted()
        val m = hour * 60 + minute
        val out = linkedMapOf<Int, QuietRule>()
        for (d in days) {
            rules.firstOrNull { matches(it, d, m) }?.let { out[d] = it }
        }
        return out
    }

    fun hm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    private val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

    fun daysText(days: List<Int>): String = when {
        days.isEmpty() -> "매일"
        days.sorted() == listOf(1, 2, 3, 4, 5) -> "월~금"
        days.sorted() == listOf(6, 7) -> "토·일"
        else -> days.sorted().joinToString("·") { DAY_NAMES[it - 1] }
    }

    /** 예: "📚 수업시간 · 월~금 09:00~15:00" */
    fun label(rule: QuietRule): String =
        QuietReason.emoji(rule.reason) + " " + QuietReason.label(rule.reason, rule.note) +
            " · " + daysText(rule.days) + " " + hm(rule.startMin) + "~" + hm(rule.endMin)

    /** 거절 기록에 남길 짧은 사유. 예: "수업시간 · 월~금 09:00~15:00" */
    fun reasonText(rule: QuietRule): String = QuietReason.label(rule.reason, rule.note) + " · " + daysText(rule.days) +
        " " + hm(rule.startMin) + "~" + hm(rule.endMin)
}
