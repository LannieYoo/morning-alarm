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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Alarm
import com.lannie.morningalarm.data.AlarmEvent
import com.lannie.morningalarm.data.Kind
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Question
import com.lannie.morningalarm.data.Repo

/** 엄마(발신) 홈 화면: 알람 / 기록 / 메시지 / 상태 */
@Composable
fun MomHome(prefs: Prefs) {
    var tab by remember { mutableStateOf(0) }
    var paired by remember { mutableStateOf(prefs.paired) }
    var peerHealth by remember { mutableStateOf<Map<String, Any>?>(null) }

    // 자녀가 연결 요청을 수락했는지 감시
    DisposableEffect(Unit) {
        val reg = Repo.listenPairAccepted(prefs.myPhone) { list ->
            if (list.any { it.toPhone == prefs.peerPhone }) {
                prefs.paired = true
                paired = true
            }
        }
        onDispose { reg.remove() }
    }
    // 자녀 폰 헬스체크 감시
    DisposableEffect(Unit) {
        val reg = Repo.listenUser(prefs.peerPhone) { data ->
            @Suppress("UNCHECKED_CAST")
            peerHealth = data?.get("health") as? Map<String, Any>
        }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        if (!paired) {
            WarnBanner("⏳ 자녀의 수락 대기 중 — 자녀 폰에서 앱을 열고 [상태] 탭에서 수락하면 연결됩니다")
        }
        val h = peerHealth
        if (h != null && !healthAllOk(h)) {
            WarnBanner("⚠️ 자녀 폰 설정 때문에 알람이 안 울릴 수 있어요 — [상태] 탭에서 확인하세요") { tab = 3 }
        }

        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> MomAlarmsTab(prefs)
                1 -> MomLogTab(prefs)
                2 -> ChatScreen(me = prefs.myPhone, peer = prefs.peerPhone, peerName = prefs.peerName)
                3 -> MomStatusTab(prefs, peerHealth)
            }
        }
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("⏰") }, label = { Text("알람") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📋") }, label = { Text("기록") })
            NavigationBarItem(selected = tab == 2, onClick = {
                tab = 2
            }, icon = { Text("💬") }, label = { Text("메시지") })
            NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Text("🩺") }, label = { Text("상태") })
        }
    }
}

fun healthAllOk(h: Map<String, Any>): Boolean {
    val keys = listOf("notifOk", "exactOk", "batteryOk", "fullscreenOk", "dndOk")
    return keys.all { (h[it] as? Boolean) != false }
}

// ---------------- 알람 탭 ----------------

@Composable
private fun MomAlarmsTab(prefs: Prefs) {
    var alarms by remember { mutableStateOf(listOf<Alarm>()) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val reg = Repo.listenAlarmsOwned(prefs.myPhone) { alarms = it.sortedBy { a -> a.hour * 60 + a.minute } }
        onDispose { reg.remove() }
    }

    if (showEditor) {
        AlarmEditor(
            initial = editing ?: Alarm(ownerPhone = prefs.myPhone, targetPhone = prefs.peerPhone),
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
            modifier = Modifier.fillMaxWidth()
        ) { Text("＋ 새 알람 만들기") }
        Spacer(Modifier.height(8.dp))
        Text("알람 시각은 자녀 폰(한국) 시간 기준으로 울립니다", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))

        if (alarms.isEmpty()) {
            Text("아직 알람이 없어요. 첫 알람을 만들어 보세요!", color = Color.Gray, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(alarms, key = { it.id }) { alarm ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
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
                            Switch(
                                checked = alarm.enabled,
                                onCheckedChange = { on ->
                                    runCatching { Repo.saveAlarm(alarm.copy(enabled = on)) }
                                }
                            )
                        }
                        Text(alarm.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        Row {
                            TextButton(onClick = {
                                editing = alarm
                                showEditor = true
                            }) { Text("수정") }
                            TextButton(onClick = { runCatching { Repo.deleteAlarm(alarm.id) } }) {
                                Text("삭제", color = Color(0xFFC62828))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 알람 편집 ----------------

private val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
private fun AlarmEditor(initial: Alarm, onSave: (Alarm) -> Unit, onCancel: () -> Unit) {
    var hourText by remember { mutableStateOf(initial.hour.toString()) }
    var minuteText by remember { mutableStateOf(initial.minute.toString()) }
    var text by remember { mutableStateOf(initial.text) }
    var days by remember { mutableStateOf(initial.days.toSet()) }
    var repeatCount by remember { mutableStateOf(initial.repeatCount.toFloat()) }
    var intervalMin by remember { mutableStateOf(initial.intervalMin.toFloat()) }
    val questions = remember { mutableStateListOf<Question>().apply { addAll(initial.questions) } }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(
            if (initial.id.isBlank()) "새 알람" else "알람 수정",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        Text("시간 (자녀 폰 기준, 24시간제)", fontWeight = FontWeight.SemiBold)
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
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("알람에서 읽어줄 말 (예: 유진아 일어나! 학교 가야지)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Text("반복 요일 (선택 없으면 매일)", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DAY_LABELS.forEachIndexed { i, label ->
                val day = i + 1
                FilterChip(
                    selected = days.contains(day),
                    onClick = { days = if (days.contains(day)) days - day else days + day },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("울림 횟수: ${repeatCount.toInt()}회 (끄지 않으면 반복)", fontWeight = FontWeight.SemiBold)
        Slider(value = repeatCount, onValueChange = { repeatCount = it }, valueRange = 1f..10f, steps = 8)

        Text("반복 간격: ${intervalMin.toInt()}분", fontWeight = FontWeight.SemiBold)
        Slider(value = intervalMin, onValueChange = { intervalMin = it }, valueRange = 1f..30f, steps = 28)
        Spacer(Modifier.height(16.dp))

        Text("그만 울리기 질문 (최대 10개)", fontWeight = FontWeight.SemiBold)
        Text(
            "질문을 넣으면 자녀가 정답을 맞혀야 그날 알람이 꺼져요.\n예) Q: 우리집 첫 강아지 이름은? A: 미미",
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
                    val h = hourText.toIntOrNull()
                    val m = minuteText.toIntOrNull()
                    when {
                        h == null || h !in 0..23 -> error = "시는 0~23 사이로 입력하세요"
                        m == null || m !in 0..59 -> error = "분은 0~59 사이로 입력하세요"
                        text.isBlank() -> error = "알람에서 읽어줄 말을 입력하세요"
                        questions.any { it.q.isNotBlank() && it.a.isBlank() } -> error = "정답이 비어 있는 질문이 있어요"
                        else -> onSave(
                            initial.copy(
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

// ---------------- 기록 탭 ----------------

@Composable
private fun MomLogTab(prefs: Prefs) {
    var events by remember { mutableStateOf(listOf<AlarmEvent>()) }

    DisposableEffect(Unit) {
        val reg = Repo.listenEvents(prefs.myPhone) { events = it }
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            "알람이 언제 울렸고, 자녀가 언제 반응했는지 기록이에요 (${TzState.label()} 표시)",
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
                                fmtDateTime(e.firedAt),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            if (e.type == "test") {
                                Text("테스트", fontSize = 11.sp, color = Color(0xFF1565C0))
                            } else {
                                Text("${e.ringIndex + 1}회차 울림", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        Text(e.alarmText, fontSize = 13.sp, color = Color.DarkGray)
                        val status = when {
                            e.dismissedAt == 0L -> "😴 아직 반응 없음"
                            e.stoppedForDay && e.answered -> "✅ ${fmtTimeShort(e.dismissedAt)} 정답 맞히고 오늘 알람 종료"
                            e.stoppedForDay -> "✅ ${fmtTimeShort(e.dismissedAt)} 확인함 (오늘 알람 종료)"
                            else -> "🔁 ${fmtTimeShort(e.dismissedAt)} 일단 끔 — 잠시 후 다시 울림"
                        }
                        Text(status, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ---------------- 상태 탭 ----------------

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

@Composable
private fun MomStatusTab(prefs: Prefs, peerHealth: Map<String, Any>?) {
    val context = LocalContext.current
    var testText by remember { mutableStateOf("알람 테스트예요. 잘 들리면 화면의 확인을 눌러 줘!") }
    var sentInfo by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("연결", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            if (prefs.paired) {
                "✅ 자녀와 연결됨 · ${prefs.peerPhone}"
            } else {
                "⏳ 수락 대기 중 · ${prefs.peerPhone}"
            },
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))

        TimezoneSetting(prefs)
        Spacer(Modifier.height(20.dp))

        Text("자녀 폰 상태 (헬스체크)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (peerHealth == null) {
            Text("아직 자녀 폰에서 상태 정보가 도착하지 않았어요.\n자녀 폰에서 앱을 한 번 실행하면 표시됩니다.", fontSize = 13.sp, color = Color.Gray)
        } else {
            HealthRowView("알림 권한", peerHealth["notifOk"])
            HealthRowView("정확한 알람 허용", peerHealth["exactOk"])
            HealthRowView("배터리 최적화 제외", peerHealth["batteryOk"])
            HealthRowView("전체 화면 알림", peerHealth["fullscreenOk"])
            HealthRowView("방해금지(알람 허용)", peerHealth["dndOk"])
            val vol = (peerHealth["alarmVolumePct"] as? Number)?.toInt() ?: -1
            if (vol >= 0) {
                Text(
                    (if (vol >= 50) "✅" else "⚠️") + " 알람 볼륨 $vol% (울릴 때 자동으로 최대로 올라감)",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            val updated = (peerHealth["updatedAt"] as? Number)?.toLong() ?: 0L
            if (updated > 0L) {
                Text("마지막 확인: ${fmtDateTime(updated)}", fontSize = 11.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("테스트 (전화 통화 중에도 들리는지 꼭 확인해 보세요)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            label = { Text("테스트 내용") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                runCatching { Repo.sendMessage(prefs.myPhone, prefs.peerPhone, testText, Kind.TEST_ALARM) }
                sentInfo = "🔊 테스트 알람 전송됨 — 자녀 폰에서 즉시 울립니다"
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("🔊 테스트 알람 보내기 (무음이어도 소리남)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching { Repo.sendMessage(prefs.myPhone, prefs.peerPhone, testText, Kind.URGENT) }
                sentInfo = "🚨 테스트 전면 팝업 전송됨"
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("🚨 테스트 전면 팝업 보내기") }
        if (sentInfo.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(sentInfo, fontSize = 13.sp, color = Color(0xFF2E7D32))
        }
        Spacer(Modifier.height(20.dp))

        Text("내 정보", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("${prefs.myName} · ${prefs.myPhone}", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun HealthRowView(label: String, value: Any?) {
    val ok = (value as? Boolean) != false
    Text(
        (if (ok) "✅ " else "⚠️ ") + label + (if (ok) "" else " — 자녀 폰에서 설정 필요"),
        fontSize = 13.sp,
        color = if (ok) Color.Unspecified else Color(0xFFC62828),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
