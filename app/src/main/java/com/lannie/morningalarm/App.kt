package com.lannie.morningalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)

        // 알람 채널: 소리는 TTS가 직접 재생하므로 채널 소리는 끔
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "엄마가 보낸 알람"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_URGENT, "긴급 메시지", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "전면 팝업 긴급 메시지"
                setBypassDnd(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHAT, "메시지", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SVC, "백그라운드 대기", NotificationManager.IMPORTANCE_MIN).apply {
                description = "알람 수신 대기 상태 표시"
            }
        )
    }

    companion object {
        const val CH_ALARM = "alarm"
        const val CH_URGENT = "urgent"
        const val CH_CHAT = "chat"
        const val CH_SVC = "svc"
    }
}
