package com.lannie.morningalarm.alarm

import android.app.KeyguardManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Repo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 긴급 메시지 전면 팝업 — 화면 전체가 빨강↔진홍으로 깜빡인다.
 * 소리는 폰 설정을 따른다: 소리 모드면 알람 스트림으로 1회 낭독 + 진동, 진동 모드면 진동만, 무음이면 화면만.
 * 긴급 메시지는 알람 거절 시간과 상관없이 항상 표시된다.
 */
class UrgentActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null

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
                val blink = rememberInfiniteTransition(label = "blink")
                val t by blink.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
                    label = "t"
                )
                val bg = lerp(Color(0xFFB71C1C), Color(0xFFFF6D00), t)
                Box(
                    modifier = Modifier.fillMaxSize().background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🚨", fontSize = 72.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "$fromName 님의 긴급 메시지",
                            color = Color.White,
                            fontSize = 22.sp,
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
        val audio = getSystemService(AudioManager::class.java)
        val ringer = audio.ringerMode
        val speak = ringer == AudioManager.RINGER_MODE_NORMAL
        val vibrate = ringer != AudioManager.RINGER_MODE_SILENT

        if (vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 1200), 0))
        }
        if (speak) {
            // 소리 모드: 현재 알람 볼륨으로 1회 낭독 (볼륨을 억지로 올리지 않는다)
            tts = Tts.create(this) { engine ->
                engine?.speak("긴급 메시지. $text", TextToSpeech.QUEUE_FLUSH, null, "urgent")
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
