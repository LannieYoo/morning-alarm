@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Contact
import com.lannie.morningalarm.data.Kind
import com.lannie.morningalarm.data.Message
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo

/** 메시지 탭: 카톡처럼 연락처 목록이 먼저, 누르면 대화방 */
@Composable
fun ChatTab(
    prefs: Prefs,
    contacts: List<Contact>,
    unreadByPhone: Map<String, Int> = emptyMap(),
    peerData: Map<String, Map<String, Any>> = emptyMap()
) {
    var open by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(listOf<Message>()) }

    DisposableEffect(Unit) {
        val reg = Repo.listenAllMessages(prefs.myPhone) { all = it }
        onDispose { reg.remove() }
    }

    val peer = open
    if (peer != null && contacts.any { it.phone == peer }) {
        val c = contacts.first { it.phone == peer }
        ChatRoom(
            me = prefs.myPhone,
            myName = prefs.myName,
            peer = c.phone,
            peerName = c.name.ifBlank { c.phone },
            peerAvatar = Repo.avatarOf(peerData[c.phone]),
            onBack = { open = null }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("메시지")
        if (contacts.isEmpty()) {
            EmptyState("💬", "연결된 사람이 없어요", "[연결] 탭에서 번호로 요청", art = { ChatBubbleArt() })
            return
        }
        // 마지막 메시지 시각순 (대화 없는 사람은 뒤로)
        val lastByPeer = all.groupBy { if (it.fromPhone == prefs.myPhone) it.toPhone else it.fromPhone }
            .mapValues { it.value.maxByOrNull { m -> m.sentAt } }
        val ordered = contacts.sortedByDescending { lastByPeer[it.phone]?.sentAt ?: 0L }
        LazyColumn(Modifier.padding(horizontal = 12.dp)) {
            items(ordered, key = { it.phone }) { c ->
                val last = lastByPeer[c.phone]
                val unread = unreadByPhone[c.phone] ?: 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            open = c.phone
                            prefs.lastChatPhone = c.phone
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(Repo.avatarOf(peerData[c.phone]), size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name.ifBlank {
                                c.phone
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Text
                        )
                        Text(
                            last?.let { previewOf(it, mine = it.fromPhone == prefs.myPhone) } ?: "아직 대화가 없어요",
                            fontSize = 13.sp,
                            color = Palette.Muted,
                            maxLines = 1
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (last != null) Text(fmtTimeShort(last.sentAt), fontSize = 11.sp, color = Palette.Muted)
                        if (unread > 0) {
                            Spacer(Modifier.padding(2.dp))
                            Pill("$unread", Palette.Danger, Palette.Text)
                        }
                    }
                }
            }
        }
    }
}

private fun previewOf(m: Message, mine: Boolean): String {
    val prefix = when {
        m.kind == Kind.URGENT -> "🚨 "
        Kind.isAlarmLike(m.kind) -> "🔊 "
        else -> ""
    }
    return (if (mine) "나: " else "") + prefix + m.text
}

/**
 * 1:1 대화방. 긴급 체크 후 보내기 → 확인 팝업 → 상대 화면 전체가 깜빡이는 팝업으로 전달
 * (거절 시간과 상관없이 항상 전달, 소리는 상대 폰 설정을 따름).
 */
@Composable
fun ChatRoom(me: String, myName: String, peer: String, peerName: String, peerAvatar: String, onBack: () -> Unit) {
    var messages by remember(peer) { mutableStateOf(listOf<Message>()) }
    var input by remember { mutableStateOf("") }
    var urgent by remember { mutableStateOf(false) }
    var confirmUrgent by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    DisposableEffect(me, peer) {
        val reg = Repo.listenChat(me, peer) { list ->
            messages = list
            list.filter { it.toPhone == me && it.readAt == 0L }
                .forEach { runCatching { Repo.markRead(it.id) } }
        }
        onDispose { reg.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send(kind: String) {
        val text = input.trim()
        if (text.isBlank()) return
        runCatching { Repo.sendMessage(me, myName, peer, text, kind) }
        input = ""
        urgent = false
    }

    if (confirmUrgent) {
        AlertDialog(
            onDismissRequest = { confirmUrgent = false },
            containerColor = Palette.Surface,
            title = { Text("🚨 긴급 메시지가 맞나요?", color = Palette.Text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "$peerName 님의 폰 화면 전체가 깜빡이며 뜨고, 거절 시간과 상관없이 바로 전달돼요.\n소리는 상대 폰 설정(소리/진동/무음)을 따라요.",
                    color = Palette.Muted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmUrgent = false
                        send(Kind.URGENT)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.Text)
                ) { Text("긴급으로 보내기") }
            },
            dismissButton = { TextButton(onClick = { confirmUrgent = false }) { Text("취소", color = Palette.Muted) } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // 헤더
        Row(
            Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹", fontSize = 26.sp, color = Palette.Text) }
            Avatar(peerAvatar, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Text(peerName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyState("👋", "$peerName 에게 첫 메시지를 보내요") }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg = msg, mine = msg.fromPhone == me, peerName = peerName)
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (urgent) "🚨 긴급 메시지" else "메시지") },
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (urgent) confirmUrgent = true else send(Kind.CHAT) },
                    colors = if (urgent) {
                        ButtonDefaults.buttonColors(containerColor = Palette.Danger, contentColor = Palette.Text)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) { Text(if (urgent) "🚨 보내기" else "보내기") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = urgent,
                    onCheckedChange = { urgent = it },
                    colors = CheckboxDefaults.colors(checkedColor = Palette.Danger)
                )
                Text(
                    "긴급 (상대 화면 전체 팝업 · 깜빡임)",
                    color = if (urgent) Palette.Danger else Palette.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { urgent = !urgent }
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message, mine: Boolean, peerName: String) {
    val urgent = msg.kind == Kind.URGENT
    val test = Kind.isAlarmLike(msg.kind)
    val bg = when {
        urgent -> Palette.DangerDim
        mine -> Palette.Orange
        else -> Palette.Surface2
    }
    val fg = when {
        urgent -> Palette.Danger
        mine -> Palette.Bg
        else -> Palette.Text
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (!mine) Text(peerName, fontSize = 11.sp, color = Palette.Muted)
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            val prefix = if (urgent) {
                "🚨 "
            } else if (test) {
                "🔊 "
            } else {
                ""
            }
            Text(prefix + msg.text, fontSize = 15.sp, color = fg)
        }
        val status = buildString {
            append(fmtTimeShort(msg.sentAt))
            if (mine) {
                append(" · ")
                append(
                    when {
                        msg.readAt > 0L -> "읽음"
                        msg.deliveredAt > 0L -> "전달됨"
                        else -> "전송됨"
                    }
                )
            }
        }
        Text(status, fontSize = 10.sp, color = Palette.Muted)
    }
}
