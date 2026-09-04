@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lannie.morningalarm.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
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
import com.lannie.morningalarm.util.anyAnswerMatches
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
    fun nameOf(phone: String): String = if (phone == prefs.myPhone) "나" else contactLabel(contacts, phone)

    if (showEditor) {
        // 최근 질문: 내가 저장했던 질문 + 지금 살아 있는 내 알람의 질문 (중복 제거, 최대 10개)
        val recent = (prefs.getRecentQuestions() + sent.flatMap { it.questions })
            .filter { it.q.isNotBlank() }
            .distinctBy { it.q.trim() }
            .take(10)
        AlarmEditor(
            prefs = prefs,
            initial = editing ?: Alarm(ownerPhone = prefs.myPhone, ownerName = prefs.myName),
            contacts = contacts,
            peerData = peerData,
            rulesOf = ::rulesOf,
            recent = recent,
            onRemoveRecent = { q -> prefs.removeRecentQuestion(q) },
            onSave = { alarm ->
                runCatching { Repo.saveAlarm(alarm) }
                prefs.addRecentQuestions(alarm.questions)
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
            peerData = peerData,
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Teal)
            ) { Text("⚡ 지금", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    editing = null
                    showEditor = true
                }
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
            contacts.isEmpty() && sent.isEmpty() && received.isEmpty() ->
                EmptyState("👥", "먼저 연결하세요", "[연결] 탭에서 번호로 요청 · 나에게 보내는 알람은 바로 가능", art = { AlarmClockArt() })
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
                            targetName = nameOf(alarm.targetPhone),
                            conflicts = conflicts,
                            onToggle = { on -> runCatching { Repo.saveAlarm(alarm.copy(enabled = on)) } },
                            onEdit = {
                                editing = alarm
                                showEditor = true
                            },
                            onDelete = { runCatching { Repo.deleteAlarm(alarm.id) } },
                            onResend = { runCatching { Repo.resendAlarm(alarm) } }
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
                            },
                            prefs = prefs
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
    onDelete: () -> Unit,
    onResend: () -> Unit
) {
    val off = alarm.cancelledByTarget
    val accent = if (alarm.isLive()) Palette.Orange else Palette.Muted
    AppCard(Modifier.alpha(if (off) 0.55f else 1f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("→ $targetName", Palette.OrangeDim, Palette.Orange)
                Spacer(Modifier.width(6.dp))
                if (off) Pill("🚫 수신인 끔", Palette.Surface2, Palette.Muted) else SoundModePill(alarm.soundMode)
                Spacer(Modifier.weight(1f))
                if (!off) Switch(checked = alarm.enabled, onCheckedChange = onToggle)
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
            if (off) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "🚫 $targetName 님이 ${if (alarm.cancelledAt > 0L) {
                        fmtDateTime(
                            alarm.cancelledAt
                        ) + "에 "
                    } else {
                        ""
                    }}이 알람을 껐어요 · 다시 보내기 전까지 울리지 않아요",
                    fontSize = 12.sp,
                    color = Palette.Muted
                )
            } else if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(conflictText(targetName, alarm.days, conflicts), fontSize = 12.sp, color = Palette.Danger)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (off) TextButton(onClick = onResend) { Text("🔁 다시 보내기", color = Palette.Orange) }
                TextButton(onClick = onEdit) { Text("수정", color = Palette.Orange) }
                TextButton(onClick = onDelete) { Text("삭제", color = Palette.Danger) }
            }
        }
    }
}

@Composable
private fun ReceivedAlarmCard(alarm: Alarm, ownerName: String, prefs: Prefs) {
    val off = alarm.cancelledByTarget
    val accent = if (alarm.isLive()) Palette.Teal else Palette.Muted
    var askOff by remember(alarm.id) { mutableStateOf(false) }
    val questions = alarm.questions.filter { it.q.isNotBlank() }

    if (askOff) {
        TurnOffDialog(
            questions = questions,
            ownerName = ownerName,
            onConfirm = { wrong ->
                runCatching { Repo.cancelAlarmByTarget(alarm, prefs.myPhone, prefs.myName, wrong) }
                askOff = false
            },
            onDismiss = { askOff = false }
        )
    }

    AppCard(Modifier.alpha(if (off) 0.55f else 1f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("← $ownerName", Palette.TealDim, Palette.Teal)
                Spacer(Modifier.width(6.dp))
                if (off) Pill("🚫 내가 껐어요", Palette.Surface2, Palette.Muted) else SoundModePill(alarm.soundMode)
                Spacer(Modifier.weight(1f))
                if (!alarm.enabled) Text("보낸 사람이 꺼둠", fontSize = 12.sp, color = Palette.Muted)
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
            if (off) {
                Spacer(Modifier.height(4.dp))
                Text("내가 껐어요 · $ownerName 님이 다시 보내면 다시 울려요", fontSize = 12.sp, color = Palette.Muted)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { askOff = true }) {
                        Text(if (questions.isEmpty()) "끄기" else "끄기 (정답 필요)", color = Palette.Danger)
                    }
                }
            }
        }
    }
}

/** 받은 알람 끄기: 질문이 있으면 정답을 맞혀야, 없으면 확인만 */
@Composable
private fun TurnOffDialog(
    questions: List<Question>,
    ownerName: String,
    onConfirm: (wrongAnswers: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var qIndex by remember {
        mutableStateOf(if (questions.isEmpty()) 0 else (System.currentTimeMillis() % questions.size).toInt())
    }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var wrongCount by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("이 알람을 끌까요?", color = Palette.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "$ownerName 님에게 '껐다'고 알려지고, 다시 보내기 전까지 울리지 않아요.",
                    color = Palette.Muted,
                    fontSize = 13.sp
                )
                if (questions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Q. " + questions[qIndex].q, color = Palette.Text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = answer,
                        onValueChange = {
                            answer = it
                            wrong = false
                        },
                        placeholder = { Text("정답") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (wrong) Text("틀렸어요, 다시!", color = Palette.Danger, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (questions.isEmpty() || anyAnswerMatches(answer, questions[qIndex].answers())) {
                        onConfirm(wrongCount)
                    } else {
                        wrong = true
                        wrongCount++
                        qIndex = (qIndex + 1) % questions.size
                        answer = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.Text)
            ) { Text(if (questions.isEmpty()) "끄기" else "정답 확인 → 끄기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = Palette.Muted) } }
    )
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
    peerData: Map<String, Map<String, Any>>,
    rulesOf: (String) -> List<QuietRule>,
    onSent: (String) -> Unit,
    onCancel: () -> Unit
) {
    var target by remember { mutableStateOf("") }
    var soundMode by remember { mutableStateOf(SoundMode.FORCE) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val targetName = if (target == prefs.myPhone) "나" else contactLabel(contacts, target)
    val quietNow = Quiet.find(rulesOf(target), ZonedDateTime.now())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("⚡ 지금 바로 알람") {
            TextButton(onClick = onCancel) { Text("취소", color = Palette.Muted) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("예약 없이 선택한 사람 폰에서 바로 울려요", fontSize = 13.sp, color = Palette.Muted)

            SectionHeader("받을 사람 · 한 명만 선택")
            RecipientPicker(
                prefs = prefs,
                contacts = contacts,
                peerData = peerData,
                selected = target,
                locked = false,
                onSelect = { target = it }
            )

            SectionHeader("울림 방식")
            SoundModePicker(mode = soundMode, onChange = { soundMode = it })

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
    prefs: Prefs,
    initial: Alarm,
    contacts: List<Contact>,
    peerData: Map<String, Map<String, Any>>,
    rulesOf: (String) -> List<QuietRule>,
    recent: List<Question>,
    onRemoveRecent: (Question) -> Unit,
    onSave: (Alarm) -> Unit,
    onCancel: () -> Unit
) {
    var showRecent by remember { mutableStateOf(false) }
    // 새 알람은 받을 사람을 반드시 직접 고른다 (기본 선택 없음)
    var target by remember { mutableStateOf(initial.targetPhone) }
    var soundMode by remember { mutableStateOf(initial.soundMode.ifBlank { SoundMode.FORCE }) }
    // 오전/오후 다이얼 (내부 값은 0~23시)
    val timeState =
        rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    var text by remember { mutableStateOf(initial.text) }
    var days by remember { mutableStateOf(initial.days.toSet()) }
    var repeatCount by remember { mutableStateOf(initial.repeatCount.toFloat()) }
    var intervalMin by remember { mutableStateOf(initial.intervalMin.toFloat()) }
    val questions = remember { mutableStateListOf<Question>().apply { addAll(initial.questions) } }
    var error by remember { mutableStateOf("") }

    val targetName = if (target == prefs.myPhone) "나" else contactLabel(contacts, target)
    val h = timeState.hour
    val m = timeState.minute
    // 시간을 고르는 즉시 상대의 거절 시간과 비교
    val conflicts = if (target.isNotBlank()) Quiet.conflicts(rulesOf(target), days.toList(), h, m) else emptyMap()
    val totalDays = if (days.isEmpty()) 7 else days.size
    val fullyBlocked = conflicts.isNotEmpty() && conflicts.size >= totalDays

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(if (initial.id.isBlank()) "새 알람" else "알람 수정") {
            TextButton(onClick = onCancel) { Text("취소", color = Palette.Muted) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            // 1) 받을 사람 (선택한 한 사람에게만 간다)
            SectionHeader("받을 사람 · 한 명만 선택")
            RecipientPicker(
                prefs = prefs,
                contacts = contacts,
                peerData = peerData,
                selected = target,
                locked = initial.id.isNotBlank(),
                onSelect = { target = it }
            )
            Spacer(Modifier.height(10.dp))

            // 2) 시간 (오전/오후 다이얼)
            SectionHeader("시간")
            AppCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(
                        state = timeState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = Palette.Surface2,
                            selectorColor = Palette.Orange,
                            clockDialSelectedContentColor = Palette.Bg,
                            clockDialUnselectedContentColor = Palette.Text,
                            periodSelectorSelectedContainerColor = Palette.Orange,
                            periodSelectorSelectedContentColor = Palette.Bg,
                            periodSelectorUnselectedContentColor = Palette.Muted,
                            timeSelectorSelectedContainerColor = Palette.OrangeDim,
                            timeSelectorSelectedContentColor = Palette.Orange,
                            timeSelectorUnselectedContainerColor = Palette.Surface2,
                            timeSelectorUnselectedContentColor = Palette.Text
                        )
                    )
                    Text(
                        "${ampm(h, m)} · 받는 사람 폰 시간 기준",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Orange
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            SectionHeader("울림 방식")
            SoundModePicker(mode = soundMode, onChange = { soundMode = it })

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
            if (recent.isNotEmpty()) {
                OutlinedButton(onClick = { showRecent = !showRecent }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showRecent) "기존 질문 닫기" else "📚 기존 질문 선택 (${recent.size})")
                }
                if (showRecent) {
                    Spacer(Modifier.height(6.dp))
                    recent.forEach { r ->
                        val already = questions.any { it.q.trim() == r.q.trim() }
                        AppCard(Modifier.padding(vertical = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Q. ${r.q}",
                                        fontSize = 13.sp,
                                        color = Palette.Text,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "A. ${r.answers().joinToString(" / ")}",
                                        fontSize = 12.sp,
                                        color = Palette.Muted
                                    )
                                }
                                TextButton(
                                    onClick = { if (!already && questions.size < 10) questions.add(r.copy()) },
                                    enabled = !already && questions.size < 10
                                ) {
                                    Text(
                                        if (already) "추가됨" else "추가",
                                        color = if (already) Palette.Muted else Palette.Orange
                                    )
                                }
                                TextButton(onClick = {
                                    onRemoveRecent(r)
                                }) { Text("삭제", color = Palette.Danger, fontSize = 12.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
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
                        Text("정답 · 하나만 맞아도 정답 (예: 1 / 일 / 하나)", fontSize = 11.sp, color = Palette.Muted)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = q.a,
                                onValueChange = { questions[i] = q.copy(a = it) },
                                label = { Text("정답 1") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = q.a2,
                                onValueChange = { questions[i] = q.copy(a2 = it) },
                                label = { Text("정답 2") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = q.a3,
                                onValueChange = { questions[i] = q.copy(a3 = it) },
                                label = { Text("정답 3") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
                        text.isBlank() -> error = "읽어줄 말을 입력하세요"
                        questions.any { it.q.isNotBlank() && it.answers().isEmpty() } -> error = "정답이 빈 질문이 있어요"
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

/** "오후 7:00" 형식 */
fun ampm(h: Int, m: Int): String {
    val period = if (h < 12) "오전" else "오후"
    val h12 = when (h % 12) {
        0 -> 12
        else -> h % 12
    }
    return "$period $h12:${two(m)}"
}

/** 받을 사람 선택: 연결된 사람 + 나. 선택한 한 명에게만 알람이 간다. */
@Composable
private fun RecipientPicker(
    prefs: Prefs,
    contacts: List<Contact>,
    peerData: Map<String, Map<String, Any>>,
    selected: String,
    locked: Boolean,
    onSelect: (String) -> Unit
) {
    val options = contacts.filter { it.active }
        .map { Triple(it.phone, it.name.ifBlank { it.phone }, Repo.avatarOf(peerData[it.phone])) } +
        Triple(prefs.myPhone, "나 (${prefs.myName})", prefs.myAvatar)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (phone, name, avatar) ->
            val sel = phone == selected
            Card(
                onClick = { if (!locked) onSelect(phone) },
                enabled = !locked || sel,
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (sel) Palette.Orange else Palette.Surface,
                    disabledContainerColor = if (sel) Palette.Orange else Palette.Surface
                )
            ) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(avatar, size = 40.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name,
                        color = if (sel) Palette.Bg else Palette.Text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
    if (selected.isBlank()) {
        Spacer(Modifier.height(4.dp))
        Text("👆 받을 사람을 선택하세요", color = Palette.Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    } else if (locked) {
        Spacer(Modifier.height(4.dp))
        Text("받을 사람은 만들 때 정해져요 · 바꾸려면 새 알람을 만드세요", color = Palette.Muted, fontSize = 11.sp)
    }
}
