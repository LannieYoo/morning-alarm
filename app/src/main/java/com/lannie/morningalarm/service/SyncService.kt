package com.lannie.morningalarm.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ListenerRegistration
import com.lannie.morningalarm.App
import com.lannie.morningalarm.MainActivity
import com.lannie.morningalarm.alarm.AlarmScheduler
import com.lannie.morningalarm.alarm.RingPlayerService
import com.lannie.morningalarm.alarm.UrgentActivity
import com.lannie.morningalarm.data.Kind
import com.lannie.morningalarm.data.Message
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.health.Health
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 딸(수신) 기기의 상시 대기 서비스.
 * - 알람 동기화 → 로컬 예약
 * - 긴급 팝업 / 테스트 알람 / 채팅 실시간 수신
 * - 헬스체크 상태를 주기적으로 업로드 (엄마 화면에 표시)
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<ListenerRegistration>()
    private val handledMessages = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundQuiet()

        val prefs = Prefs(this)
        if (prefs.myPhone.isBlank()) return

        scope.launch {
            runCatching { Repo.ensureAuth() }
            attachListeners(prefs)
            uploadHealth(prefs)
        }

        // 30분마다 헬스체크 업로드 (+ 첫 실행 때 오프라인이어서 익명 로그인이 안 됐다면 재시도)
        val tick = object : Runnable {
            override fun run() {
                if (!Repo.isAuthed()) scope.launch { runCatching { Repo.ensureAuth() } }
                uploadHealth(Prefs(this@SyncService))
                handler.postDelayed(this, 30 * 60_000L)
            }
        }
        handler.postDelayed(tick, 30 * 60_000L)
    }

    private fun attachListeners(prefs: Prefs) {
        val me = prefs.myPhone

        // 알람 동기화 → 로컬 캐시 + 재예약
        listeners += Repo.listenAlarmsFor(me) { alarms ->
            // 엄마가 삭제한 알람은 로컬 예약(반복 회차 포함)도 모두 취소
            val newIds = alarms.map { it.id }.toSet()
            prefs.getAlarms().filter { it.id !in newIds }.forEach {
                AlarmScheduler.cancelAll(this, it.id, maxRepeat = 10)
            }
            prefs.saveAlarms(alarms)
            AlarmScheduler.scheduleAll(this)
        }

        // 나에게 온 새 메시지 처리
        listeners += Repo.listenUndelivered(me) { messages ->
            for (msg in messages) {
                if (msg.id in handledMessages) continue
                handledMessages += msg.id
                Repo.markDelivered(msg.id)
                handleIncoming(msg)
            }
        }
    }

    private fun handleIncoming(msg: Message) {
        // 오래 밀려 있던 메시지(10분 초과)는 팝업 대신 일반 알림으로만
        val fresh = System.currentTimeMillis() - msg.sentAt < 10 * 60_000L
        when (msg.kind) {
            Kind.URGENT -> {
                if (fresh) showUrgentFullscreen(msg) else showChatNotification(msg, "🚨 (놓친 긴급) ")
            }
            Kind.TEST_ALARM -> {
                if (fresh) {
                    RingPlayerService.start(
                        this,
                        alarmId = "",
                        text = msg.text,
                        ringIndex = 0,
                        type = RingPlayerService.TYPE_TEST,
                        messageId = msg.id
                    )
                } else {
                    showChatNotification(msg, "🔊 (놓친 테스트 알람) ")
                }
            }
            else -> showChatNotification(msg, "")
        }
    }

    private fun showUrgentFullscreen(msg: Message) {
        val full = Intent(this, UrgentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("text", msg.text)
            putExtra("messageId", msg.id)
            putExtra("sentAt", msg.sentAt)
        }
        val fullPi = PendingIntent.getActivity(
            this,
            msg.id.hashCode(),
            full,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, App.CH_URGENT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("🚨 엄마의 긴급 메시지")
            .setContentText(msg.text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(msg.id.hashCode(), notif) }
        // 화면이 켜져 있으면 즉시 실행 시도 (일부 기기에서는 알림 헤드업으로 대체됨)
        runCatching { startActivity(full) }
    }

    private fun showChatNotification(msg: Message, prefix: String) {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val peerName = Prefs(this).peerName.ifBlank { "가족" }
        val notif = NotificationCompat.Builder(this, App.CH_CHAT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(prefix + peerName)
            .setContentText(msg.text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(msg.id.hashCode(), notif) }
    }

    private fun uploadHealth(prefs: Prefs) {
        if (prefs.myPhone.isBlank()) return
        runCatching {
            Repo.updateHealth(prefs.myPhone, Health.check(this).toMap())
        }
    }

    private fun startForegroundQuiet() {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, App.CH_SVC)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("모닝콜 대기 중")
            .setContentText("엄마의 알람을 받을 수 있어요")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 3001

        fun start(context: Context) {
            // Android 12+: 백그라운드에서 포그라운드 서비스 시작이 거부되면 예외가 나므로 앱이 죽지 않게 감싼다
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, SyncService::class.java))
            }
        }
    }
}
