package com.lannie.morningalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 표시 시간대 설정.
 * 기본은 한국시간(Asia/Seoul). 해외에 있으면 "local"(기기 시간대)로 전환 가능.
 * 알람이 울리는 시각 자체는 항상 받는 기기의 현지 시간 기준이다.
 */
object TzState {
    const val SEOUL = "Asia/Seoul"
    const val LOCAL = "local"

    var zoneId by mutableStateOf(SEOUL)

    fun timeZone(): TimeZone = if (zoneId == LOCAL) TimeZone.getDefault() else TimeZone.getTimeZone(zoneId)

    fun label(): String = if (zoneId == LOCAL) "현지 시간" else "한국시간"
}

fun fmtDateTime(ms: Long): String = SimpleDateFormat("M/d (E) HH:mm", Locale.KOREA)
    .apply { timeZone = TzState.timeZone() }
    .format(Date(ms))

fun fmtTimeShort(ms: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA)
    .apply { timeZone = TzState.timeZone() }
    .format(Date(ms))

private val DAY_NAMES = listOf("월", "화", "수", "목", "금", "토", "일")

fun daysLabel(days: List<Int>): String = if (days.isEmpty()) {
    "매일"
} else {
    days.sorted().joinToString("·") { DAY_NAMES[it - 1] }
}

fun two(n: Int): String = n.toString().padStart(2, '0')

/** 상단 경고 배너 */
@Composable
fun WarnBanner(text: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFFF3CD))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(text, color = Color(0xFF7A5C00), fontSize = 13.sp)
    }
}

/** 국가번호 선택 (국기 버튼 + 직접 입력). 버튼 높이는 입력칸(56dp)과 맞춘다. */
@Composable
fun CountryRow(cc: String, onCc: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlagChip(flag = "🇰🇷", selected = cc == "82") { onCc("82") }
        Spacer(Modifier.width(8.dp))
        FlagChip(flag = "🇨🇦", selected = cc == "1") { onCc("1") }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = cc,
            onValueChange = { onCc(it.filter { ch -> ch.isDigit() }) },
            label = { Text("국가번호") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
            singleLine = true
        )
    }
}

@Composable
private fun FlagChip(flag: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(flag, fontSize = 26.sp) },
        modifier = Modifier.height(56.dp).width(64.dp)
    )
}

/** 요일 선택 칩 (선택 없음 = 매일) */
@Composable
fun DayChips(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        DAY_NAMES.forEachIndexed { i, label ->
            val day = i + 1
            FilterChip(
                selected = selected.contains(day),
                onClick = { onChange(if (selected.contains(day)) selected - day else selected + day) },
                label = { Text(label, fontSize = 12.sp) },
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}
