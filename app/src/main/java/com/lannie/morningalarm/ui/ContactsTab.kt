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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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

/** 연결 탭: 연락처 / 요청 / 알람 거절 시간 / 알람 점검 */
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("연결") {
            Text("${prefs.myName} · ${prefs.myPhone}", fontSize = 12.sp, color = Palette.Muted)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            // ---- 받은 요청 ----
            if (incoming.isNotEmpty()) {
                SectionHeader("받은 요청", incoming.size)
                incoming.forEach { req ->
                    AppCard(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    req.fromName.ifBlank {
                                        "가족"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Text
                                )
                                Text(req.fromPhone, fontSize = 12.sp, color = Palette.Muted)
                            }
                            TextButton(onClick = {
                                runCatching { Repo.rejectPairRequest(req.id) }
                            }) { Text("거절", color = Palette.Muted) }
                            Button(onClick = {
                                runCatching { Repo.acceptPairRequest(req.id, prefs.myName) }
                            }) { Text("수락") }
                        }
                    }
                }
            }

            // ---- 연결된 사람 ----
            SectionHeader("연결된 사람", contacts.size)
            if (contacts.isEmpty()) {
                AppCard { Text("아직 없어요. 아래에서 번호로 요청하세요.", fontSize = 13.sp, color = Palette.Muted) }
            }
            contacts.forEach { c ->
                val data = peerData[c.phone]

                @Suppress("UNCHECKED_CAST")
                val h = data?.get("health") as? Map<String, Any>
                val rules = Repo.parseQuietRules(data)
                AppCard(Modifier.padding(bottom = 8.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.name.ifBlank {
                                        c.phone
                                    },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Text
                                )
                                Text(c.phone, fontSize = 12.sp, color = Palette.Muted)
                            }
                            when {
                                h == null -> Pill("상태 없음", Palette.Surface2, Palette.Muted)
                                healthAllOk(h) -> Pill("준비 완료", Palette.TealDim, Palette.Teal)
                                else -> Pill("설정 필요", Palette.DangerDim, Palette.Danger)
                            }
                        }
                        if (rules.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            rules.forEach { Text(Quiet.label(it), fontSize = 12.sp, color = Palette.Warn) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                runCatching {
                                    Repo.sendMessage(
                                        prefs.myPhone,
                                        prefs.myName,
                                        c.phone,
                                        "알람 테스트예요. 확인을 눌러 줘!",
                                        Kind.TEST_ALARM
                                    )
                                }
                                info = "🔊 ${c.name}에게 테스트 알람 전송"
                            }) { Text("🔊 테스트 알람", fontSize = 12.sp) }
                            OutlinedButton(onClick = {
                                runCatching {
                                    Repo.sendMessage(prefs.myPhone, prefs.myName, c.phone, "긴급 테스트예요", Kind.URGENT)
                                }
                                info = "🚨 ${c.name}에게 긴급 팝업 전송"
                            }) { Text("🚨 긴급 팝업", fontSize = 12.sp) }
                        }
                    }
                }
            }
            if (info.isNotBlank()) {
                Text(
                    info,
                    fontSize = 12.sp,
                    color = Palette.Success,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // ---- 새로 연결 ----
            SectionHeader("새로 연결")
            AppCard {
                Column {
                    CountryRow(cc = newCc, onCc = { newCc = it })
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newPhone,
                            onValueChange = { newPhone = it },
                            placeholder = { Text("상대 전화번호") },
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
                                contacts.any { it.phone == p } -> info = "이미 연결됐어요"
                                else -> {
                                    runCatching { Repo.sendPairRequest(prefs.myPhone, prefs.myName, p) }
                                    newPhone = ""
                                    info = "📨 요청 보냄 — 상대가 수락하면 연결돼요"
                                }
                            }
                        }) { Text("요청") }
                    }
                    outgoing.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text(
                                "⏳ ${r.toPhone}",
                                fontSize = 12.sp,
                                color = Palette.Muted,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                runCatching { Repo.sendPairRequest(prefs.myPhone, prefs.myName, r.toPhone) }
                                info = "📨 다시 보냈어요"
                            }) { Text("다시 보내기", fontSize = 12.sp, color = Palette.Orange) }
                        }
                    }
                }
            }

            // ---- 알람 거절 시간 ----
            SectionHeader("알람 거절 시간", quietRules.size)
            AppCard {
                Column {
                    Text("이 시간엔 알람이 울리지 않아요 · 긴급은 예외", fontSize = 12.sp, color = Palette.Muted)
                    Spacer(Modifier.height(8.dp))
                    if (quietRules.isEmpty()) {
                        Text("등록된 시간 없음", fontSize = 13.sp, color = Palette.Muted)
                    }
                    quietRules.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                Quiet.label(rule),
                                fontSize = 14.sp,
                                color = Palette.Text,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { saveQuiet(quietRules.filter { it.id != rule.id }) }) {
                                Text("삭제", color = Palette.Danger, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = {
                        showQuietEditor = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("＋ 추가") }
                }
            }

            // ---- 표시 시간대 ----
            SectionHeader("표시 시간대")
            TimezoneSetting(prefs)

            // ---- 알람 점검 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("알람 점검")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefreshHealth) { Text("새로고침", fontSize = 12.sp, color = Palette.Orange) }
            }
            AppCard {
                Column {
                    FixRow("알림", health.notifOk) {
                        runCatching { context.startActivity(Health.notifSettingsIntent(context)) }
                    }
                    FixRow("정확한 알람", health.exactOk) {
                        Health.exactAlarmIntent(context)?.let { runCatching { context.startActivity(it) } }
                    }
                    FixRow("배터리 최적화 제외", health.batteryOk) {
                        runCatching { context.startActivity(Health.batteryIntent(context)) }
                    }
                    FixRow("다른 앱 위에 표시", health.overlayOk) {
                        runCatching { context.startActivity(Health.overlayIntent(context)) }
                    }
                    FixRow("전체 화면 알림", health.fullscreenOk) {
                        Health.fullscreenIntent(context)?.let { runCatching { context.startActivity(it) } }
                    }
                    FixRow("방해금지 예외", health.dndOk) {
                        runCatching { context.startActivity(Health.soundSettingsIntent()) }
                    }
                    Text(
                        (if (health.alarmVolumePct >= 50) "✅ " else "⚠️ ") +
                            "알람 볼륨 ${health.alarmVolumePct}% (울릴 때 자동 최대)",
                        fontSize = 13.sp,
                        color = Palette.Text,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            RingPlayerService.start(
                                context,
                                alarmId = "",
                                text = "알람 미리 듣기예요. 무음이어도 이렇게 들려요.",
                                ringIndex = 0,
                                type = RingPlayerService.TYPE_PREVIEW
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🔊 내 폰에서 미리 듣기") }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FixRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (ok) "✅ " else "⚠️ ") + label,
            fontSize = 14.sp,
            color = if (ok) Palette.Text else Palette.Danger,
            modifier = Modifier.weight(1f)
        )
        if (!ok) TextButton(onClick = onFix) { Text("해결", color = Palette.Orange) }
    }
}

@Composable
fun TimezoneSetting(prefs: Prefs) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = TzState.zoneId == TzState.SEOUL,
            onClick = {
                TzState.zoneId = TzState.SEOUL
                prefs.displayTz = TzState.SEOUL
            },
            label = { Text("🇰🇷 한국시간") }
        )
        FilterChip(
            selected = TzState.zoneId == TzState.LOCAL,
            onClick = {
                TzState.zoneId = TzState.LOCAL
                prefs.displayTz = TzState.LOCAL
            },
            label = { Text("📍 현지 시간") }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("거절 시간 추가") {
            TextButton(onClick = onCancel) { Text("취소", color = Palette.Muted) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader("사유")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuietReason.ALL.forEach { r ->
                    FilterChip(
                        selected = reason == r,
                        onClick = { reason = r },
                        label = { Text(QuietReason.emoji(r) + " " + QuietReason.label(r), fontSize = 12.sp) }
                    )
                }
            }
            if (reason == QuietReason.OTHER) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("예: 아르바이트") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            SectionHeader("요일 · 없으면 매일")
            DayChips(selected = days, onChange = { days = it })

            SectionHeader("시간 · 24시간제")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("시작") },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
                Text("  ~  ", fontSize = 20.sp, color = Palette.Muted)
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("종료") },
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }
            Text(
                "자정 넘김 가능 (23:00 ~ 07:00)",
                fontSize = 11.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row {
                TextButton(onClick = {
                    startText = "23:00"
                    endText = "07:00"
                }) { Text("밤 23~07", fontSize = 12.sp, color = Palette.Orange) }
                TextButton(onClick = {
                    startText = "09:00"
                    endText = "15:00"
                    days = setOf(1, 2, 3, 4, 5)
                }) { Text("평일 09~15", fontSize = 12.sp, color = Palette.Orange) }
                TextButton(onClick = {
                    startText = "00:00"
                    endText = "00:00"
                }) { Text("하루 종일", fontSize = 12.sp, color = Palette.Orange) }
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Palette.Danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val s = parse(startText)
                    val e = parse(endText)
                    when {
                        s == null -> error = "시작 시간은 09:00 형식"
                        e == null -> error = "종료 시간은 15:00 형식"
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
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("저장", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(28.dp))
        }
    }
}
