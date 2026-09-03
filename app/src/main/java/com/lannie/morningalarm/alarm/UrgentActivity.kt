package com.lannie.morningalarm.alarm

import android.app.KeyguardManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Repo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 긴급 메시지 전면 팝업.
 * 눈에 띄는 빨강-주황 전체 화면 + 강한 진동 + 알람 스트림으로 메시지 1회 낭독.
 * 긴급 메시지는 알람 거절 시간과 상관없이 항상 표시된다.
 */
class UrgentActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var prevVolume = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnOnScreen()

        val text = intent.getStringExtra("text") ?: ""
        val messageId = intent.getStringExtra("messageId") ?: ""
        val sentAt = intent.getLongExtra("sentAt", System.currentTimeMillis())
        val fromName = (intent.getStringExtra("fromName") ?: "").ifBlank { "가족" }

        startEffects(text)

        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFFD50000), Color(0xFFFF6D00)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🚨", fontSize = 64.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "$fromName 님의 긴급 메시지",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(sentAt)),
                            color = Color(0xFFFFE0B2),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(28.dp))
                        Text(
                            text,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = {
                                stopEffects()
                                if (messageId.isNotBlank()) runCatching { Repo.markRead(messageId) }
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFFD50000)
                            )
                        ) { Text("확인했어요", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    private fun startEffects(text: String) {
        // 강한 진동 반복
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 1200), 0))

        // 알람 스트림으로 1회 낭독 (무음이어도 들림)
        val audio = getSystemService(AudioManager::class.java)
        prevVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.KOREAN
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.speak("긴급 메시지. $text", TextToSpeech.QUEUE_FLUSH, null, "urgent")
            }
        }
    }

    private fun stopEffects() {
        vibrator?.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        if (prevVolume >= 0) {
            runCatching {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, prevVolume, 0)
            }
            prevVolume = -1
        }
    }

    override fun onDestroy() {
        stopEffects()
        super.onDestroy()
    }

    private fun turnOnScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
    }
}
