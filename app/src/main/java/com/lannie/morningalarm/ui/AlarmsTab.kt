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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Alarm
import com.lannie.morningalarm.data.Contact
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Question
import com.lannie.morningalarm.data.QuietRule
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.util.Quiet

/** 알람 탭: 내가 보낸 알람(편집 가능) + 나에게 오는 알람(조회) */
@Composable
fun AlarmsTab(prefs: Prefs, contacts: List<Contact>, peerData: Map<String, Map<String, Any>>) {
    var sent by remember { mutableStateOf(listOf<Alarm>()) }
    var received by remember { mutableStateOf(prefs.getAlarms()) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showEditor by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Button(
            onClick = {
                editing = null
                showEditor = true
            },
            enabled = contacts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("＋ 새 알람 만들기") }
        Spacer(Modifier.height(4.dp))
        Text(
            if (contacts.isEmpty()) {
                "먼저 [연결·상태] 탭에서 상대와 연결하세요"
            } else {
                "알람 시각은 받는 사람 폰의 현지 시간 기준으로 울립니다"
            },
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionTitle("내가 보낸 알람 (${sent.size})") }
            if (sent.isEmpty()) {
                item { Text("아직 보낸 알람이 없어요", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
            }
            items(sent, key = { "s" + it.id }) { alarm ->
                val conflicts = Quiet.conflicts(rulesOf(alarm.targetPhone), alarm.days, alarm.hour, alarm.minute)
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
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle("나에게 오는 알람 (${received.size})")
            }
            if (received.isEmpty()) {
                item { Text("아직 받은 알람이 없어요", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
            }
            items(received, key = { "r" + it.id }) { alarm ->
                ReceivedAlarmCard(
                    alarm,
                    ownerName = alarm.ownerName.ifBlank {
                        contactLabel(contacts, alarm.ownerPhone)
                    }
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
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
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                "→ $targetName",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${two(alarm.hour)}:${two(alarm.minute)}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(daysLabel(alarm.days), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${alarm.repeatCount}회 울림 · ${alarm.intervalMin}분 간격 · 질문 ${alarm.questions.size}개",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
            Text(alarm.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(conflictText(targetName, alarm.days, conflicts), fontSize = 12.sp, color = Color(0xFFC62828))
            }
            Row {
                TextButton(onClick = onEdit) { Text("수정") }
                TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFC62828)) }
            }
        }
    }
}

@Composable
private fun ReceivedAlarmCard(alarm: Alarm, ownerName: String) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("← $ownerName", fontSize = 12.sp, color = Color(0xFF6A1B9A), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${two(alarm.hour)}:${two(alarm.minute)}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.enabled) Color(0xFF6A1B9A) else Color.Gray
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(daysLabel(alarm.days), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${alarm.repeatCount}회 울림 · ${alarm.intervalMin}분 간격" +
                            if (alarm.questions.isNotEmpty()) " · 끄려면 질문 정답 필요" else "",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Text(alarm.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Text(
                if (alarm.enabled) "보낸 사람만 수정·삭제할 수 있어요" else "(꺼져 있음)",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * 거절 시간 충돌 안내문.
 * 예) "⛔ 유진의 📚 수업시간 · 월~금 09:00~15:00 — 월·화·수·목·금에는 거절돼요"
 */
fun conflictText(targetName: String, alarmDays: List<Int>, conflicts: Map<Int, QuietRule>): String {
    if (conflicts.isEmpty()) return ""
    val totalDays = if (alarmDays.isEmpty()) 7 else alarmDays.distinct().size
    val byRule = conflicts.entries.groupBy({ it.value }, { it.key })
    val parts = byRule.entries.joinToString("\n") { (rule, days) ->
        "⛔ ${targetName}의 ${Quiet.label(rule)} — ${Quiet.daysText(days.sorted())}에는 거절돼요"
    }
    return if (conflicts.size >= totalDays) "$parts\n이 시간에는 알람이 전부 거절돼요. 시간을 바꿔 주세요." else parts
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
    var hourText by remember { mutableStateOf(initial.hour.toString()) }
    var minuteText by remember { mutableStateOf(initial.minute.toString()) }
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(
            if (initial.id.isBlank()) "새 알람" else "알람 수정",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        Text("누구에게", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            contacts.forEach { c ->
                FilterChip(
                    selected = c.phone == target,
                    onClick = { if (initial.id.isBlank()) target = c.phone },
                    enabled = initial.id.isBlank() || c.phone == target,
                    label = { Text(c.name.ifBlank { c.phone }) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        val targetRules = rulesOf(target)
        if (targetRules.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${targetName}의 알람 거절 시간:\n" + targetRules.joinToString("\n") { "  " + Quiet.label(it) },
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Spacer(Modifier.height(16.dp))

        Text("시간 (받는 사람 폰 기준, 24시간제)", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hourText,
                onValueChange = { hourText = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("시") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp),
                singleLine = true
            )
            Text("  :  ", fontSize = 24.sp)
            OutlinedTextField(
                value = minuteText,
                onValueChange = { minuteText = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("분") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(90.dp),
                singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))

        Text("반복 요일 (선택 없으면 매일)", fontWeight = FontWeight.SemiBold)
        DayChips(selected = days, onChange = { days = it })

        if (conflicts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Text(
                    conflictText(targetName, days.toList(), conflicts),
                    fontSize = 13.sp,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("알람이 울릴 때 읽어줄 말 (예: 유진아 일어나! 학교 가야지)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("이 문장을 목소리로 반복해서 읽어줘요 (무음이어도 알람 볼륨으로)", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        Text("울림 횟수: ${repeatCount.toInt()}회 (끄지 않으면 반복)", fontWeight = FontWeight.SemiBold)
        Slider(value = repeatCount, onValueChange = { repeatCount = it }, valueRange = 1f..10f, steps = 8)

        Text("반복 간격: ${intervalMin.toInt()}분", fontWeight = FontWeight.SemiBold)
        Slider(value = intervalMin, onValueChange = { intervalMin = it }, valueRange = 1f..30f, steps = 28)
        Spacer(Modifier.height(16.dp))

        Text("그만 울리기 질문 (최대 10개)", fontWeight = FontWeight.SemiBold)
        Text(
            "질문을 넣으면 받는 사람이 정답을 맞혀야 그날 알람이 꺼져요.\n예) Q: 우리집 첫 강아지 이름은? A: 미미",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        questions.forEachIndexed { i, q ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
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
                        TextButton(onClick = { questions.removeAt(i) }) { Text("삭제") }
                    }
                }
            }
        }
        if (questions.size < 10) {
            OutlinedButton(onClick = { questions.add(Question()) }) { Text("＋ 질문 추가") }
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFC62828), fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row {
            Button(
                onClick = {
                    when {
                        target.isBlank() -> error = "받을 사람을 선택하세요"
                        h == null || h !in 0..23 -> error = "시는 0~23 사이로 입력하세요"
                        m == null || m !in 0..59 -> error = "분은 0~59 사이로 입력하세요"
                        text.isBlank() -> error = "알람에서 읽어줄 말을 입력하세요"
                        questions.any { it.q.isNotBlank() && it.a.isBlank() } -> error = "정답이 비어 있는 질문이 있어요"
                        fullyBlocked -> error = "이 시간에는 ${targetName}이(가) 알람을 거절해요. 시간이나 요일을 바꿔 주세요"
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
                                questions = questions.filter { it.q.isNotBlank() }
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
