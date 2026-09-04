package com.lannie.morningalarm.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lannie.morningalarm.App
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.service.SyncService
import com.lannie.morningalarm.util.Quiet
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 예약된 시각 도달 → (예고 / 거절 시간 확인 / 울림 서비스 시작) + 반복·다음 날 재예약 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val ringIndex = intent.getIntExtra("ringIndex", 0)
        val prefs = Prefs(context)
        val alarm = prefs.getAlarms().find { it.id == alarmId } ?: return

        // 절전 정책으로 수신 대기 서비스가 죽어 있었다면 이 기회에 되살린다 (정확한 알람 예외로 허용됨)
        SyncService.start(context)

        if (ringIndex == AlarmScheduler.PRE_INDEX) {
            handlePreAlarm(context, prefs, alarm, intent.getLongExtra("triggerAt", 0L))
            return
        }

        // 첫 회차에서 다음 날(다음 요일) 울림을 미리 예약해 둔다
        if (ringIndex == 0) {
            AlarmScheduler.scheduleNextOccurrence(context, alarm)
        }

        if (!alarm.isLive()) return
        if (prefs.isStoppedToday(alarmId)) return
        // 연결을 끊은 상대의 알람은 울리지 않는다
        if (prefs.isDisconnected(alarm.ownerPhone)) return

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
            ownerName = alarm.ownerName,
            soundMode = alarm.soundMode
        )
    }

    /** 5분 전 예고: 꺼졌거나 오늘 종료됐거나 거절 시간에 울릴 예정이면 예고하지 않는다 */
    private fun handlePreAlarm(
        context: Context,
        prefs: Prefs,
        alarm: com.lannie.morningalarm.data.Alarm,
        triggerAt: Long
    ) {
        if (!alarm.isLive() || triggerAt <= 0L) return
        if (prefs.isStoppedToday(alarm.id)) return
        if (prefs.isDisconnected(alarm.ownerPhone)) return
        val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(triggerAt), ZoneId.systemDefault())
        if (Quiet.find(prefs.getQuietRules(), at) != null) return

        val full = Intent(context, PreAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("alarmId", alarm.id)
            putExtra("triggerAt", triggerAt)
        }
        val fullPi = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            full,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, App.CH_URGENT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ 5분 후 알람이 울려요")
            .setContentText("${alarm.ownerName.ifBlank { "가족" }} 님의 알람 · ${alarm.text}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setAutoCancel(true)
            .setTimeoutAfter(AlarmScheduler.PRE_LEAD_MS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(PRE_NOTIF_BASE + alarm.id.hashCode(), notif) }
        // "다른 앱 위에 표시" 권한이 있으면 앱이 꺼져 있어도 바로 뜬다
        runCatching { context.startActivity(full) }
    }

    companion object {
        const val PRE_NOTIF_BASE = 40_000
    }
}
