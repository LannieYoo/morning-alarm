package com.lannie.morningalarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lannie.morningalarm.data.Contact
import com.lannie.morningalarm.data.Message
import com.lannie.morningalarm.data.PairRequest
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.health.Health

/** 홈: 알람 / 기록 / 메시지 / 연결. 역할 구분 없이 누구나 같은 화면. */
@Composable
fun Home(prefs: Prefs) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var contacts by remember { mutableStateOf(prefs.getContacts()) }
    var incoming by remember { mutableStateOf(listOf<PairRequest>()) }
    var unread by remember { mutableStateOf(listOf<Message>()) }
    var health by remember { mutableStateOf(Health.check(context)) }

    // 상대별 users/{phone} 문서 (헬스체크 + 알람 거절 시간)
    val peerData = remember { mutableStateMapOf<String, Map<String, Any>>() }

    fun refreshHealth() {
        health = Health.check(context)
        // 상대 화면의 "설정 필요" 표시가 바로 갱신되도록 즉시 올린다
        if (prefs.myPhone.isNotBlank()) runCatching { Repo.updateHealth(prefs.myPhone, health.toMap()) }
    }

    // 설정 화면에서 돌아올 때마다 다시 검사
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshHealth()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    DisposableEffect(Unit) {
        val reg = Repo.listenUnread(prefs.myPhone) { unread = it }
        onDispose { reg.remove() }
    }
    DisposableEffect(contacts) {
        val regs = contacts.map { c ->
            Repo.listenUser(c.phone) { data -> peerData[c.phone] = data ?: emptyMap() }
        }
        onDispose { regs.forEach { runCatching { it.remove() } } }
    }

    val incomingPhones = incoming.map { it.fromPhone }.distinct()
    val unreadByPhone = unread.groupBy { it.fromPhone }.mapValues { it.value.size }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(4.dp))
        if (!health.allOk) WarnBanner("⚠️ 설정 필요: " + health.missing.joinToString(" · ")) { tab = 3 }
        if (incomingPhones.isNotEmpty()) WarnBanner("📨 연결 요청 ${incomingPhones.size}건") { tab = 3 }

        Column(Modifier.weight(1f)) {
            when (tab) {
                0 -> AlarmsTab(prefs, contacts, peerData)
                1 -> LogTab(prefs)
                2 -> ChatTab(prefs, contacts, unreadByPhone, peerData)
                3 -> ContactsTab(
                    prefs = prefs,
                    contacts = contacts,
                    incoming = incoming,
                    peerData = peerData,
                    health = health,
                    onRefreshHealth = { refreshHealth() }
                )
            }
        }

        NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
            NavItem("알람", Icons.Filled.Notifications, tab == 0) { tab = 0 }
            NavItem("기록", Icons.Filled.DateRange, tab == 1) { tab = 1 }
            NavItem("메시지", Icons.Filled.Email, tab == 2, badge = unread.size) { tab = 2 }
            NavItem("연결", Icons.Filled.Person, tab == 3, badge = incomingPhones.size) {
                tab = 3
                refreshHealth()
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: ImageVector, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (badge > 0) {
                BadgedBox(
                    badge = { Badge(containerColor = Palette.Danger, contentColor = Palette.Text) { Text("$badge") } }
                ) { Icon(icon, contentDescription = label) }
            } else {
                Icon(icon, contentDescription = label)
            }
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Palette.Bg,
            selectedTextColor = Palette.Orange,
            indicatorColor = Palette.Orange,
            unselectedIconColor = Palette.Muted,
            unselectedTextColor = Palette.Muted
        )
    )
}

/** 상대의 users 문서에서 헬스체크가 전부 OK인지 */
fun healthAllOk(h: Map<String, Any>?): Boolean = Health.missingFrom(h).isEmpty()

fun contactLabel(contacts: List<Contact>, phone: String): String =
    contacts.firstOrNull { it.phone == phone }?.name?.ifBlank { null } ?: phone
