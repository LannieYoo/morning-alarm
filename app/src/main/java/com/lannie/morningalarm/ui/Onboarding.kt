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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
 * 첫 실행 화면.
 * 역할(엄마=알람 보내는 사람 / 자녀=알람 받는 사람)과 전화번호로 간단히 시작한다.
 * 엄마는 자녀 번호를 입력해 연결 요청을 보내고, 자녀가 수락하면 연결 완료.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") } // mom | daughter
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
        Text("멀리 있어도 아침을 깨워주는 가족 알람", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(28.dp))

        Text("내 역할", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row {
            FilterChip(
                selected = role == "mom",
                onClick = { role = "mom" },
                label = { Text("알람 보내는 사람 (엄마)") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = role == "daughter",
                onClick = { role = "daughter" },
                label = { Text("알람 받는 사람 (자녀)") }
            )
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("이름 (예: 엄마, 유진)") },
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

        if (role == "mom") {
            Spacer(Modifier.height(16.dp))
            Text("자녀 전화번호 (알람 받을 폰)", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            CountryRow(cc = peerCc, onCc = { peerCc = it })
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = peerPhone,
                onValueChange = { peerPhone = it },
                label = { Text("자녀 전화번호") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Color(0xFFC62828), fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                error = ""
                if (role.isBlank()) {
                    error = "역할을 선택하세요"
                    return@Button
                }
                if (name.isBlank()) {
                    error = "이름을 입력하세요"
                    return@Button
                }
                if (myPhone.isBlank()) {
                    error = "전화번호를 입력하세요"
                    return@Button
                }
                if (role == "mom" && peerPhone.isBlank()) {
                    error = "자녀 전화번호를 입력하세요"
                    return@Button
                }
                busy = true
                scope.launch {
                    try {
                        Repo.ensureAuth()
                        val prefs = Prefs(context)
                        prefs.role = role
                        prefs.myName = name.trim()
                        prefs.myPhone = normalizePhone(myCc, myPhone)
                        Repo.upsertUser(
                            prefs.myPhone,
                            mapOf("phone" to prefs.myPhone, "name" to prefs.myName, "role" to role)
                        )
                        if (role == "mom") {
                            prefs.peerPhone = normalizePhone(peerCc, peerPhone)
                            prefs.peerName = "자녀"
                            Repo.sendPairRequest(prefs.myPhone, prefs.myName, prefs.peerPhone)
                        } else {
                            prefs.peerName = "엄마"
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
            "· 같은 나라 번호끼리는 국가번호만 맞추면 됩니다.\n· 엄마가 보낸 연결 요청을 자녀가 수락하면 알람을 보낼 수 있어요.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun CountryRow(cc: String, onCc: (String) -> Unit) {
    Row {
        FilterChip(selected = cc == "82", onClick = { onCc("82") }, label = { Text("🇰🇷 +82 한국") })
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = cc == "1", onClick = { onCc("1") }, label = { Text("🇨🇦 +1 캐나다") })
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
