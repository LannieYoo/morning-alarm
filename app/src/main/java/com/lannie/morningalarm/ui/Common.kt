package com.lannie.morningalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

// ---------------- 공통 UI 조각 ----------------

/** 화면 상단 제목 */
@Composable
fun ScreenTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 섹션 제목 (작은 회색 대문자 느낌) */
@Composable
fun SectionHeader(text: String, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Muted, letterSpacing = 1.sp)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Pill(count.toString(), Palette.Surface2, Palette.Muted)
        }
    }
}

/** 카드 박스 (모서리 16dp, 남색 표면) */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface)
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/** 작은 라벨 알약 */
@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/** 아무것도 없을 때 화면 가운데 크게 */
@Composable
fun EmptyState(icon: String, title: String, hint: String = "") {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                textAlign = TextAlign.Center
            )
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(hint, fontSize = 13.sp, color = Palette.Muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 상단 경고 배너 */
@Composable
fun WarnBanner(text: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.OrangeDim)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = Palette.Warn,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text("›", color = Palette.Warn, fontSize = 18.sp)
    }
}

/** 국가 선택: 국기(테두리 없음) + 작은 국가번호 칸 */
@Composable
fun CountryRow(cc: String, onCc: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Flag("🇰🇷", selected = cc == "82") { onCc("82") }
        Spacer(Modifier.width(6.dp))
        Flag("🇨🇦", selected = cc == "1") { onCc("1") }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Palette.Surface)
                .height(40.dp)
                .width(84.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+", color = Palette.Muted, fontSize = 15.sp)
            BasicTextField(
                value = cc,
                onValueChange = { onCc(it.filter { ch -> ch.isDigit() }.take(4)) },
                singleLine = true,
                textStyle = TextStyle(color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Palette.Orange),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Flag(flag: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .alpha(if (selected) 1f else 0.35f)
    ) {
        Text(flag, fontSize = 30.sp)
        Box(
            Modifier
                .height(3.dp)
                .width(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Palette.Orange else Color.Transparent)
        )
    }
}

/** 요일 선택 칩 (선택 없음 = 매일) */
@Composable
fun DayChips(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DAY_NAMES.forEachIndexed { i, label ->
            val day = i + 1
            FilterChip(
                selected = selected.contains(day),
                onClick = { onChange(if (selected.contains(day)) selected - day else selected + day) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}
