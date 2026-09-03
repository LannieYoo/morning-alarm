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
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ListenerRegistration
import com.lannie.morningalarm.App
import com.lannie.morningalarm.MainActivity
import com.lannie.morningalarm.alarm.AlarmScheduler
import com.lannie.morningalarm.alarm.RingPlayerService
import com.lannie.morningalarm.alarm.UrgentActivity
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Kind
import com.lannie.morningalarm.data.Message
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.health.Health
import com.lannie.morningalarm.util.Quiet
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 모든 기기의 상시 대기 서비스 (누구나 알람을 받을 수 있으므로 역할 구분 없음).
 * - 나에게 오는 알람 동기화 → 로컬 예약
 * - 연락처(수락된 연결) 동기화 → 로컬 캐시
 * - 긴급 팝업 / 테스트·즉시 알람 / 채팅 / 연결 요청 실시간 수신
 * - 헬스체크·거절 시간 업로드 (상대 화면에 표시)
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<ListenerRegistration>()
    private val handledMessages = mutableSetOf<String>()
    private val notifiedRequests = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundQuiet()

        val prefs = Prefs(this)
        if (prefs.myPhone.isBlank()) return

        scope.launch {
            runCatching { Repo.ensureAuth() }
            attachListeners(prefs)
            uploadStatus(prefs)
        }

        // 30분마다 상태 업로드 (+ 첫 실행 때 오프라인이어서 익명 로그인이 안 됐다면 재시도)
        val tick = object : Runnable {
            override fun run() {
                if (!Repo.isAuthed()) scope.launch { runCatching { Repo.ensureAuth() } }
                uploadStatus(Prefs(this@SyncService))
                handler.postDelayed(this, 30 * 60_000L)
            }
        }
        handler.postDelayed(tick, 30 * 60_000L)
    }

    private fun attachListeners(prefs: Prefs) {
        val me = prefs.myPhone

        // 연락처 동기화 (알림·울림 화면에서 이름 표시용)
        listeners += Repo.listenContacts(me) { contacts ->
            prefs.saveContacts(contacts)
        }

        // 나에게 오는 알람 동기화 → 로컬 캐시 + 재예약
        listeners += Repo.listenAlarmsFor(me) { alarms ->
            // 보낸 사람이 삭제한 알람은 로컬 예약(반복 회차 포함)도 모두 취소
            val newIds = alarms.map { it.id }.toSet()
            prefs.getAlarms().filter { it.id !in newIds }.forEach {
                AlarmScheduler.cancelAll(this, it.id, maxRepeat = AlarmScheduler.MAX_REPEAT)
            }
            // 보낸 사람이 수정한 알람: 진행 중 반복 회차 취소 + "오늘 종료" 표시 해제 → 새 시각에 다시 울림
            for (a in alarms) {
                val known = prefs.alarmVersion(a.id)
                if (known != -1L && known != a.updatedAt) {
                    AlarmScheduler.cancelAll(this, a.id, maxRepeat = AlarmScheduler.MAX_REPEAT)
                    prefs.clearStopped(a.id)
                }
                prefs.setAlarmVersion(a.id, a.updatedAt)
            }
            prefs.saveAlarms(alarms)
            AlarmScheduler.scheduleAll(this)
        }

        // 내가 보낸 알람의 반응(정답·확인·취소·거절)을 알림으로
        listeners += Repo.listenEventChanges(me) { events ->
            for (e in events) notifyEventChange(e)
        }

        // 나에게 온 새 메시지 처리
        listeners += Repo.listenUndelivered(me) { messages ->
            for (msg in messages) {
                if (msg.id in handledMessages) continue
                handledMessages += msg.id
                Repo.markDelivered(msg.id)
                handleIncoming(msg, prefs)
            }
        }

        // 새 연결 요청 알림 (같은 번호는 한 번만)
        listeners += Repo.listenPairRequestsTo(me) { reqs ->
            for (r in reqs) {
                if (r.fromPhone in notifiedRequests) continue
                notifiedRequests += r.fromPhone
                showSimpleNotification(
                    id = r.fromPhone.hashCode(),
                    title = "📨 ${r.fromName.ifBlank { r.fromPhone }} 님의 연결 요청",
                    text = "[연결] 탭에서 수락하면 서로 알람과 메시지를 보낼 수 있어요"
                )
            }
        }
    }

    private val notifiedEventStates = mutableSetOf<String>()

    /** 받는 사람의 반응을 보낸 사람에게 알린다 (같은 상태는 한 번만) */
    private fun notifyEventChange(e: AlarmEvent) {
        val who = e.targetName.ifBlank { Prefs(this).contactName(e.targetPhone) }
        val at = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(e.firedAt))
        val (state, text) = when {
            e.cancelled -> "cancelled" to "❌ $who 님이 $at 알람을 취소했어요"
            e.rejected -> "rejected" to "🚫 $who 님의 거절 시간이라 울리지 않았어요 (${e.rejectReason})"
            e.stoppedForDay && e.answered -> "answered" to "✅ $who 님이 정답을 맞히고 알람을 껐어요"
            e.stoppedForDay -> "stopped" to "✅ $who 님이 알람을 확인했어요"
            e.dismissedAt > 0L -> "snoozed" to "🔁 $who 님이 일단 껐어요 · 잠시 후 다시 울려요"
            else -> return
        }
        val key = e.id + ":" + state
        if (!notifiedEventStates.add(key)) return
        showSimpleNotification(id = key.hashCode(), title = text, text = "“${e.alarmText}”")
    }

    private fun handleIncoming(msg: Message, prefs: Prefs) {
        // 오래 밀려 있던 메시지(10분 초과)는 팝업 대신 일반 알림으로만
        val fresh = System.currentTimeMillis() - msg.sentAt < 10 * 60_000L
        val fromName = msg.fromName.ifBlank { prefs.contactName(msg.fromPhone) }
        when (msg.kind) {
            Kind.URGENT -> {
                // 긴급은 거절 시간과 상관없이 항상 전달
                if (fresh) showUrgentFullscreen(msg, fromName) else showChatNotification(msg, fromName, "🚨 놓친 긴급 · ")
            }
            Kind.TEST_ALARM, Kind.INSTANT_ALARM -> {
                val instant = msg.kind == Kind.INSTANT_ALARM
                val quiet = Quiet.find(prefs.getQuietRules(), ZonedDateTime.now())
                when {
                    quiet != null -> {
                        // 거절 시간: 울리지 않고 보낸 사람 기록에 남긴다
                        runCatching {
                            Repo.createEvent(
                                AlarmEvent(
                                    alarmText = msg.text,
                                    ownerPhone = msg.fromPhone,
                                    ownerName = fromName,
                                    targetPhone = prefs.myPhone,
                                    targetName = prefs.myName,
                                    type = if (instant) "instant" else "test",
                                    firedAt = System.currentTimeMillis(),
                                    rejected = true,
                                    rejectReason = Quiet.reasonText(quiet)
                                )
                            )
                        }
                        showChatNotification(msg, fromName, "⛔ 거절 시간이라 울리지 않음 · ")
                    }
                    fresh -> RingPlayerService.start(
                        this,
                        alarmId = "",
                        text = msg.text,
                        ringIndex = 0,
                        type = if (instant) RingPlayerService.TYPE_INSTANT else RingPlayerService.TYPE_TEST,
                        messageId = msg.id,
                        ownerPhone = msg.fromPhone,
                        ownerName = fromName
                    )
                    else -> showChatNotification(msg, fromName, if (instant) "⚡ 놓친 즉시 알람 · " else "🔊 놓친 테스트 알람 · ")
                }
            }
            else -> showChatNotification(msg, fromName, "")
        }
    }

    private fun showUrgentFullscreen(msg: Message, fromName: String) {
        val full = Intent(this, UrgentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("text", msg.text)
            putExtra("messageId", msg.id)
            putExtra("sentAt", msg.sentAt)
            putExtra("fromName", fromName)
        }
        val fullPi = PendingIntent.getActivity(
            this,
            msg.id.hashCode(),
            full,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, App.CH_URGENT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("🚨 $fromName 님의 긴급 메시지")
            .setContentText(msg.text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(msg.id.hashCode(), notif) }
        // 화면이 켜져 있으면 즉시 실행 시도 ("다른 앱 위에 표시" 권한이 있으면 바로 뜸)
        runCatching { startActivity(full) }
    }

    /** 채팅 말풍선 알림 (헤드업 + 전용 차임음/진동은 채널 설정을 따른다) */
    private fun showChatNotification(msg: Message, fromName: String, prefix: String) {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val sender = Person.Builder().setName(prefix + fromName).setKey(msg.fromPhone).build()
        val me = Person.Builder().setName("나").setKey(Prefs(this).myPhone).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(msg.text, msg.sentAt, sender)
        val notif = NotificationCompat.Builder(this, App.CH_CHAT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(style)
            .setContentTitle(prefix + fromName)
            .setContentText(msg.text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(msg.id.hashCode(), notif) }
    }

    private fun showSimpleNotification(id: Int, title: String, text: String) {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, App.CH_CHAT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(id, notif) }
    }

    /** 헬스체크 + 거절 시간을 users/{me}에 올린다 (상대가 알람 만들 때 참고) */
    private fun uploadStatus(prefs: Prefs) {
        if (prefs.myPhone.isBlank()) return
        runCatching { Repo.updateHealth(prefs.myPhone, Health.check(this).toMap()) }
        runCatching { Repo.updateQuietRules(prefs.myPhone, prefs.getQuietRules()) }
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
            .setContentText("가족의 알람과 메시지를 받을 수 있어요")
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
