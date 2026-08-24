package com.lannie.morningalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lannie.morningalarm.MainActivity
import com.lannie.morningalarm.data.Alarm
import com.lannie.morningalarm.data.Prefs
import java.time.ZonedDateTime

/**
 * 딸 기기에서 알람을 로컬 AlarmManager로 예약한다.
 * setAlarmClock을 사용해 절전(Doze) 상태에서도 정확히 울린다.
 */
object AlarmScheduler {

    /** 캐시된 모든 알람의 다음 울림을 다시 예약 (동기화/재부팅 시 호출) */
    fun scheduleAll(context: Context) {
        val alarms = Prefs(context).getAlarms()
        for (alarm in alarms) {
            cancelAll(context, alarm.id, maxRepeat = 10)
            if (alarm.enabled) {
                val next = nextTrigger(alarm, ZonedDateTime.now())
                scheduleAt(context, alarm.id, next.toInstant().toEpochMilli(), ringIndex = 0)
            }
        }
    }

    /** 오늘 이 알람이 이미 울렸다면 다음 날 이후의 첫 울림을 예약 */
    fun scheduleNextOccurrence(context: Context, alarm: Alarm) {
        if (!alarm.enabled) return
        val next = nextTrigger(alarm, ZonedDateTime.now().plusMinutes(1))
        scheduleAt(context, alarm.id, next.toInstant().toEpochMilli(), ringIndex = 0)
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

        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(timeMillis, show), pi)
        } else {
            // 정확한 알람 권한이 없으면 근사치로라도 (헬스체크가 경고를 띄움)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
        }
    }

    /** 남아 있는 반복 울림 취소 (정답 맞혔을 때) */
    fun cancelRepeats(context: Context, alarmId: String, maxRepeat: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 1..maxRepeat) {
            am.cancel(firePendingIntent(context, alarmId, i))
        }
    }

    /** 이 알람의 모든 예약 취소 */
    fun cancelAll(context: Context, alarmId: String, maxRepeat: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 0..maxRepeat) {
            am.cancel(firePendingIntent(context, alarmId, i))
        }
    }

    private fun firePendingIntent(context: Context, alarmId: String, ringIndex: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra("alarmId", alarmId)
            .putExtra("ringIndex", ringIndex)
        val requestCode = alarmId.hashCode() * 31 + ringIndex
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
