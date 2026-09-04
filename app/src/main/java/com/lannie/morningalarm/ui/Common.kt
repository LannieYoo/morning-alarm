package com.lannie.morningalarm.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/** 섹션 제목 */
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

/** 아무것도 없을 때 화면 가운데 크게. art를 주면 이모지 대신 일러스트를 그린다. */
@Composable
fun EmptyState(icon: String, title: String, hint: String = "", art: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (art != null) art() else Text(icon, fontSize = 72.sp)
            Spacer(Modifier.height(18.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                textAlign = TextAlign.Center
            )
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(hint, fontSize = 14.sp, color = Palette.Muted, textAlign = TextAlign.Center)
            }
        }
    }
}

private val ArtGradient = listOf(Color(0xFFEC4899), Color(0xFFF97316), Color(0xFFF59E0B))

/** 곰귀 달린 알람시계 일러스트 (분홍→주황 그라데이션) */
@Composable
fun AlarmClockArt(size: Dp = 170.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val c = center
        val r = w * 0.34f
        val stroke = w * 0.15f
        val brush = Brush.linearGradient(ArtGradient, start = Offset(0f, 0f), end = Offset(w, w))
        // 귀
        drawCircle(brush, radius = w * 0.115f, center = Offset(c.x - r * 0.8f, c.y - r * 0.85f))
        drawCircle(brush, radius = w * 0.115f, center = Offset(c.x + r * 0.8f, c.y - r * 0.85f))
        // 테두리 링
        drawCircle(brush, radius = r, center = c, style = Stroke(stroke))
        // 안쪽
        drawCircle(Color(0xFFF8FAFC), radius = r - stroke / 2 + 1f, center = c)
        // 바늘
        drawLine(brush, start = c, end = Offset(c.x, c.y - r * 0.55f), strokeWidth = w * 0.075f, cap = StrokeCap.Round)
        drawLine(
            brush,
            start = c,
            end = Offset(c.x + r * 0.42f, c.y + r * 0.28f),
            strokeWidth = w * 0.075f,
            cap = StrokeCap.Round
        )
        drawCircle(brush, radius = w * 0.045f, center = c)
    }
}

/** 말풍선 일러스트 */
@Composable
fun ChatBubbleArt(size: Dp = 160.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val brush = Brush.linearGradient(ArtGradient, start = Offset(0f, 0f), end = Offset(w, w))
        val bubble = Size(w * 0.86f, w * 0.6f)
        val left = w * 0.07f
        val top = w * 0.12f
        drawRoundRect(brush, topLeft = Offset(left, top), size = bubble, cornerRadius = CornerRadius(w * 0.16f))
        val tail = Path().apply {
            moveTo(left + w * 0.2f, top + bubble.height - 2f)
            lineTo(left + w * 0.12f, top + bubble.height + w * 0.16f)
            lineTo(left + w * 0.38f, top + bubble.height - 2f)
            close()
        }
        drawPath(tail, brush)
        val dotY = top + bubble.height / 2
        for (i in 0..2) {
            drawCircle(
                Color(0xFFF8FAFC),
                radius = w * 0.05f,
                center = Offset(left + bubble.width * (0.3f + 0.2f * i), dotY)
            )
        }
    }
}

/** 프로필 아이콘 (이모지, 동그란 배경) */
@Composable
fun Avatar(emoji: String, size: Dp = 44.dp, selected: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Palette.Orange else Palette.Surface2),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** 아이콘 고르기 격자 (6열). labels가 있으면 아이콘 아래 이름표를 붙인다. */
@Composable
fun AvatarPicker(
    options: List<String>,
    selected: String,
    labels: Map<String, String> = emptyMap(),
    onPick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { e ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(46.dp).clickable { onPick(e) }
                    ) {
                        Avatar(e, size = 46.dp, selected = e == selected)
                        labels[e]?.let {
                            Text(
                                it,
                                fontSize = 10.sp,
                                color = if (e == selected) Palette.Orange else Palette.Muted,
                                maxLines = 1
                            )
                        }
                    }
                }
                repeat(6 - row.size) { Spacer(Modifier.size(46.dp)) }
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
