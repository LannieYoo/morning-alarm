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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.alarm.RingPlayerService
import com.lannie.morningalarm.data.Avatars
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
    var testInfo by remember { mutableStateOf("") }
    var quietRules by remember { mutableStateOf(prefs.getQuietRules()) }
    var showQuietEditor by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf(prefs.myAvatar) }
    var showAvatars by remember { mutableStateOf(false) }
    var disconnectTarget by remember { mutableStateOf<Contact?>(null) }
    var hidden by remember { mutableStateOf(prefs.hiddenPhones()) }

    disconnectTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { disconnectTarget = null },
            containerColor = Palette.Surface,
            title = {
                Text("${c.name.ifBlank { c.phone }} 님과 연결을 끊을까요?", color = Palette.Text, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "서로 알람과 메시지를 보낼 수 없게 돼요. 상대 화면에는 '연결 해제됨'으로 표시돼요.\n다시 연결하려면 새로 요청하면 돼요.",
                    color = Palette.Muted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching { Repo.disconnect(prefs.myPhone, c.phone) }
                        disconnectTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.Text)
                ) { Text("연결 끊기") }
            },
            dismissButton = { TextButton(onClick = { disconnectTarget = null }) { Text("취소", color = Palette.Muted) } }
        )
    }

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

    // 같은 번호의 중복 요청은 하나로 보여준다
    val incomingByPhone = incoming.groupBy { it.fromPhone }
    val outgoingPhones = outgoing.map { it.toPhone }.distinct().filter { p -> contacts.none { it.phone == p } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("연결")
        Column(Modifier.padding(horizontal = 16.dp)) {
            // ---- 내 프로필 ----
            AppCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(avatar, size = 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prefs.myName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                            Text(prefs.myPhone, fontSize = 12.sp, color = Palette.Muted)
                        }
                        TextButton(onClick = { showAvatars = !showAvatars }) {
                            Text(if (showAvatars) "닫기" else "아이콘 바꾸기", color = Palette.Orange, fontSize = 12.sp)
                        }
                    }
                    if (showAvatars) {
                        fun pick(picked: String) {
                            avatar = picked
                            prefs.myAvatar = picked
                            runCatching { Repo.updateProfile(prefs.myPhone, prefs.myName, picked) }
                            showAvatars = false
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("가족", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
                        Spacer(Modifier.height(6.dp))
                        AvatarPicker(
                            options = Avatars.FAMILY.map { it.first },
                            selected = avatar,
                            labels = Avatars.FAMILY.toMap(),
                            onPick = ::pick
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("기타", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted)
                        Spacer(Modifier.height(6.dp))
                        AvatarPicker(options = Avatars.OTHERS, selected = avatar, onPick = ::pick)
                    }
                }
            }

            // ---- 내 폰 설정 (부족한 게 있으면 맨 위에) ----
            if (!health.allOk) {
                SectionHeader("⚠️ 내 폰 설정 필요")
                Text("알람이 제대로 울리려면 아래 항목의 '해결'을 눌러 켜 주세요", fontSize = 12.sp, color = Palette.Danger)
                Spacer(Modifier.height(6.dp))
                HealthCard(health, onRefreshHealth)
            }

            // ---- 받은 요청 ----
            if (incomingByPhone.isNotEmpty()) {
                SectionHeader("받은 요청", incomingByPhone.size)
                incomingByPhone.forEach { (phone, reqs) ->
                    val req = reqs.maxByOrNull { it.createdAt } ?: reqs.first()
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
                                Text(phone, fontSize = 12.sp, color = Palette.Muted)
                            }
                            TextButton(onClick = {
                                reqs.forEach { r -> runCatching { Repo.rejectPairRequest(r.id) } }
                            }) { Text("거절", color = Palette.Muted) }
                            Button(onClick = {
                                // 같은 번호에서 여러 번 왔어도 전부 수락 처리해 대기 목록에 남지 않게
                                reqs.forEach { r -> runCatching { Repo.acceptPairRequest(r.id, prefs.myName) } }
                            }) { Text("수락", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // ---- 연결된 사람 ----
            val activeContacts = contacts.filter { it.active }
            val inactiveContacts = contacts.filter { !it.active && it.phone !in hidden }
            SectionHeader("연결된 사람", activeContacts.size)
            if (activeContacts.isEmpty()) {
                AppCard { Text("아직 없어요. 아래에서 번호로 요청하세요.", fontSize = 13.sp, color = Palette.Muted) }
            }
            activeContacts.forEach { c ->
                val data = peerData[c.phone]
                val name = c.name.ifBlank { c.phone }

                @Suppress("UNCHECKED_CAST")
                val h = data?.get("health") as? Map<String, Any>
                val missing = Health.missingFrom(h)
                val rules = Repo.parseQuietRules(data)
                AppCard(Modifier.padding(bottom = 8.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(Repo.avatarOf(data), size = 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                                Text(c.phone, fontSize = 12.sp, color = Palette.Muted)
                            }
                            when {
                                h == null -> Pill("상태 없음", Palette.Surface2, Palette.Muted)
                                missing.isEmpty() -> Pill("준비 완료", Palette.TealDim, Palette.Teal)
                                else -> Pill("상대 설정 필요", Palette.DangerDim, Palette.Danger)
                            }
                        }
                        if (missing.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "⚠️ $name 님 폰에서 켜야 할 설정: " + missing.joinToString(" · ") +
                                    "\n(이 폰에서는 할 수 없어요 · $name 님 앱 [연결] 탭 맨 위에서 '해결')",
                                fontSize = 12.sp,
                                color = Palette.Danger
                            )
                        }
                        if (rules.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            rules.forEach { Text(Quiet.label(it), fontSize = 12.sp, color = Palette.Warn) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                testInfo = "🔊 ${name}에게 테스트 알람 전송"
                            }) { Text("🔊 테스트", fontSize = 12.sp) }
                            OutlinedButton(onClick = {
                                runCatching {
                                    Repo.sendMessage(prefs.myPhone, prefs.myName, c.phone, "긴급 테스트예요", Kind.URGENT)
                                }
                                testInfo = "🚨 ${name}에게 긴급 팝업 전송"
                            }) { Text("🚨 긴급", fontSize = 12.sp) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                disconnectTarget = c
                            }) { Text("연결 끊기", color = Palette.Danger, fontSize = 12.sp) }
                        }
                    }
                }
            }

            // ---- 연결 해제된 사람 (회색) ----
            if (inactiveContacts.isNotEmpty()) {
                SectionHeader("연결 해제됨", inactiveContacts.size)
                inactiveContacts.forEach { c ->
                    val name = c.name.ifBlank { c.phone }
                    val byMe = c.disconnectedBy == prefs.myPhone
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).alpha(0.55f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Palette.Surface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar("🔌", size = 44.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                                    Text(c.phone, fontSize = 12.sp, color = Palette.Muted)
                                }
                                Pill("연결 해제됨", Palette.Surface2, Palette.Muted)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (byMe) "내가 연결을 끊었어요" else "$name 님이 연결을 끊었어요",
                                fontSize = 12.sp,
                                color = Palette.Muted
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    runCatching { Repo.sendPairRequest(prefs.myPhone, prefs.myName, c.phone) }
                                    info = "📨 $name 님에게 다시 연결 요청을 보냈어요"
                                }) { Text("다시 연결 요청", fontSize = 12.sp) }
                                TextButton(onClick = {
                                    prefs.hidePhone(c.phone)
                                    hidden = prefs.hiddenPhones()
                                }) { Text("목록에서 지우기", fontSize = 12.sp, color = Palette.Muted) }
                            }
                        }
                    }
                }
            }
            if (testInfo.isNotBlank()) {
                Text(
                    testInfo,
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
                        OutlinedButton(
                            onClick = {
                                val p = normalizePhone(newCc, newPhone)
                                when {
                                    newPhone.isBlank() -> info = "번호를 입력하세요"
                                    p == prefs.myPhone -> info = "내 번호예요"
                                    contacts.any { it.phone == p && it.active } -> info = "이미 연결된 사람이에요"
                                    outgoingPhones.contains(p) -> info = "이미 요청을 보냈어요 · 상대 수락 대기 중"
                                    else -> {
                                        runCatching { Repo.sendPairRequest(prefs.myPhone, prefs.myName, p) }
                                        newPhone = ""
                                        info = "📨 요청 보냄 · 상대가 수락하면 연결돼요"
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Orange)
                        ) { Text("요청") }
                    }
                    if (info.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            info,
                            fontSize = 12.sp,
                            color = if (info.startsWith("📨")) Palette.Success else Palette.Warn
                        )
                    }
                    outgoingPhones.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text("⏳ $p 수락 대기", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                runCatching { Repo.sendPairRequest(prefs.myPhone, prefs.myName, p) }
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

            // ---- 알람 점검 (전부 정상이면 아래에) ----
            if (health.allOk) {
                SectionHeader("✅ 알람 점검 · 모두 정상")
                HealthCard(health, onRefreshHealth)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 내 폰 설정 점검 카드: 부족한 항목은 '해결' 버튼으로 설정 화면 이동 */
@Composable
private fun HealthCard(health: HealthStatus, onRefreshHealth: () -> Unit) {
    val context = LocalContext.current
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.weight(1f)
                ) { Text("🔊 미리 듣기", fontSize = 12.sp) }
                OutlinedButton(onClick = onRefreshHealth, modifier = Modifier.weight(1f)) {
                    Text("다시 검사", fontSize = 12.sp)
                }
            }
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
