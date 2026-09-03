package com.lannie.morningalarm.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.util.answersMatch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 알람이 울릴 때 잠금화면 위로 뜨는 전체 화면. 질문 정답을 맞혀야 오늘 울림이 끝난다. */
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

        val prefs = Prefs(this)
        val alarm = prefs.getAlarms().find { it.id == alarmId }
        val questions = alarm?.questions?.filter { it.q.isNotBlank() } ?: emptyList()

        setContent {
            MaterialTheme {
                var askQuestion by remember { mutableStateOf(false) }
                var qIndex by remember {
                    mutableStateOf(
                        if (questions.isEmpty()) {
                            0
                        } else {
                            (
                                System.currentTimeMillis() %
                                    questions.size
                                ).toInt()
                        }
                    )
                }
                var answer by remember { mutableStateOf("") }
                var wrong by remember { mutableStateOf(false) }

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
                    // 이번 울림만 끄기: 반복 예약은 그대로 두어 간격 후 다시 울린다
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF311B92)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                            color = Color.White,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (type) {
                                RingPlayerService.TYPE_TEST -> "테스트 알람"
                                RingPlayerService.TYPE_PREVIEW -> "알람 미리 듣기"
                                else -> "엄마의 모닝콜 ⏰"
                            },
                            color = Color(0xFFFFD54F),
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        if (type == RingPlayerService.TYPE_ALARM && alarm != null && alarm.repeatCount > 1) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "${ringIndex + 1}번째 울림 / 총 ${alarm.repeatCount}회 · 끄지 않으면 ${alarm.intervalMin}분 후 다시 울려요",
                                color = Color(0xFFB39DDB),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(36.dp))

                        when {
                            type != RingPlayerService.TYPE_ALARM -> {
                                Button(
                                    onClick = { confirmTest() },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) { Text("확인했어요", fontSize = 18.sp) }
                            }

                            askQuestion && questions.isNotEmpty() -> {
                                Text(
                                    "오늘 알람을 끝내려면 정답을 입력하세요",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Q. " + questions[qIndex].q,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = answer,
                                    onValueChange = {
                                        answer = it
                                        wrong = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("정답 입력") }
                                )
                                if (wrong) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("틀렸어요! 다시 생각해 보세요 🙃", color = Color(0xFFFF8A80), fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val ok = answersMatch(answer, questions[qIndex].a)
                                        if (ok) {
                                            stopForDay()
                                        } else {
                                            wrong = true
                                            qIndex = (qIndex + 1) % questions.size
                                            answer = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) { Text("정답 확인", fontSize = 17.sp) }
                            }

                            else -> {
                                Button(
                                    onClick = {
                                        if (questions.isEmpty()) stopForDay() else askQuestion = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043))
                                ) { Text("오늘 알람 끝내기", fontSize = 18.sp) }
                                if (alarm != null && alarm.repeatCount > 1) {
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { snooze() },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text(
                                            "일단 끄기 (${alarm.intervalMin}분 후 다시 울림)",
                                            color = Color.White,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
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
