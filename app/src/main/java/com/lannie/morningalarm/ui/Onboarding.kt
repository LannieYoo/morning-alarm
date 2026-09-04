@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lannie.morningalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.util.normalizePhone
import kotlinx.coroutines.launch

/** 첫 실행: 이름 + 내 번호. 상대 번호는 선택(적으면 바로 연결 요청). */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var myCc by remember { mutableStateOf("82") }
    var myPhone by remember { mutableStateOf("") }
    var peerCc by remember { mutableStateOf("82") }
    var peerPhone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "MORNING CALL",
            color = Palette.Orange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("⏰ 모닝콜", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
        Text("서로 깨워주는 가족 알람", fontSize = 15.sp, color = Palette.Muted)
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("이름 또는 애칭") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(20.dp))

        Label("내 전화번호")
        CountryRow(cc = myCc, onCc = { myCc = it })
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = myPhone,
            onValueChange = { myPhone = it },
            placeholder = { Text("01012345678") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(20.dp))

        Label("연결할 상대 (선택)")
        CountryRow(cc = peerCc, onCc = { peerCc = it })
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = peerPhone,
            onValueChange = { peerPhone = it },
            placeholder = { Text("상대 전화번호") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Palette.Danger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                error = ""
                if (name.isBlank()) {
                    error = "이름을 입력하세요"
                    return@Button
                }
                if (myPhone.isBlank()) {
                    error = "전화번호를 입력하세요"
                    return@Button
                }
                busy = true
                scope.launch {
                    try {
                        Repo.ensureAuth()
                        val prefs = Prefs(context)
                        prefs.myName = name.trim()
                        prefs.myPhone = normalizePhone(myCc, myPhone)
                        Repo.updateProfile(prefs.myPhone, prefs.myName, prefs.myAvatar)
                        if (peerPhone.isNotBlank()) {
                            val peer = normalizePhone(peerCc, peerPhone)
                            if (peer != prefs.myPhone) Repo.sendPairRequest(prefs.myPhone, prefs.myName, peer)
                        }
                        prefs.onboarded = true
                        onDone()
                    } catch (e: Exception) {
                        error = "연결 실패 — 인터넷을 확인하세요"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text(if (busy) "연결 중…" else "시작하기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(14.dp))
        Text("전화번호가 ID예요 · 비밀번호 없음", fontSize = 12.sp, color = Palette.Muted)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = Palette.Muted,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )
}
