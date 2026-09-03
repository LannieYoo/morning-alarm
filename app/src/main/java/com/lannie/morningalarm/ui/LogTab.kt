package com.lannie.morningalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo

/** 기록 탭: 내가 보낸 알람이 언제 울렸고(또는 거절됐고) 상대가 언제 반응했는지 */
@Composable
fun LogTab(prefs: Prefs) {
    var events by remember { mutableStateOf(listOf<AlarmEvent>()) }

    DisposableEffect(Unit) {
        val reg = Repo.listenEvents(prefs.myPhone) { events = it }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "내가 보낸 알람이 언제 울렸고 상대가 언제 반응했는지 기록이에요 (${TzState.label()} 표시)",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (events.isEmpty()) {
            Text("아직 기록이 없어요", color = Color.Gray, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { e ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row {
                            Text(
                                "→ ${e.targetName.ifBlank {
                                    prefs.contactName(e.targetPhone)
                                }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(fmtDateTime(e.firedAt), fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.width(6.dp))
                            if (e.type == "test") {
                                Text("테스트", fontSize = 11.sp, color = Color(0xFF1565C0))
                            } else if (!e.rejected) {
                                Text("${e.ringIndex + 1}회차 울림", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Text(e.alarmText, fontSize = 13.sp, color = Color.DarkGray)
                        val status = when {
                            e.rejected -> "🚫 거절됨 — ${e.rejectReason.ifBlank { "알람 거절 시간" }}"
                            e.dismissedAt == 0L -> "😴 아직 반응 없음"
                            e.stoppedForDay && e.answered -> "✅ ${fmtTimeShort(e.dismissedAt)} 정답 맞히고 오늘 알람 종료"
                            e.stoppedForDay -> "✅ ${fmtTimeShort(e.dismissedAt)} 확인함 (오늘 알람 종료)"
                            else -> "🔁 ${fmtTimeShort(e.dismissedAt)} 일단 끔 — 잠시 후 다시 울림"
                        }
                        Text(
                            status,
                            fontSize = 13.sp,
                            color = if (e.rejected) Color(0xFFC62828) else Color.Unspecified,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
