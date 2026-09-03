package com.lannie.morningalarm.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

/** 로컬 설정 + 캐시(연락처, 받은 알람, 거절 시간 — 오프라인/재부팅 대비) */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("morning_alarm", Context.MODE_PRIVATE)
    private val gson = Gson()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var myName: String
        get() = sp.getString("myName", "") ?: ""
        set(v) = sp.edit().putString("myName", v).apply()

    /** 정규화된 내 번호 (+8210..., +1613...) */
    var myPhone: String
        get() = sp.getString("myPhone", "") ?: ""
        set(v) = sp.edit().putString("myPhone", v).apply()

    /** 표시 시간대: Asia/Seoul(기본) 또는 "local"(기기 시간대) */
    var displayTz: String
        get() = sp.getString("displayTz", "Asia/Seoul") ?: "Asia/Seoul"
        set(v) = sp.edit().putString("displayTz", v).apply()

    /** 마지막으로 채팅한 상대 (메시지 탭 기본 선택) */
    var lastChatPhone: String
        get() = sp.getString("lastChatPhone", "") ?: ""
        set(v) = sp.edit().putString("lastChatPhone", v).apply()

    // ---- 연락처 (수락된 연결) ----

    fun saveContacts(list: List<Contact>) {
        sp.edit().putString("contacts", gson.toJson(list)).apply()
    }

    fun getContacts(): List<Contact> = readList("contacts", object : TypeToken<List<Contact>>() {})

    fun contactName(phone: String): String =
        getContacts().firstOrNull { it.phone == phone }?.name?.ifBlank { null } ?: phone

    // ---- 나에게 온 알람 캐시 ----

    fun saveAlarms(alarms: List<Alarm>) {
        sp.edit().putString("alarms", gson.toJson(alarms)).apply()
    }

    fun getAlarms(): List<Alarm> = readList("alarms", object : TypeToken<List<Alarm>>() {})

    // ---- 내 알람 거절 시간 ----

    fun saveQuietRules(rules: List<QuietRule>) {
        sp.edit().putString("quietRules", gson.toJson(rules)).apply()
    }

    fun getQuietRules(): List<QuietRule> = readList("quietRules", object : TypeToken<List<QuietRule>>() {})

    // ---- 오늘 하루 그만 울리기 플래그 ----

    fun stopForToday(alarmId: String) {
        sp.edit().putString("stopped_$alarmId", LocalDate.now().toString()).apply()
    }

    fun isStoppedToday(alarmId: String): Boolean = sp.getString("stopped_$alarmId", null) == LocalDate.now().toString()

    /**
     * 예전 버전(엄마/자녀 역할 고정) 데이터 이전: 연결돼 있던 상대를 연락처로 옮긴다.
     * 한 번만 실행되고 이후에는 아무것도 하지 않는다.
     */
    fun migrateLegacyPair() {
        if (sp.getBoolean("migratedPair", false)) return
        val peer = sp.getString("peerPhone", "") ?: ""
        val paired = sp.getBoolean("paired", false)
        if (paired && peer.isNotBlank() && getContacts().none { it.phone == peer }) {
            val name = (sp.getString("peerName", "") ?: "").ifBlank { peer }
            saveContacts(getContacts() + Contact(peer, name))
        }
        sp.edit().putBoolean("migratedPair", true).apply()
    }

    private fun <T> readList(key: String, token: TypeToken<List<T>>): List<T> {
        val json = sp.getString(key, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<T>>(json, token.type) }.getOrNull() ?: emptyList()
    }
}
