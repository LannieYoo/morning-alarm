package com.lannie.morningalarm.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.ui.MorningTheme
import com.lannie.morningalarm.ui.Palette
import com.lannie.morningalarm.ui.Pill
import com.lannie.morningalarm.util.answersMatch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * 알람이 울릴 때 잠금화면 위로 뜨는 전체 화면.
 * 큰 원형 링(남은 울림 시간) 안에 현재 시각, 위에 보낸 사람, 아래에 둥근 버튼.
 * 질문이 있으면 정답을 맞혀야 오늘 울림이 끝난다.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnOnScreen()

        val text = intent.getStringExtra("text") ?: ""
        val alarmId = intent.getStringExtra("alarmId") ?: ""
        val eventId = intent.getStringExtra("eventId") ?: ""
        val type = intent.getStringExtra("type") ?: RingPlayerService.TYPE_ALARM
        val messageId = intent.getStringExtra("messageId") ?: ""
        val ringIndex = intent.getIntExtra("ringIndex", 0)
        val ownerName = (intent.getStringExtra("ownerName") ?: "").ifBlank { "가족" }

        val prefs = Prefs(this)
        val alarm = prefs.getAlarms().find { it.id == alarmId }
        val questions = alarm?.questions?.filter { it.q.isNotBlank() } ?: emptyList()
        val isAlarm = type == RingPlayerService.TYPE_ALARM
        val canSnooze = isAlarm && alarm != null && alarm.repeatCount > 1

        setContent {
            MorningTheme {
                var askQuestion by remember { mutableStateOf(false) }
                var qIndex by remember {
                    mutableStateOf(
                        if (questions.isEmpty()) 0 else (System.currentTimeMillis() % questions.size).toInt()
                    )
                }
                var answer by remember { mutableStateOf("") }
                var wrong by remember { mutableStateOf(false) }

                // 시계 + 남은 울림 시간 링
                var now by remember { mutableStateOf(LocalTime.now()) }
                var progress by remember { mutableStateOf(0f) }
                LaunchedEffect(Unit) {
                    val start = System.currentTimeMillis()
                    while (true) {
                        now = LocalTime.now()
                        val elapsed = (System.currentTimeMillis() - start).toFloat()
                        progress = (elapsed / RingPlayerService.RING_DURATION_MS).coerceIn(0f, 1f)
                        delay(500)
                    }
                }

                fun logDismiss(stopped: Boolean, correct: Boolean) {
                    if (eventId.isNotBlank()) {
                        runCatching {
                            Repo.updateEvent(
                                eventId,
                                mapOf(
                                    "dismissedAt" to System.currentTimeMillis(),
                                    "answered" to correct,
                                    "stoppedForDay" to stopped
                                )
                            )
                        }
                    }
                }

                fun snooze() {
                    RingPlayerService.stop(this@RingActivity)
                    logDismiss(stopped = false, correct = false)
                    finish()
                }

                fun stopForDay() {
                    RingPlayerService.stop(this@RingActivity)
                    if (alarmId.isNotBlank()) {
                        prefs.stopForToday(alarmId)
                        AlarmScheduler.cancelRepeats(this@RingActivity, alarmId, maxRepeat = 10)
                    }
                    logDismiss(stopped = true, correct = questions.isNotEmpty())
                    finish()
                }

                fun confirmTest() {
                    RingPlayerService.stop(this@RingActivity)
                    if (messageId.isNotBlank()) runCatching { Repo.markRead(messageId) }
                    logDismiss(stopped = true, correct = false)
                    finish()
                }

                Column(
                    modifier = Modifier.fillMaxSize().background(
                        Palette.Bg
                    ).padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ---- 보낸 사람 ----
                    Text(
                        when (type) {
                            RingPlayerService.TYPE_TEST -> "TEST ALARM"
                            RingPlayerService.TYPE_PREVIEW -> "PREVIEW"
                            else -> "MORNING CALL"
                        },
                        color = Palette.Orange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (type == RingPlayerService.TYPE_PREVIEW) "미리 듣기" else "$ownerName 님의 알람",
                        color = Palette.Text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (canSnooze) {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            "${ringIndex + 1} / ${alarm!!.repeatCount}회 · ${alarm.intervalMin}분 간격",
                            Palette.Surface2,
                            Palette.Muted
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // ---- 원형 링 + 시각 ----
                    RingClock(progress = 1f - progress, time = now.format(DateTimeFormatter.ofPattern("HH:mm")))

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "“$text”",
                        color = Palette.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.weight(1f))

                    // ---- 버튼 영역 ----
                    when {
                        !isAlarm -> RoundActions(
                            primaryLabel = "확인",
                            primaryIcon = "✓",
                            onPrimary = { confirmTest() }
                        )

                        askQuestion && questions.isNotEmpty() -> {
                            Text(
                                "정답을 맞히면 오늘 알람 끝",
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
                                        stopForDay()
                                    } else {
                                        wrong = true
                                        qIndex = (qIndex + 1) % questions.size
                                        answer = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            ) { Text("정답 확인", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }

                        else -> RoundActions(
                            primaryLabel = "오늘 끝",
                            primaryIcon = "✓",
                            onPrimary = { if (questions.isEmpty()) stopForDay() else askQuestion = true },
                            secondaryLabel = if (canSnooze) "일단 끄기" else null,
                            secondaryIcon = "💤",
                            onSecondary = { snooze() }
                        )
                    }
                }
            }
        }
    }

    /** singleInstance라 다음 회차 울림이 같은 화면으로 오면 새 extras(회차·기록 id)로 다시 그린다 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun turnOnScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
    }
}

/** 남은 울림 시간을 보여주는 주황 링 + 가운데 현재 시각 */
@Composable
private fun RingClock(progress: Float, time: String) {
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Palette.Surface2,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = Palette.Orange,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⏰", fontSize = 34.sp)
            Text(time, color = Palette.Text, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 둥근 버튼들: 보조(선택) + 주요 */
@Composable
private fun RoundActions(
    primaryLabel: String,
    primaryIcon: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    secondaryIcon: String = "",
    onSecondary: () -> Unit = {}
) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
        if (secondaryLabel != null) {
            RoundButton(secondaryLabel, secondaryIcon, Palette.Surface2, Palette.Text, 68.dp, onSecondary)
            Spacer(Modifier.width(36.dp))
        }
        RoundButton(primaryLabel, primaryIcon, Palette.Orange, Palette.Bg, 88.dp, onPrimary)
    }
}

@Composable
private fun RoundButton(
    label: String,
    icon: String,
    bg: Color,
    fg: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
            modifier = Modifier.size(size),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) { Text(icon, fontSize = if (size > 80.dp) 34.sp else 26.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
