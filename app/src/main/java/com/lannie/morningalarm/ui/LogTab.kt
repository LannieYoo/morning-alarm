package com.lannie.morningalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("기록") {
            Text(TzState.label(), fontSize = 12.sp, color = Palette.Muted)
        }
        if (events.isEmpty()) {
            EmptyState("📋", "기록이 없어요", "보낸 알람이 울리면 여기 쌓여요")
            return
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.id }) { e ->
                val status: String
                val color: androidx.compose.ui.graphics.Color
                when {
                    e.rejected -> {
                        status = "🚫 거절 · ${e.rejectReason.ifBlank { "거절 시간" }}"
                        color = Palette.Danger
                    }
                    e.dismissedAt == 0L -> {
                        status = "😴 아직 반응 없음"
                        color = Palette.Muted
                    }
                    e.stoppedForDay && e.answered -> {
                        status = "✅ ${fmtTimeShort(e.dismissedAt)} 정답 · 오늘 종료"
                        color = Palette.Success
                    }
                    e.stoppedForDay -> {
                        status = "✅ ${fmtTimeShort(e.dismissedAt)} 확인 · 오늘 종료"
                        color = Palette.Success
                    }
                    else -> {
                        status = "🔁 ${fmtTimeShort(e.dismissedAt)} 일단 끔"
                        color = Palette.Warn
                    }
                }
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                "→ ${e.targetName.ifBlank {
                                    prefs.contactName(e.targetPhone)
                                }}",
                                Palette.OrangeDim,
                                Palette.Orange
                            )
                            Spacer(Modifier.width(8.dp))
                            if (e.type == "test") {
                                Pill("테스트", Palette.TealDim, Palette.Teal)
                            } else if (!e.rejected) {
                                Pill("${e.ringIndex + 1}회차", Palette.Surface2, Palette.Muted)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(fmtDateTime(e.firedAt), fontSize = 12.sp, color = Palette.Muted)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("“${e.alarmText}”", fontSize = 14.sp, color = Palette.Text)
                        Spacer(Modifier.height(6.dp))
                        Text(status, fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
