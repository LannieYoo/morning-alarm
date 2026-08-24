package com.lannie.morningalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.service.SyncService

/** 재부팅 후 알람 재예약 + 수신 대기 서비스 재시작 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AlarmScheduler.scheduleAll(context)
        val prefs = Prefs(context)
        if (prefs.onboarded && prefs.role == "daughter") {
            // 일부 기기는 부팅 직후 포그라운드 서비스 시작을 제한할 수 있음
            runCatching { SyncService.start(context) }
        }
    }
}
