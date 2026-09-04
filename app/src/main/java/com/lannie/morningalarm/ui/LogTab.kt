package com.lannie.morningalarm.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 알람 하나의 하루치 기록 묶음 */
private data class DayLog(
    val key: String,
    val targetPhone: String,
    val targetName: String,
    val text: String,
    val type: String,
    val firstAt: Long,
    val rings: List<AlarmEvent>
)

/** 기록 탭: 내가 보낸 알람을 날짜·알람별로 묶어 "몇 번 울리고 어떻게 껐는지" 요약 */
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
        val groups = groupByDay(events)
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups, key = { it.key }) { g -> DayLogCard(g, prefs) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 같은 알람의 같은 날 울림을 한 묶음으로 (테스트·즉시 알람은 건별) */
private fun groupByDay(events: List<AlarmEvent>): List<DayLog> {
    val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA).apply { timeZone = TzState.timeZone() }
    return events
        .groupBy { e ->
            if (e.alarmId.isBlank() || e.type != "alarm") e.id else e.alarmId + "_" + dayFmt.format(Date(e.firedAt))
        }
        .map { (key, list) ->
            val sorted = list.sortedBy { it.firedAt }
            val f = sorted.first()
            DayLog(
                key = key,
                targetPhone = f.targetPhone,
                targetName = f.targetName,
                text = f.alarmText,
                type = f.type,
                firstAt = f.firedAt,
                rings = sorted
            )
        }
        .sortedByDescending { it.firstAt }
}

@Composable
private fun DayLogCard(g: DayLog, prefs: Prefs) {
    val cancelled = g.rings.firstOrNull { it.cancelled }
    val rejected = g.rings.all { it.rejected }
    val rang = g.rings.filter { !it.rejected && !it.cancelled }
    val stopped = rang.firstOrNull { it.stoppedForDay }
    val snoozes = rang.count { it.dismissedAt > 0L && !it.stoppedForDay }

    // 요약 문장과 색
    val (summary, color, highlight) = when {
        cancelled != null -> Triple(
            "❌ 취소됨 · 알람 5분 전(${fmtTimeShort(cancelled.dismissedAt)})에 취소" +
                if (cancelled.answered) {
                    if (cancelled.wrongAnswers == 0) " · 정답 한 번에" else " · 정답 (${cancelled.wrongAnswers}번 틀림)"
                } else {
                    ""
                },
            Palette.Danger,
            true
        )
        rejected -> Triple("🚫 거절 시간이라 울리지 않음 · ${g.rings.first().rejectReason}", Palette.Muted, false)
        stopped != null -> {
            val idx = rang.indexOf(stopped) + 1
            val react = ((stopped.dismissedAt - stopped.firedAt) / 1000).coerceAtLeast(0)
            val reactText = if (react < 60) "${react}초 만에" else "${react / 60}분 만에"
            val answerText = when {
                !stopped.answered -> ""
                stopped.wrongAnswers == 0 -> " · 정답 한 번에 ✨"
                else -> " · 정답 (${stopped.wrongAnswers}번 틀림)"
            }
            Triple(
                "✅ ${rang.size}회 울림 → ${idx}회차에 끔 (${fmtTimeShort(stopped.dismissedAt)}, 울린 지 $reactText)$answerText",
                Palette.Success,
                false
            )
        }
        rang.isNotEmpty() && rang.last().dismissedAt > 0L -> Triple(
            "🔁 ${rang.size}회 울림 · 일단 끔 ${snoozes}번 · 아직 종료 안 함",
            Palette.Warn,
            false
        )
        rang.isNotEmpty() -> Triple("😴 ${rang.size}회 울림 · 아직 반응 없음", Palette.Muted, false)
        else -> Triple("", Palette.Muted, false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlight) Palette.DangerDim else Palette.Surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    "→ ${g.targetName.ifBlank {
                        prefs.contactName(g.targetPhone)
                    }}",
                    Palette.OrangeDim,
                    Palette.Orange
                )
                Spacer(Modifier.width(8.dp))
                when (g.type) {
                    "test" -> Pill("테스트", Palette.TealDim, Palette.Teal)
                    "instant" -> Pill("⚡ 즉시", Palette.TealDim, Palette.Teal)
                    else -> if (cancelled != null) Pill("취소", Palette.Danger, Palette.Text)
                }
                Spacer(Modifier.weight(1f))
                Text(fmtDateTime(g.firstAt), fontSize = 12.sp, color = Palette.Muted)
            }
            Spacer(Modifier.height(8.dp))
            Text("“${g.text}”", fontSize = 14.sp, color = Palette.Text)
            Spacer(Modifier.height(6.dp))
            Text(summary, fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold)

            // 회차별 상세 (2회 이상 울렸을 때만)
            if (rang.size > 1) {
                Spacer(Modifier.height(8.dp))
                rang.forEachIndexed { i, e ->
                    RingRow(i + 1, e)
                }
            }
        }
    }
}

@Composable
private fun RingRow(index: Int, e: AlarmEvent) {
    val status = when {
        e.stoppedForDay && e.answered -> "정답 → 종료"
        e.stoppedForDay -> "확인 → 종료"
        e.dismissedAt > 0L -> "일단 끔"
        else -> "반응 없음"
    }
    val dot: Color = when {
        e.stoppedForDay -> Palette.Success
        e.dismissedAt > 0L -> Palette.Warn
        else -> Palette.Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            "${index}회차 ${fmtTimeShort(e.firedAt)}",
            fontSize = 12.sp,
            color = Palette.Muted,
            modifier = Modifier.width(110.dp)
        )
        Text(
            status + if (e.dismissedAt > 0L) " (${fmtTimeShort(e.dismissedAt)})" else "",
            fontSize = 12.sp,
            color = Palette.Text
        )
    }
}
