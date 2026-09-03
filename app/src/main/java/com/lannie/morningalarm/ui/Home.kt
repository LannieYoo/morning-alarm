package com.lannie.morningalarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lannie.morningalarm.data.Contact
import com.lannie.morningalarm.data.PairRequest
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.health.Health

/** 홈: 알람 / 기록 / 메시지 / 연결·상태. 역할 구분 없이 누구나 같은 화면. */
@Composable
fun Home(prefs: Prefs) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var contacts by remember { mutableStateOf(prefs.getContacts()) }
    var incoming by remember { mutableStateOf(listOf<PairRequest>()) }
    var health by remember { mutableStateOf(Health.check(context)) }

    // 상대별 users/{phone} 문서 (헬스체크 + 알람 거절 시간)
    val peerData = remember { mutableStateMapOf<String, Map<String, Any>>() }

    DisposableEffect(Unit) {
        val reg = Repo.listenContacts(prefs.myPhone) { list ->
            contacts = list
            prefs.saveContacts(list)
        }
        onDispose { reg.remove() }
    }
    DisposableEffect(Unit) {
        val reg = Repo.listenPairRequestsTo(prefs.myPhone) { incoming = it }
        onDispose { reg.remove() }
    }
    DisposableEffect(contacts) {
        val regs = contacts.map { c ->
            Repo.listenUser(c.phone) { data -> peerData[c.phone] = data ?: emptyMap() }
        }
        onDispose { regs.forEach { runCatching { it.remove() } } }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(8.dp))
        if (!health.allOk) {
            WarnBanner("⚠️ 지금 설정으로는 알람이 안 울릴 수 있어요 — [연결·상태] 탭에서 해결하세요") { tab = 3 }
        }
        if (incoming.isNotEmpty()) {
            WarnBanner("📨 연결 요청 ${incoming.size}건 — [연결·상태] 탭에서 수락하세요") { tab = 3 }
        }

        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> AlarmsTab(prefs, contacts, peerData)
                1 -> LogTab(prefs)
                2 -> ChatTab(prefs, contacts)
                3 -> ContactsTab(
                    prefs = prefs,
                    contacts = contacts,
                    incoming = incoming,
                    peerData = peerData,
                    health = health,
                    onRefreshHealth = { health = Health.check(context) }
                )
            }
        }
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("⏰") }, label = { Text("알람") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📋") }, label = { Text("기록") })
            NavigationBarItem(selected = tab == 2, onClick = {
                tab = 2
            }, icon = { Text("💬") }, label = { Text("메시지") })
            NavigationBarItem(
                selected = tab == 3,
                onClick = {
                    tab = 3
                    health = Health.check(context)
                },
                icon = {
                    if (incoming.isEmpty()) {
                        Text("👥")
                    } else {
                        BadgedBox(badge = { Badge { Text("${incoming.size}") } }) { Text("👥") }
                    }
                },
                label = { Text("연결·상태") }
            )
        }
    }
}

/** 상대의 users 문서에서 헬스체크가 전부 OK인지 */
fun healthAllOk(h: Map<String, Any>?): Boolean {
    if (h == null) return true
    val keys = listOf("notifOk", "exactOk", "batteryOk", "fullscreenOk", "dndOk")
    return keys.all { (h[it] as? Boolean) != false }
}

fun contactLabel(contacts: List<Contact>, phone: String): String =
    contacts.firstOrNull { it.phone == phone }?.name?.ifBlank { null } ?: phone
