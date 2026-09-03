package com.lannie.morningalarm.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.ui.MorningTheme
import com.lannie.morningalarm.ui.Palette
import com.lannie.morningalarm.ui.Pill
import com.lannie.morningalarm.util.answersMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 알람 5분 전 예고 화면 (깜빡임).
 * "그대로 두기"면 예정대로 울리고, "알람 취소"면 (질문이 있으면 정답을 맞힌 뒤) 오늘 울림을 취소하고
 * 보낸 사람 기록에 알람 시각으로 "취소됨"을 남긴다.
 */
class PreAlarmActivity : ComponentActivity() {

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnOnScreen()

        val alarmId = intent.getStringExtra("alarmId") ?: ""
        val triggerAt = intent.getLongExtra("triggerAt", 0L)
        val prefs = Prefs(this)
        val alarm = prefs.getAlarms().find { it.id == alarmId }
        if (alarm == null || triggerAt <= 0L) {
            finish()
            return
        }
        val questions = alarm.questions.filter { it.q.isNotBlank() }
        val ownerName = alarm.ownerName.ifBlank { prefs.contactName(alarm.ownerPhone) }
        val timeText = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(triggerAt))

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)) }

        setContent {
            MorningTheme {
                var asking by remember { mutableStateOf(false) }
                var qIndex by remember {
                    mutableStateOf(
                        if (questions.isEmpty()) 0 else (System.currentTimeMillis() % questions.size).toInt()
                    )
                }
                var answer by remember { mutableStateOf("") }
                var wrong by remember { mutableStateOf(false) }
                var remainSec by remember {
                    mutableStateOf(((triggerAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0))
                }

                // 알람 시각이 되면 예고 화면은 스스로 닫힌다 (울림 화면이 대신 뜸)
                LaunchedEffect(Unit) {
                    while (true) {
                        remainSec = ((triggerAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                        if (remainSec <= 0L) {
                            finish()
                            break
                        }
                        delay(1000)
                    }
                }

                // 깜빡임
                val blink = rememberInfiniteTransition(label = "blink")
                val glow by blink.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
                    label = "glow"
                )

                fun cancelAlarm() {
                    prefs.stopForToday(alarm.id)
                    AlarmScheduler.cancelAll(this@PreAlarmActivity, alarm.id, maxRepeat = AlarmScheduler.MAX_REPEAT)
                    // 취소된 회차 다음 울림(내일/다음 요일)은 다시 예약
                    AlarmScheduler.scheduleNextOccurrence(this@PreAlarmActivity, alarm, afterMillis = triggerAt)
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
                                firedAt = triggerAt,
                                dismissedAt = System.currentTimeMillis(),
                                answered = questions.isNotEmpty(),
                                stoppedForDay = true,
                                cancelled = true
                            )
                        )
                    }
                    finish()
                }

                Column(
                    modifier = Modifier.fillMaxSize().background(
                        Palette.Bg
                    ).padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("⏰", fontSize = 72.sp, modifier = Modifier.alpha(glow))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${remainSec / 60}분 ${remainSec % 60}초 후 알람이 울려요",
                        color = Palette.Orange,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(0.6f + glow * 0.4f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Pill("$ownerName 님의 알람 · $timeText", Palette.Surface2, Palette.Muted)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "“${alarm.text}”",
                        color = Palette.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(36.dp))

                    if (asking && questions.isNotEmpty()) {
                        Text(
                            "정답을 맞히면 이번 알람이 취소돼요",
                            color = Palette.Orange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Q. " + questions[qIndex].q,
                            color = Palette.Text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = {
                                answer = it
                                wrong = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("정답") }
                        )
                        if (wrong) {
                            Spacer(Modifier.height(6.dp))
                            Text("틀렸어요, 다시!", color = Palette.Danger, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (answersMatch(answer, questions[qIndex].a)) {
                                    cancelAlarm()
                                } else {
                                    wrong = true
                                    qIndex = (qIndex + 1) % questions.size
                                    answer = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Palette.Danger,
                                contentColor = Palette.Text
                            ),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) { Text("정답 확인 → 알람 취소", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            asking = false
                        }, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
                    } else {
                        Button(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) { Text("그대로 울리기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { if (questions.isEmpty()) cancelAlarm() else asking = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Danger),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text(if (questions.isEmpty()) "이번 알람 취소" else "이번 알람 취소 (정답 필요)", fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("취소하면 보낸 사람에게 알려져요", color = Palette.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { vibrator?.cancel() }
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
