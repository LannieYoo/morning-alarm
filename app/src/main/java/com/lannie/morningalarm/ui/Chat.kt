@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lannie.morningalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Contact
import com.lannie.morningalarm.data.Kind
import com.lannie.morningalarm.data.Message
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo

/** 메시지 탭: 상단에서 상대를 고르고 그 사람과 1:1 대화 */
@Composable
fun ChatTab(prefs: Prefs, contacts: List<Contact>) {
    var peer by remember {
        mutableStateOf(
            prefs.lastChatPhone.takeIf { p -> contacts.any { it.phone == p } } ?: contacts.firstOrNull()?.phone ?: ""
        )
    }
    if (peer.isBlank() && contacts.isNotEmpty()) peer = contacts.first().phone

    Column(Modifier.fillMaxSize()) {
        if (contacts.isEmpty()) {
            Text(
                "아직 연결된 사람이 없어요. [연결] 탭에서 번호로 연결 요청을 보내세요.",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
            return
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            contacts.forEach { c ->
                FilterChip(
                    selected = c.phone == peer,
                    onClick = {
                        peer = c.phone
                        prefs.lastChatPhone = c.phone
                    },
                    label = { Text(c.name.ifBlank { c.phone }) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        val peerName = contacts.firstOrNull { it.phone == peer }?.name?.ifBlank { null } ?: peer
        ChatScreen(me = prefs.myPhone, myName = prefs.myName, peer = peer, peerName = peerName)
    }
}

/**
 * 1:1 양방향 채팅.
 * - 일반 메시지: 카톡처럼 말풍선 + 전달됨/읽음 표시
 * - 긴급 메시지: 상대 화면 전체를 덮는 빨간 팝업으로 전달 (🚨 버튼) — 거절 시간과 상관없이 항상 전달
 */
@Composable
fun ChatScreen(me: String, myName: String, peer: String, peerName: String) {
    var messages by remember(peer) { mutableStateOf(listOf<Message>()) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(me, peer) {
        val reg = Repo.listenChat(me, peer) { list ->
            messages = list
            // 내가 받은 안 읽은 메시지 → 읽음 처리
            list.filter { it.toPhone == me && it.readAt == 0L }
                .forEach { runCatching { Repo.markRead(it.id) } }
        }
        onDispose { reg.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg = msg, mine = msg.fromPhone == me, peerName = peerName)
            }
        }

        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("$peerName 에게 메시지") },
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val text = input.trim()
                    if (text.isNotBlank()) {
                        runCatching { Repo.sendMessage(me, myName, peer, text, Kind.CHAT) }
                        input = ""
                    }
                }) { Text("보내기") }
            }
            TextButton(onClick = {
                val text = input.trim()
                if (text.isNotBlank()) {
                    runCatching { Repo.sendMessage(me, myName, peer, text, Kind.URGENT) }
                    input = ""
                }
            }) {
                Text("🚨 긴급 — 상대 화면 전체 팝업으로 보내기", color = Color(0xFFD50000), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message, mine: Boolean, peerName: String) {
    val urgent = msg.kind == Kind.URGENT
    val test = msg.kind == Kind.TEST_ALARM
    val bg = when {
        urgent -> Color(0xFFFFCDD2)
        mine -> MaterialTheme.colorScheme.primaryContainer
        else -> Color(0xFFEDE7E3)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (!mine) Text(peerName, fontSize = 11.sp, color = Color.Gray)
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                (
                    if (urgent) {
                        "🚨 "
                    } else if (test) {
                        "🔊 "
                    } else {
                        ""
                    }
                    ) + msg.text,
                fontSize = 15.sp
            )
        }
        val status = buildString {
            append(fmtTimeShort(msg.sentAt))
            if (mine) {
                append(" · ")
                append(
                    when {
                        msg.readAt > 0L -> "읽음 " + fmtTimeShort(msg.readAt)
                        msg.deliveredAt > 0L -> "전달됨"
                        else -> "전송됨"
                    }
                )
            }
        }
        Text(status, fontSize = 10.sp, color = Color.Gray)
    }
}
