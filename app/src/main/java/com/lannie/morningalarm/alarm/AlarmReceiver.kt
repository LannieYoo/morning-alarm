package com.lannie.morningalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.service.SyncService
import com.lannie.morningalarm.util.Quiet
import java.time.ZonedDateTime

/** 예약된 시각 도달 → 거절 시간 확인 → 울림 서비스 시작 + 반복/다음 날 재예약 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val ringIndex = intent.getIntExtra("ringIndex", 0)
        val prefs = Prefs(context)
        val alarm = prefs.getAlarms().find { it.id == alarmId } ?: return

        // 절전 정책으로 수신 대기 서비스가 죽어 있었다면 이 기회에 되살린다 (정확한 알람 예외로 허용됨)
        SyncService.start(context)

        // 첫 회차에서 다음 날(다음 요일) 울림을 미리 예약해 둔다
        if (ringIndex == 0) {
            AlarmScheduler.scheduleNextOccurrence(context, alarm)
        }

        if (!alarm.enabled) return
        if (prefs.isStoppedToday(alarmId)) return

        // 내 알람 거절 시간이면 울리지 않고 보낸 사람 기록에 "거절됨"으로 남긴다
        val quiet = Quiet.find(prefs.getQuietRules(), ZonedDateTime.now())
        if (quiet != null) {
            runCatching {
                Repo.createEvent(
                    AlarmEvent(
                        alarmId = alarm.id,
                        alarmText = alarm.text,
                        ownerPhone = alarm.ownerPhone,
                        ownerName = alarm.ownerName,
                        targetPhone = prefs.myPhone,
                        targetName = prefs.myName,
                        type = "alarm",
                        ringIndex = ringIndex,
                        firedAt = System.currentTimeMillis(),
                        rejected = true,
                        rejectReason = Quiet.reasonText(quiet)
                    )
                )
            }
            return
        }

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
            ownerPhone = alarm.ownerPhone,
            ownerName = alarm.ownerName
        )
    }
}
