package com.lannie.morningalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lannie.morningalarm.data.Prefs

/** 예약된 시각 도달 → 울림 서비스 시작 + 반복/다음 날 재예약 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val ringIndex = intent.getIntExtra("ringIndex", 0)
        val prefs = Prefs(context)
        val alarm = prefs.getAlarms().find { it.id == alarmId } ?: return

        // 첫 회차에서 다음 날(다음 요일) 울림을 미리 예약해 둔다
        if (ringIndex == 0) {
            AlarmScheduler.scheduleNextOccurrence(context, alarm)
        }

        if (!alarm.enabled) return
        if (prefs.isStoppedToday(alarmId)) return

        // 다음 반복 회차 예약 (정답을 맞히면 cancelRepeats로 취소됨)
        if (ringIndex + 1 < alarm.repeatCount) {
            val next = System.currentTimeMillis() + alarm.intervalMin * 60_000L
            AlarmScheduler.scheduleAt(context, alarmId, next, ringIndex + 1)
        }

        RingPlayerService.start(
            context,
            alarmId = alarm.id,
            text = alarm.text,
            ringIndex = ringIndex,
            type = RingPlayerService.TYPE_ALARM,
        )
    }
}
