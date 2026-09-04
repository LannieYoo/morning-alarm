package com.lannie.morningalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)

        // 알람 채널: 목소리는 TTS가 직접 재생하지만, 채널이 완전 무음이면 안드로이드가 "조용한 알림"으로 취급해
        // 전체 화면(RingActivity)을 띄우지 않는다. 그래서 무음 파일을 소리로 걸고 진동을 켜 긴급 채널과 같은 등급으로 만든다.
        nm.deleteNotificationChannel("alarm")
        val silence = Uri.parse("android.resource://$packageName/${R.raw.silence}")
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "가족이 보낸 알람 (전체 화면)"
                setSound(silence, alarmAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_URGENT, "긴급 메시지", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "전면 팝업 긴급 메시지"
                setBypassDnd(true)
            }
        )

        // 메시지 채널: 전용 차임음 + 진동 (진동 모드면 시스템이 소리 대신 진동만 울림).
        // 채널 설정은 만든 뒤 바꿀 수 없어서 새 id로 만들고 예전 채널은 지운다.
        nm.deleteNotificationChannel("chat")
        val chime = Uri.parse("android.resource://$packageName/${R.raw.message_chime}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CH_CHAT, "메시지", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "가족의 메시지 · 연결 요청"
                setSound(chime, attrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 120, 220)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SVC, "백그라운드 대기", NotificationManager.IMPORTANCE_MIN).apply {
                description = "알람 수신 대기 상태 표시"
            }
        )
    }

    companion object {
        const val CH_ALARM = "alarm_v2"
        const val CH_URGENT = "urgent"
        const val CH_CHAT = "chat_v2"
        const val CH_SVC = "svc"
    }
}
