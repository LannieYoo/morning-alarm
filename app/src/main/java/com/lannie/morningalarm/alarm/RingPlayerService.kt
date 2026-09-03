package com.lannie.morningalarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lannie.morningalarm.App
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import java.util.Locale

/**
 * 알람 울림 서비스.
 * - 무음/진동 모드여도 알람 스트림(STREAM_ALARM)을 최대 볼륨으로 올려 TTS로 텍스트를 반복해 읽는다.
 * - 통화 중에도 알람 스트림은 재생된다.
 * - 전체 화면 알림으로 RingActivity(잠금화면 위)를 띄운다.
 */
class RingPlayerService : Service() {

    private var tts: TextToSpeech? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var prevVolume = -1
    private var speaking = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            stopSelf()
            return START_NOT_STICKY
        }

        // 이미 울리는 중에 새 울림이 오면(짧은 반복 간격, 테스트 알람 등) 먼저 정리해
        // 볼륨 원복값이 덮어써지거나 TTS가 겹치는 것을 막는다
        if (speaking) stopRinging()

        val text = intent?.getStringExtra("text") ?: "일어나세요"
        val alarmId = intent?.getStringExtra("alarmId") ?: ""
        val ringIndex = intent?.getIntExtra("ringIndex", 0) ?: 0
        val type = intent?.getStringExtra("type") ?: TYPE_ALARM
        val messageId = intent?.getStringExtra("messageId") ?: ""
        val prefs = Prefs(this)

        // 울림 기록 생성 (미리듣기 제외)
        var eventId = ""
        if (type != TYPE_PREVIEW) {
            eventId = runCatching { Repo.newEventId() }.getOrDefault("")
            if (eventId.isNotBlank()) {
                runCatching {
                    Repo.createEvent(
                        AlarmEvent(
                            id = eventId,
                            alarmId = alarmId,
                            alarmText = text,
                            ownerPhone = prefs.peerPhone,
                            targetPhone = prefs.myPhone,
                            type = if (type == TYPE_TEST) "test" else "alarm",
                            ringIndex = ringIndex,
                            firedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        startForegroundWithNotification(text, alarmId, eventId, type, messageId, ringIndex)
        acquireWakeLock()
        raiseAlarmVolume()
        startVibration()
        startTts(text)

        // 회차당 최대 2분 울리고 자동 종료 (반복 회차는 AlarmReceiver가 별도 예약)
        handler.postDelayed({
            stopRinging()
            stopSelf()
        }, RING_DURATION_MS)
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(
        text: String,
        alarmId: String,
        eventId: String,
        type: String,
        messageId: String,
        ringIndex: Int
    ) {
        val full = Intent(this, RingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("text", text)
            putExtra("alarmId", alarmId)
            putExtra("eventId", eventId)
            putExtra("type", type)
            putExtra("messageId", messageId)
            putExtra("ringIndex", ringIndex)
        }
        val fullPi = PendingIntent.getActivity(
            this,
            1001,
            full,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, App.CH_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("모닝콜 알람")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "morningalarm:ring").apply {
            acquire(RING_DURATION_MS + 10_000)
        }
    }

    private fun raiseAlarmVolume() {
        val audio = getSystemService(AudioManager::class.java)
        prevVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 800, 400, 800, 1000)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun startTts(text: String) {
        speaking = true
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            engine.language = Locale.KOREAN
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (speaking) handler.postDelayed({ speak(text) }, 2_500)
                }
            })
            speak(text)
        }
    }

    private fun speak(text: String) {
        if (!speaking) return
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "ring")
    }

    private fun stopRinging() {
        speaking = false
        handler.removeCallbacksAndMessages(null)
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        vibrator?.cancel()
        if (prevVolume >= 0) {
            runCatching {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, prevVolume, 0)
            }
            prevVolume = -1
        }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        const val TYPE_ALARM = "alarm"
        const val TYPE_TEST = "test"
        const val TYPE_PREVIEW = "preview"
        const val ACTION_STOP = "com.lannie.morningalarm.STOP_RING"
        const val NOTIF_ID = 2001
        const val RING_DURATION_MS = 120_000L

        fun start(
            context: Context,
            alarmId: String,
            text: String,
            ringIndex: Int,
            type: String,
            messageId: String = ""
        ) {
            val intent = Intent(context, RingPlayerService::class.java)
                .putExtra("alarmId", alarmId)
                .putExtra("text", text)
                .putExtra("ringIndex", ringIndex)
                .putExtra("type", type)
                .putExtra("messageId", messageId)
            // Android 12+: 백그라운드에서 포그라운드 서비스 시작이 거부되면 예외가 나므로 앱이 죽지 않게 감싼다
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RingPlayerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
