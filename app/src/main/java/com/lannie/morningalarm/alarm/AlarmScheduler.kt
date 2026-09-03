package com.lannie.morningalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lannie.morningalarm.MainActivity
import com.lannie.morningalarm.data.Alarm
import com.lannie.morningalarm.data.Prefs
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 받는 기기에서 알람을 로컬 AlarmManager로 예약한다.
 * - ringIndex 0..N : 울림 회차 (setAlarmClock, 절전 중에도 정확)
 * - ringIndex -1   : 5분 전 예고 (PRE_INDEX)
 */
object AlarmScheduler {

    const val PRE_INDEX = -1
    const val PRE_LEAD_MS = 5 * 60_000L
    const val MAX_REPEAT = 10

    /**
     * 캐시된 모든 알람의 다음 울림을 다시 예약 (동기화/재부팅 시 호출).
     * 첫 회차(ringIndex 0)와 예고만 갱신하고 진행 중인 반복 회차는 건드리지 않는다
     * (자녀가 "일단 끄기"한 뒤 서비스가 재시작돼도 반복 울림이 사라지지 않도록).
     * 꺼진 알람은 반복 회차까지 모두 취소한다.
     */
    fun scheduleAll(context: Context) {
        val alarms = Prefs(context).getAlarms()
        for (alarm in alarms) {
            if (alarm.enabled) {
                scheduleFirstRing(context, alarm, nextTrigger(alarm, ZonedDateTime.now()))
            } else {
                cancelAll(context, alarm.id, maxRepeat = MAX_REPEAT)
            }
        }
    }

    /** 방금 울린(또는 취소된) 회차 이후의 첫 울림 예약. afterMillis 기준 1분 뒤부터 찾는다. */
    fun scheduleNextOccurrence(context: Context, alarm: Alarm, afterMillis: Long = System.currentTimeMillis()) {
        if (!alarm.enabled) return
        val from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), ZoneId.systemDefault()).plusMinutes(1)
        scheduleFirstRing(context, alarm, nextTrigger(alarm, from))
    }

    /** 첫 회차 + 5분 전 예고 예약 */
    private fun scheduleFirstRing(context: Context, alarm: Alarm, at: ZonedDateTime) {
        val triggerAt = at.toInstant().toEpochMilli()
        scheduleAt(context, alarm.id, triggerAt, ringIndex = 0)
        val preAt = triggerAt - PRE_LEAD_MS
        if (preAt > System.currentTimeMillis()) {
            schedulePre(context, alarm.id, preAt, triggerAt)
        } else {
            cancelPre(context, alarm.id)
        }
    }

    /** 다음 울림 시각 계산. days가 비어 있으면 매일. */
    fun nextTrigger(alarm: Alarm, from: ZonedDateTime): ZonedDateTime {
        var candidate = from.withHour(alarm.hour).withMinute(alarm.minute)
            .withSecond(0).withNano(0)
        repeat(8) {
            val dayOk = alarm.days.isEmpty() || alarm.days.contains(candidate.dayOfWeek.value)
            if (candidate.isAfter(from) && dayOk) return candidate
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    /** 특정 시각에 울림 예약. ringIndex>0은 같은 알람의 반복 울림. */
    fun scheduleAt(context: Context, alarmId: String, timeMillis: Long, ringIndex: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(context, alarmId, ringIndex)

        if (canExact(am)) {
            val show = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(timeMillis, show), pi)
        } else {
            // 정확한 알람 권한이 없으면 근사치로라도 (헬스체크가 경고를 띄움)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
        }
    }

    /** 5분 전 예고 예약 (원래 알람 시각을 함께 넘긴다) */
    private fun schedulePre(context: Context, alarmId: String, preAt: Long, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(context, alarmId, PRE_INDEX, triggerAt)
        if (canExact(am)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preAt, pi)
        }
    }

    fun cancelPre(context: Context, alarmId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, alarmId, PRE_INDEX))
    }

    /** 남아 있는 반복 울림 취소 (정답 맞혔을 때) */
    fun cancelRepeats(context: Context, alarmId: String, maxRepeat: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 1..maxRepeat) {
            am.cancel(firePendingIntent(context, alarmId, i))
        }
    }

    /** 이 알람의 모든 예약(예고 포함) 취소 */
    fun cancelAll(context: Context, alarmId: String, maxRepeat: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in PRE_INDEX..maxRepeat) {
            am.cancel(firePendingIntent(context, alarmId, i))
        }
    }

    private fun canExact(am: AlarmManager): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    private fun firePendingIntent(
        context: Context,
        alarmId: String,
        ringIndex: Int,
        triggerAt: Long = 0L
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra("alarmId", alarmId)
            .putExtra("ringIndex", ringIndex)
            .putExtra("triggerAt", triggerAt)
        val requestCode = alarmId.hashCode() * 31 + ringIndex
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
