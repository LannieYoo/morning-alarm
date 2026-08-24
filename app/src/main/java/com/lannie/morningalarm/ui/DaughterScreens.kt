@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lannie.morningalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.alarm.RingPlayerService
import com.lannie.morningalarm.data.Alarm
import com.lannie.morningalarm.data.PairRequest
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.health.Health
import com.lannie.morningalarm.health.HealthStatus
import com.lannie.morningalarm.service.SyncService

/** 자녀(수신) 홈 화면: 알람(조회 전용) / 메시지 / 상태 */
@Composable
fun DaughterHome(prefs: Prefs) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var health by remember { mutableStateOf(Health.check(context)) }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        if (!health.allOk) {
            WarnBanner("⚠️ 지금 설정으로는 알람이 안 울릴 수 있어요 — [상태] 탭에서 해결하세요") { tab = 2 }
        }

        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> DaughterAlarmsTab(prefs)
                1 -> ChatScreen(me = prefs.myPhone, peer = prefs.peerPhone, peerName = prefs.peerName)
                2 -> DaughterStatusTab(prefs, health, onRefresh = { health = Health.check(context) })
            }
        }
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("⏰") }, label = { Text("알람") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("💬") }, label = { Text("메시지") })
            NavigationBarItem(
                selected = tab == 2,
                onClick = { tab = 2; health = Health.check(context) },
                icon = { Text("🩺") }, label = { Text("상태") },
            )
        }
    }
}

// ---------------- 알람 탭 (조회 전용) ----------------

@Composable
private fun DaughterAlarmsTab(prefs: Prefs) {
    var alarms by remember { mutableStateOf(prefs.getAlarms()) }

    DisposableEffect(Unit) {
        val reg = Repo.listenAlarmsFor(prefs.myPhone) { list ->
            alarms = list.sortedBy { it.hour * 60 + it.minute }
        }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "예약된 알람 (엄마가 관리 — 여기서는 볼 수만 있어요)",
            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp),
        )
        if (alarms.isEmpty()) {
            Text("아직 예약된 알람이 없어요", color = Color.Gray, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(alarms, key = { it.id }) { alarm ->
                AlarmReadOnlyCard(alarm)
            }
        }
    }
}

@Composable
private fun AlarmReadOnlyCard(alarm: Alarm) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${two(alarm.hour)}:${two(alarm.minute)}",
                    fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.primary else Color.Gray,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(daysLabel(alarm.days), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${alarm.repeatCount}회 울림 · ${alarm.intervalMin}분 간격" +
                            if (alarm.questions.isNotEmpty()) " · 끄려면 질문 정답 필요" else "",
                        fontSize = 12.sp, color = Color.Gray,
                    )
                }
            }
            Text(alarm.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            if (!alarm.enabled) {
                Text("(꺼져 있음)", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

// ---------------- 상태 탭 ----------------

@Composable
private fun DaughterStatusTab(prefs: Prefs, health: HealthStatus, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var requests by remember { mutableStateOf(listOf<PairRequest>()) }
    var paired by remember { mutableStateOf(prefs.paired) }

    DisposableEffect(Unit) {
        val reg = Repo.listenPairRequestsTo(prefs.myPhone) { requests = it }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("연결", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (paired) {
            Text("✅ ${prefs.peerName}와 연결됨 · ${prefs.peerPhone}", fontSize = 13.sp)
        } else if (requests.isEmpty()) {
            Text("아직 연결 요청이 없어요. 엄마 폰에서 내 번호로 요청을 보내면 여기에 떠요.", fontSize = 13.sp, color = Color.Gray)
        }
        requests.forEach { req ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${req.fromName.ifBlank { "엄마" }} (${req.fromPhone})님의 연결 요청", fontSize = 14.sp)
                    Text("수락하면 알람과 메시지를 받을 수 있어요", fontSize = 12.sp, color = Color.Gray)
                    Row {
                        Button(onClick = {
                            runCatching { Repo.setPairStatus(req.id, "accepted") }
                            prefs.peerPhone = req.fromPhone
                            prefs.peerName = req.fromName.ifBlank { "엄마" }
                            prefs.paired = true
                            paired = true
                            SyncService.start(context)
                        }) { Text("수락") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            runCatching { Repo.setPairStatus(req.id, "rejected") }
                        }) { Text("거절") }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        TimezoneSetting(prefs)
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("알람이 울릴 수 있는 상태인가요?", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRefresh) { Text("새로고침") }
        }

        FixRow("알림 권한", health.notifOk) {
            runCatching { context.startActivity(Health.notifSettingsIntent(context)) }
        }
        FixRow("정확한 알람 허용", health.exactOk) {
            Health.exactAlarmIntent(context)?.let { runCatching { context.startActivity(it) } }
        }
        FixRow("배터리 최적화 제외", health.batteryOk) {
            runCatching { context.startActivity(Health.batteryIntent(context)) }
        }
        FixRow("전체 화면 알림 허용", health.fullscreenOk) {
            Health.fullscreenIntent(context)?.let { runCatching { context.startActivity(it) } }
        }
        FixRow("방해금지 모드 (알람 허용)", health.dndOk) {
            runCatching { context.startActivity(Health.soundSettingsIntent()) }
        }
        Text(
            (if (health.alarmVolumePct >= 50) "✅" else "⚠️") +
                " 알람 볼륨 ${health.alarmVolumePct}% — 울릴 때 자동으로 최대로 올라가요",
            fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp),
        )
        Spacer(Modifier.height(20.dp))

        Text("테스트", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                RingPlayerService.start(
                    context, alarmId = "", text = "알람 미리 듣기예요. 무음 모드여도 이렇게 크게 들려요.",
                    ringIndex = 0, type = RingPlayerService.TYPE_PREVIEW,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("🔊 알람 미리 듣기 (내 폰에서 바로)") }
        Spacer(Modifier.height(20.dp))

        Text("내 정보", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("${prefs.myName} · ${prefs.myPhone}", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FixRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (ok) "✅ " else "⚠️ ") + label,
            fontSize = 13.sp,
            color = if (ok) Color.Unspecified else Color(0xFFC62828),
            modifier = Modifier.weight(1f),
        )
        if (!ok) {
            TextButton(onClick = onFix) { Text("해결하기") }
        }
    }
}
