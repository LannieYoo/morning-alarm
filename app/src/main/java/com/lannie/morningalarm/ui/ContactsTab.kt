@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lannie.morningalarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.alarm.RingPlayerService
import com.lannie.morningalarm.data.Contact
import com.lannie.morningalarm.data.Kind
import com.lannie.morningalarm.data.PairRequest
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.QuietReason
import com.lannie.morningalarm.data.QuietRule
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.health.Health
import com.lannie.morningalarm.health.HealthStatus
import com.lannie.morningalarm.util.Quiet
import com.lannie.morningalarm.util.normalizePhone
import java.util.UUID

/** 연결·상태 탭: 연락처 / 연결 요청 / 내 알람 거절 시간 / 헬스체크 / 테스트 */
@Composable
fun ContactsTab(
    prefs: Prefs,
    contacts: List<Contact>,
    incoming: List<PairRequest>,
    peerData: Map<String, Map<String, Any>>,
    health: HealthStatus,
    onRefreshHealth: () -> Unit
) {
    val context = LocalContext.current
    var outgoing by remember { mutableStateOf(listOf<PairRequest>()) }
    var newCc by remember { mutableStateOf("82") }
    var newPhone by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var quietRules by remember { mutableStateOf(prefs.getQuietRules()) }
    var showQuietEditor by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val reg = Repo.listenPairRequestsFrom(prefs.myPhone) { outgoing = it }
        onDispose { reg.remove() }
    }

    fun saveQuiet(list: List<QuietRule>) {
        quietRules = list
        prefs.saveQuietRules(list)
        runCatching { Repo.updateQuietRules(prefs.myPhone, list) }
    }

    if (showQuietEditor) {
        QuietRuleEditor(
            onSave = { rule ->
                saveQuiet(quietRules + rule)
                showQuietEditor = false
            },
            onCancel = { showQuietEditor = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // ---- 받은 연결 요청 ----
        if (incoming.isNotEmpty()) {
            Title("📨 받은 연결 요청")
            incoming.forEach { req ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${req.fromName.ifBlank { "가족" }} (${req.fromPhone})님의 연결 요청", fontSize = 14.sp)
                        Text("수락하면 서로 알람과 메시지를 보낼 수 있어요", fontSize = 12.sp, color = Color.Gray)
                        Row {
                            Button(onClick = {
                                runCatching { Repo.acceptPairRequest(req.id, prefs.myName) }
                            }) { Text("수락") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { runCatching { Repo.rejectPairRequest(req.id) } }) { Text("거절") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- 연결된 사람 ----
        Title("👥 연결된 사람 (${contacts.size})")
        if (contacts.isEmpty()) {
            Text("아직 없어요. 아래에서 번호로 연결 요청을 보내세요.", fontSize = 13.sp, color = Color.Gray)
        }
        contacts.forEach { c ->
            val data = peerData[c.phone]

            @Suppress("UNCHECKED_CAST")
            val h = data?.get("health") as? Map<String, Any>
            val rules = Repo.parseQuietRules(data)
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(c.name.ifBlank { c.phone }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(c.phone, fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    when {
                        h == null -> Text("상태 정보 없음 — 상대가 앱을 한 번 실행하면 표시돼요", fontSize = 12.sp, color = Color.Gray)
                        healthAllOk(h) -> Text("✅ 알람 받을 준비 완료", fontSize = 12.sp, color = Color(0xFF2E7D32))
                        else -> Text(
                            "⚠️ 상대 폰 설정 때문에 알람이 안 울릴 수 있어요 (상대 폰 [연결·상태] 탭에서 해결)",
                            fontSize = 12.sp,
                            color = Color(0xFFC62828)
                        )
                    }
                    val updated = (h?.get("updatedAt") as? Number)?.toLong() ?: 0L
                    if (updated > 0L) Text("마지막 확인: ${fmtDateTime(updated)}", fontSize = 11.sp, color = Color.Gray)
                    if (rules.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("알람 거절 시간:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        rules.forEach { Text("  " + Quiet.label(it), fontSize = 12.sp, color = Color(0xFF7A5C00)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedButton(onClick = {
                            runCatching {
                                Repo.sendMessage(
                                    prefs.myPhone,
                                    prefs.myName,
                                    c.phone,
                                    "알람 테스트예요. 잘 들리면 화면의 확인을 눌러 줘!",
                                    Kind.TEST_ALARM
                                )
                            }
                            info = "🔊 ${c.name}에게 테스트 알람 전송 — 거절 시간이면 [기록]에 거절로 남아요"
                        }) { Text("🔊 테스트 알람", fontSize = 12.sp) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = {
                            runCatching {
                                Repo.sendMessage(prefs.myPhone, prefs.myName, c.phone, "테스트 긴급 메시지예요", Kind.URGENT)
                            }
                            info = "🚨 ${c.name}에게 테스트 팝업 전송"
                        }) { Text("🚨 테스트 팝업", fontSize = 12.sp) }
                    }
                }
            }
        }
        if (info.isNotBlank()) {
            Text(
                info,
                fontSize = 12.sp,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- 새로 연결 ----
        Title("➕ 새로 연결하기")
        CountryRow(cc = newCc, onCc = { newCc = it })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPhone,
                onValueChange = { newPhone = it },
                label = { Text("상대 전화번호") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val p = normalizePhone(newCc, newPhone)
                when {
                    newPhone.isBlank() -> info = "번호를 입력하세요"
                    p == prefs.myPhone -> info = "내 번호예요"
                    contacts.any { it.phone == p } -> info = "이미 연결된 사람이에요"
                    else -> {
                        runCatching { Repo.sendPairRequest(prefs.myPhone, prefs.myName, p) }
                        newPhone = ""
                        info = "📨 $p 에게 연결 요청을 보냈어요. 상대가 수락하면 목록에 나타나요"
                    }
                }
            }) { Text("요청") }
        }
        outgoing.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text("⏳ ${r.toPhone} 수락 대기 중", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    runCatching { Repo.sendPairRequest(prefs.myPhone, prefs.myName, r.toPhone) }
                    info = "📨 다시 보냈어요"
                }) { Text("다시 보내기", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ---- 내 알람 거절 시간 ----
        Title("⛔ 내 알람 거절 시간")
        Text(
            "이 시간에 오는 알람은 울리지 않고, 보낸 사람 기록에 '거절됨'으로 표시돼요. 상대가 알람을 만들 때도 미리 보여요.\n긴급 메시지는 예외로 항상 전달돼요.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        if (quietRules.isEmpty()) Text("등록된 거절 시간이 없어요", fontSize = 13.sp, color = Color.Gray)
        quietRules.forEach { rule ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(Quiet.label(rule), fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    saveQuiet(quietRules.filter { it.id != rule.id })
                }) { Text("삭제", color = Color(0xFFC62828), fontSize = 12.sp) }
            }
        }
        OutlinedButton(onClick = { showQuietEditor = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 거절 시간 추가") }
        Spacer(Modifier.height(20.dp))

        TimezoneSetting(prefs)
        Spacer(Modifier.height(20.dp))

        // ---- 헬스체크 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("🩺 알람이 울릴 수 있는 상태인가요?")
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRefreshHealth) { Text("새로고침") }
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
        FixRow("다른 앱 위에 표시 허용 (폰 쓰는 중에도 알람 화면 바로 뜸)", health.overlayOk) {
            runCatching { context.startActivity(Health.overlayIntent(context)) }
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
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                RingPlayerService.start(
                    context,
                    alarmId = "",
                    text = "알람 미리 듣기예요. 무음 모드여도 이렇게 크게 들려요.",
                    ringIndex = 0,
                    type = RingPlayerService.TYPE_PREVIEW
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("🔊 알람 미리 듣기 (내 폰에서 바로)") }
        Spacer(Modifier.height(20.dp))

        Title("내 정보")
        Text("${prefs.myName} · ${prefs.myPhone}", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun FixRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (ok) "✅ " else "⚠️ ") + label,
            fontSize = 13.sp,
            color = if (ok) Color.Unspecified else Color(0xFFC62828),
            modifier = Modifier.weight(1f)
        )
        if (!ok) {
            TextButton(onClick = onFix) { Text("해결하기") }
        }
    }
}

@Composable
fun TimezoneSetting(prefs: Prefs) {
    Text("표시 시간대", fontWeight = FontWeight.SemiBold)
    Text("알람 기록·메시지 시각을 어느 시간대로 보여줄지 정해요", fontSize = 12.sp, color = Color.Gray)
    Spacer(Modifier.height(6.dp))
    Row {
        FilterChip(
            selected = TzState.zoneId == TzState.SEOUL,
            onClick = {
                TzState.zoneId = TzState.SEOUL
                prefs.displayTz = TzState.SEOUL
            },
            label = { Text("🇰🇷 한국시간") }
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = TzState.zoneId == TzState.LOCAL,
            onClick = {
                TzState.zoneId = TzState.LOCAL
                prefs.displayTz = TzState.LOCAL
            },
            label = { Text("📍 현지(기기) 시간") }
        )
    }
}

// ---------------- 거절 시간 편집 ----------------

@Composable
private fun QuietRuleEditor(onSave: (QuietRule) -> Unit, onCancel: () -> Unit) {
    var reason by remember { mutableStateOf(QuietReason.SLEEP) }
    var note by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var startText by remember { mutableStateOf("23:00") }
    var endText by remember { mutableStateOf("07:00") }
    var error by remember { mutableStateOf("") }

    fun parse(s: String): Int? {
        val p = s.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("알람 거절 시간 추가", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("이 시간에는 상대가 보낸 알람이 울리지 않아요 (내 폰 현지 시간 기준)", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        Text("사유", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            QuietReason.ALL.forEach { r ->
                FilterChip(
                    selected = reason == r,
                    onClick = { reason = r },
                    label = { Text(QuietReason.emoji(r) + " " + QuietReason.label(r), fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
        if (reason == QuietReason.OTHER) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("기타 사유 (예: 아르바이트)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        Spacer(Modifier.height(16.dp))

        Text("요일 (선택 없으면 매일)", fontWeight = FontWeight.SemiBold)
        DayChips(selected = days, onChange = { days = it })
        Spacer(Modifier.height(16.dp))

        Text("시간 (24시간제, 예: 09:00 ~ 15:00, 자정 넘김 가능: 23:00 ~ 07:00)", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("시작") },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
            Text("  ~  ", fontSize = 20.sp)
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text("종료") },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("빠른 선택:", fontSize = 12.sp, color = Color.Gray)
        Row {
            TextButton(onClick = {
                startText = "23:00"
                endText = "07:00"
            }) { Text("밤 23~07", fontSize = 12.sp) }
            TextButton(onClick = {
                startText = "09:00"
                endText = "15:00"
                days = setOf(1, 2, 3, 4, 5)
            }) { Text("평일 09~15", fontSize = 12.sp) }
            TextButton(onClick = {
                startText = "00:00"
                endText = "00:00"
            }) { Text("하루 종일", fontSize = 12.sp) }
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFC62828), fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Row {
            Button(
                onClick = {
                    val s = parse(startText)
                    val e = parse(endText)
                    when {
                        s == null -> error = "시작 시간을 09:00 형식으로 입력하세요"
                        e == null -> error = "종료 시간을 15:00 형식으로 입력하세요"
                        reason == QuietReason.OTHER && note.isBlank() -> error = "기타 사유를 적어 주세요"
                        else -> onSave(
                            QuietRule(
                                id = UUID.randomUUID().toString(),
                                days = days.sorted(),
                                startMin = s,
                                endMin = e,
                                reason = reason,
                                note = note.trim()
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("저장") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
