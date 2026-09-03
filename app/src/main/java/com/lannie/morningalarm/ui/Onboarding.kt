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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lannie.morningalarm.data.Prefs
import com.lannie.morningalarm.data.Repo
import com.lannie.morningalarm.util.normalizePhone
import kotlinx.coroutines.launch

/**
 * 첫 실행 화면. 이름과 내 전화번호만 있으면 시작한다 (역할 구분 없음 — 누구나 보내고 받는다).
 * 연결할 상대 번호를 함께 적으면 바로 연결 요청을 보낸다. 나중에 [연결] 탭에서 더 추가할 수 있다.
 */
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
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("⏰ 모닝콜", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("멀리 있어도 서로 아침을 깨워주는 가족 알람", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("내 이름 또는 애칭") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        Text("내 전화번호", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        CountryRow(cc = myCc, onCc = { myCc = it })
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = myPhone,
            onValueChange = { myPhone = it },
            label = { Text("전화번호 (예: 01012345678)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(20.dp))

        Text("연결할 상대 전화번호 (선택)", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        CountryRow(cc = peerCc, onCc = { peerCc = it })
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = peerPhone,
            onValueChange = { peerPhone = it },
            label = { Text("상대 전화번호") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Color(0xFFC62828), fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
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
                        Repo.upsertUser(
                            prefs.myPhone,
                            mapOf("phone" to prefs.myPhone, "name" to prefs.myName)
                        )
                        if (peerPhone.isNotBlank()) {
                            val peer = normalizePhone(peerCc, peerPhone)
                            if (peer != prefs.myPhone) Repo.sendPairRequest(prefs.myPhone, prefs.myName, peer)
                        }
                        prefs.onboarded = true
                        onDone()
                    } catch (e: Exception) {
                        error = "연결 실패 — 인터넷을 확인하세요 (${e.message ?: ""})"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (busy) "연결 중…" else "시작하기", fontSize = 17.sp) }

        Spacer(Modifier.height(12.dp))
        Text(
            "· 전화번호가 곧 ID예요. 비밀번호는 없어요.\n" +
                "· 상대가 요청을 수락하면 서로 알람·메시지를 보낼 수 있어요.\n" +
                "· 여러 명과 연결할 수 있어요.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
