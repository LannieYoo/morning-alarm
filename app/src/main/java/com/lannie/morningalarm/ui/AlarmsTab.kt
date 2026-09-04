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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Alarm
import com.lannie.morningalarm.data.Contact
import com.lannie.morningalarm.data.Kind
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Question
import com.lannie.morningalarm.data.QuietRule
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.data.SoundMode
import com.lannie.morningalarm.util.Quiet
import com.lannie.morningalarm.util.alarmPresets
import com.lannie.morningalarm.util.vocative
import java.time.ZonedDateTime

/** 알람 탭: 보낸 알람(편집) + 받은 알람(조회) + 즉시 알람 */
@Composable
fun AlarmsTab(prefs: Prefs, contacts: List<Contact>, peerData: Map<String, Map<String, Any>>) {
    var sent by remember { mutableStateOf(listOf<Alarm>()) }
    var received by remember { mutableStateOf(prefs.getAlarms()) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showInstant by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val a = Repo.listenAlarmsOwned(prefs.myPhone) { sent = it.sortedBy { x -> x.hour * 60 + x.minute } }
        val b = Repo.listenAlarmsFor(prefs.myPhone) { received = it.sortedBy { x -> x.hour * 60 + x.minute } }
        onDispose {
            a.remove()
            b.remove()
        }
    }

    fun rulesOf(phone: String): List<QuietRule> = Repo.parseQuietRules(peerData[phone])

    if (showEditor) {
        AlarmEditor(
            initial = editing ?: Alarm(ownerPhone = prefs.myPhone, ownerName = prefs.myName),
            contacts = contacts,
            rulesOf = ::rulesOf,
            onSave = { alarm ->
                runCatching { Repo.saveAlarm(alarm) }
                showEditor = false
            },
            onCancel = { showEditor = false }
        )
        return
    }
    if (showInstant) {
        InstantAlarmScreen(
            prefs = prefs,
            contacts = contacts,
            rulesOf = ::rulesOf,
            onSent = { name ->
                info = "⚡ $name 에게 지금 알람을 보냈어요"
                showInstant = false
            },
            onCancel = { showInstant = false }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("알람") {
            OutlinedButton(
                onClick = { showInstant = true },
                enabled = contacts.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Teal)
            ) { Text("⚡ 지금", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    editing = null
                    showEditor = true
                },
                enabled = contacts.isNotEmpty()
            ) { Text("＋ 만들기", fontWeight = FontWeight.Bold) }
        }
        if (info.isNotBlank()) {
            Text(
                info,
                fontSize = 13.sp,
                color = Palette.Success,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when {
            contacts.isEmpty() -> EmptyState("👥", "먼저 연결하세요", "[연결] 탭에서 번호로 요청", art = { AlarmClockArt() })
            sent.isEmpty() && received.isEmpty() ->
                EmptyState("⏰", "알람이 없어요", "＋ 만들기로 예약하거나 ⚡ 지금 바로 보내요", art = { AlarmClockArt() })
            else -> LazyColumn(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (sent.isNotEmpty()) {
                    item { SectionHeader("보낸 알람", sent.size) }
                    items(sent, key = { "s" + it.id }) { alarm ->
                        val conflicts = Quiet.conflicts(
                            rulesOf(alarm.targetPhone),
                            alarm.days,
                            alarm.hour,
                            alarm.minute
                        )
                        SentAlarmCard(
                            alarm = alarm,
                            targetName = contactLabel(contacts, alarm.targetPhone),
                            conflicts = conflicts,
                            onToggle = { on -> runCatching { Repo.saveAlarm(alarm.copy(enabled = on)) } },
                            onEdit = {
                                editing = alarm
                                showEditor = true
                            },
                            onDelete = { runCatching { Repo.deleteAlarm(alarm.id) } }
                        )
                    }
                }
                if (received.isNotEmpty()) {
                    item { SectionHeader("받은 알람", received.size) }
                    items(received, key = { "r" + it.id }) { alarm ->
                        ReceivedAlarmCard(
                            alarm,
                            ownerName = alarm.ownerName.ifBlank {
                                contactLabel(contacts, alarm.ownerPhone)
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SentAlarmCard(
    alarm: Alarm,
    targetName: String,
    conflicts: Map<Int, QuietRule>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (alarm.enabled) Palette.Orange else Palette.Muted
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("→ $targetName", Palette.OrangeDim, Palette.Orange)
                Spacer(Modifier.width(6.dp))
                SoundModePill(alarm.soundMode)
                Spacer(Modifier.weight(1f))
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${two(alarm.hour)}:${two(alarm.minute)}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        daysLabel(alarm.days),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Text
                    )
                    Text(
                        "${alarm.repeatCount}회 · ${alarm.intervalMin}분 간격" +
                            if (alarm.questions.isNotEmpty()) " · 질문 ${alarm.questions.size}" else "",
                        fontSize = 12.sp,
                        color = Palette.Muted
                    )
                }
            }
            Text("“${alarm.text}”", fontSize = 14.sp, color = Palette.Text, modifier = Modifier.padding(top = 4.dp))
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(conflictText(targetName, alarm.days, conflicts), fontSize = 12.sp, color = Palette.Danger)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("수정", color = Palette.Orange) }
                TextButton(onClick = onDelete) { Text("삭제", color = Palette.Danger) }
            }
        }
    }
}

@Composable
private fun ReceivedAlarmCard(alarm: Alarm, ownerName: String) {
    val accent = if (alarm.enabled) Palette.Teal else Palette.Muted
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("← $ownerName", Palette.TealDim, Palette.Teal)
                Spacer(Modifier.width(6.dp))
                SoundModePill(alarm.soundMode)
                Spacer(Modifier.weight(1f))
                if (!alarm.enabled) Text("꺼짐", fontSize = 12.sp, color = Palette.Muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${two(alarm.hour)}:${two(alarm.minute)}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        daysLabel(alarm.days),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Text
                    )
                    Text(
                        "${alarm.repeatCount}회 · ${alarm.intervalMin}분 간격" +
                            if (alarm.questions.isNotEmpty()) " · 정답 필요" else "",
                        fontSize = 12.sp,
                        color = Palette.Muted
                    )
                }
            }
            Text("“${alarm.text}”", fontSize = 14.sp, color = Palette.Text, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 거절 시간 충돌 안내문. 예) "⛔ 월~금 거절 — 유진의 📚 수업시간 · 월~금 09:00~15:00" */
fun conflictText(targetName: String, alarmDays: List<Int>, conflicts: Map<Int, QuietRule>): String {
    if (conflicts.isEmpty()) return ""
    val totalDays = if (alarmDays.isEmpty()) 7 else alarmDays.distinct().size
    val byRule = conflicts.entries.groupBy({ it.value }, { it.key })
    val parts = byRule.entries.joinToString("\n") { (rule, days) ->
        "⛔ ${Quiet.daysText(days.sorted())} 거절 — ${targetName}의 ${Quiet.label(rule)}"
    }
    return if (conflicts.size >= totalDays) "$parts\n모든 요일이 거절돼요. 시간을 바꿔 주세요." else parts
}

// ---------------- 즉시 알람 ----------------

@Composable
private fun InstantAlarmScreen(
    prefs: Prefs,
    contacts: List<Contact>,
    rulesOf: (String) -> List<QuietRule>,
    onSent: (String) -> Unit,
    onCancel: () -> Unit
) {
    var target by remember { mutableStateOf(contacts.firstOrNull()?.phone ?: "") }
    var soundMode by remember { mutableStateOf(SoundMode.FORCE) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val targetName = contactLabel(contacts, target)
    val quietNow = Quiet.find(rulesOf(target), ZonedDateTime.now())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("⚡ 지금 바로 알람") {
            TextButton(onClick = onCancel) { Text("취소", color = Palette.Muted) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("예약 없이 상대 폰에서 바로 울려요", fontSize = 13.sp, color = Palette.Muted)

            SectionHeader("울림 방식")
            SoundModePicker(mode = soundMode, onChange = { soundMode = it })

            SectionHeader("누구에게")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                contacts.forEach { c ->
                    FilterChip(
                        selected = c.phone == target,
                        onClick = { target = c.phone },
                        label = { Text(c.name.ifBlank { c.phone }) }
                    )
                }
            }
            if (quietNow != null) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Palette.DangerDim),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "⛔ 지금은 ${targetName}의 ${Quiet.label(quietNow)}\n보내도 울리지 않고 거절로 기록돼요",
                        fontSize = 13.sp,
                        color = Palette.Danger,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            SectionHeader("읽어줄 말")
            MessagePicker(targetName = targetName, text = text, onText = { text = it })

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Palette.Danger, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    when {
                        target.isBlank() -> error = "받을 사람을 선택하세요"
                        text.isBlank() -> error = "읽어줄 말을 입력하세요"
                        else -> {
                            runCatching {
                                Repo.sendMessage(
                                    prefs.myPhone,
                                    prefs.myName,
                                    target,
                                    text.trim(),
                                    Kind.INSTANT_ALARM,
                                    soundMode
                                )
                            }
                            onSent(targetName)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Teal, contentColor = Palette.Bg),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("⚡ 지금 울리기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---------------- 알람 편집 ----------------

@Composable
private fun AlarmEditor(
    initial: Alarm,
    contacts: List<Contact>,
    rulesOf: (String) -> List<QuietRule>,
    onSave: (Alarm) -> Unit,
    onCancel: () -> Unit
) {
    var target by remember { mutableStateOf(initial.targetPhone.ifBlank { contacts.firstOrNull()?.phone ?: "" }) }
    var soundMode by remember { mutableStateOf(initial.soundMode.ifBlank { SoundMode.FORCE }) }
    var hourText by remember { mutableStateOf(two(initial.hour)) }
    var minuteText by remember { mutableStateOf(two(initial.minute)) }
    var text by remember { mutableStateOf(initial.text) }
    var days by remember { mutableStateOf(initial.days.toSet()) }
    var repeatCount by remember { mutableStateOf(initial.repeatCount.toFloat()) }
    var intervalMin by remember { mutableStateOf(initial.intervalMin.toFloat()) }
    val questions = remember { mutableStateListOf<Question>().apply { addAll(initial.questions) } }
    var error by remember { mutableStateOf("") }

    val targetName = contactLabel(contacts, target)
    val h = hourText.toIntOrNull()
    val m = minuteText.toIntOrNull()
    // 시간을 입력하는 즉시 상대의 거절 시간과 비교
    val conflicts = if (h != null && m != null && h in 0..23 && m in 0..59) {
        Quiet.conflicts(rulesOf(target), days.toList(), h, m)
    } else {
        emptyMap()
    }
    val totalDays = if (days.isEmpty()) 7 else days.size
    val fullyBlocked = conflicts.isNotEmpty() && conflicts.size >= totalDays

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(if (initial.id.isBlank()) "새 알람" else "알람 수정") {
            TextButton(onClick = onCancel) { Text("취소", color = Palette.Muted) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            // 시간 (큰 디지털 표시)
            AppCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimeField(hourText, "시") { hourText = it }
                        Text(
                            ":",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Orange,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TimeField(minuteText, "분") { minuteText = it }
                    }
                    Text("받는 사람 폰 시간 · 24시간제", fontSize = 11.sp, color = Palette.Muted)
                }
            }
            Spacer(Modifier.height(14.dp))

            SectionHeader("울림 방식")
            SoundModePicker(mode = soundMode, onChange = { soundMode = it })

            SectionHeader("누구에게")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                contacts.forEach { c ->
                    FilterChip(
                        selected = c.phone == target,
                        onClick = { if (initial.id.isBlank()) target = c.phone },
                        enabled = initial.id.isBlank() || c.phone == target,
                        label = { Text(c.name.ifBlank { c.phone }) }
                    )
                }
            }
            val targetRules = rulesOf(target)
            if (targetRules.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${targetName}의 거절 시간\n" + targetRules.joinToString("\n") { "  " + Quiet.label(it) },
                    fontSize = 12.sp,
                    color = Palette.Muted
                )
            }

            SectionHeader("요일 · 없으면 매일")
            DayChips(selected = days, onChange = { days = it })

            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Palette.DangerDim),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        conflictText(targetName, days.toList(), conflicts),
                        fontSize = 13.sp,
                        color = Palette.Danger,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            SectionHeader("읽어줄 말")
            MessagePicker(targetName = targetName, text = text, onText = { text = it })
            Text(
                "울릴 때 이 문장을 목소리로 반복해요",
                fontSize = 11.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )

            SectionHeader("반복")
            Text("${repeatCount.toInt()}회 울림", fontSize = 14.sp, color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Slider(value = repeatCount, onValueChange = { repeatCount = it }, valueRange = 1f..10f, steps = 8)
            Text("${intervalMin.toInt()}분 간격", fontSize = 14.sp, color = Palette.Text, fontWeight = FontWeight.SemiBold)
            Slider(value = intervalMin, onValueChange = { intervalMin = it }, valueRange = 1f..30f, steps = 28)

            SectionHeader("끄기 질문 · 최대 10개")
            Text("정답을 맞혀야 그날 알람이 꺼져요", fontSize = 12.sp, color = Palette.Muted)
            Spacer(Modifier.height(6.dp))
            questions.forEachIndexed { i, q ->
                AppCard(Modifier.padding(vertical = 4.dp)) {
                    Column {
                        OutlinedTextField(
                            value = q.q,
                            onValueChange = { questions[i] = q.copy(q = it) },
                            label = { Text("질문 ${i + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = q.a,
                                onValueChange = { questions[i] = q.copy(a = it) },
                                label = { Text("정답") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            TextButton(onClick = { questions.removeAt(i) }) { Text("삭제", color = Palette.Danger) }
                        }
                    }
                }
            }
            if (questions.size < 10) {
                OutlinedButton(onClick = { questions.add(Question()) }) { Text("＋ 질문") }
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Palette.Danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    when {
                        target.isBlank() -> error = "받을 사람을 선택하세요"
                        h == null || h !in 0..23 -> error = "시는 0~23"
                        m == null || m !in 0..59 -> error = "분은 0~59"
                        text.isBlank() -> error = "읽어줄 말을 입력하세요"
                        questions.any { it.q.isNotBlank() && it.a.isBlank() } -> error = "정답이 빈 질문이 있어요"
                        fullyBlocked -> error = "${targetName}의 거절 시간이에요. 시간이나 요일을 바꿔 주세요"
                        else -> onSave(
                            initial.copy(
                                targetPhone = target,
                                targetName = targetName,
                                hour = h,
                                minute = m,
                                text = text.trim(),
                                days = days.sorted(),
                                repeatCount = repeatCount.toInt(),
                                intervalMin = intervalMin.toInt(),
                                questions = questions.filter { it.q.isNotBlank() },
                                soundMode = soundMode
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

/**
 * 자주 쓰는 문장 선택 (받는 사람 이름 반영, 아/야 자동) + "기타"를 고르면 직접 입력.
 * 기존 문장이 목록에 없으면 처음부터 기타(입력창)로 연다.
 */
@Composable
private fun MessagePicker(targetName: String, text: String, onText: (String) -> Unit) {
    val presets = alarmPresets(targetName)
    var custom by remember(targetName) { mutableStateOf(text.isNotBlank() && text !in presets) }
    Column {
        presets.forEach { p ->
            FilterChip(
                selected = !custom && text == p,
                onClick = {
                    custom = false
                    onText(p)
                },
                label = { Text(p, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        FilterChip(
            selected = custom,
            onClick = {
                custom = true
                if (text in presets) onText("")
            },
            label = { Text("✏️ 기타 (직접 입력)", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth()
        )
        if (custom) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text("${vocative(targetName)} 일어나! 학교 가야지") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SoundModePill(mode: String) {
    if (mode == SoundMode.FOLLOW) {
        Pill("📱 폰 설정", Palette.Surface2, Palette.Muted)
    } else {
        Pill("🔊 무조건", Palette.DangerDim, Palette.Warn)
    }
}

/** 울림 방식 선택: 무조건 소리(기본, 강조) / 폰 설정 따름 */
@Composable
private fun SoundModePicker(mode: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundModeOption(
            selected = mode != SoundMode.FOLLOW,
            title = "🔊 무조건 소리",
            desc = "무음·진동 모드여도 알람 볼륨 최대로 읽어줘요 · 기본 추천",
            accent = Palette.Orange,
            onClick = { onChange(SoundMode.FORCE) }
        )
        SoundModeOption(
            selected = mode == SoundMode.FOLLOW,
            title = "📱 폰 설정 따름",
            desc = "소리 모드면 소리, 진동 모드면 진동만, 무음이면 화면만",
            accent = Palette.Teal,
            onClick = { onChange(SoundMode.FOLLOW) }
        )
    }
}

@Composable
private fun SoundModeOption(
    selected: Boolean,
    title: String,
    desc: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) accent else Palette.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Palette.Bg else Palette.Text
                )
                Text(desc, fontSize = 12.sp, color = if (selected) Palette.Bg.copy(alpha = 0.8f) else Palette.Muted)
            }
            Text(if (selected) "●" else "○", color = if (selected) Palette.Bg else Palette.Muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun TimeField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(2)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Text,
            textAlign = TextAlign.Center
        ),
        modifier = Modifier.width(110.dp),
        singleLine = true
    )
}
