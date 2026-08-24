package com.lannie.morningalarm.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

/** 로컬 설정 + 알람 캐시(오프라인/재부팅 대비) */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("morning_alarm", Context.MODE_PRIVATE)
    private val gson = Gson()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /** mom | daughter */
    var role: String
        get() = sp.getString("role", "") ?: ""
        set(v) = sp.edit().putString("role", v).apply()

    var myName: String
        get() = sp.getString("myName", "") ?: ""
        set(v) = sp.edit().putString("myName", v).apply()

    /** 정규화된 내 번호 (+8210..., +1613...) */
    var myPhone: String
        get() = sp.getString("myPhone", "") ?: ""
        set(v) = sp.edit().putString("myPhone", v).apply()

    /** 상대(엄마↔딸) 번호 */
    var peerPhone: String
        get() = sp.getString("peerPhone", "") ?: ""
        set(v) = sp.edit().putString("peerPhone", v).apply()

    var peerName: String
        get() = sp.getString("peerName", "") ?: ""
        set(v) = sp.edit().putString("peerName", v).apply()

    var paired: Boolean
        get() = sp.getBoolean("paired", false)
        set(v) = sp.edit().putBoolean("paired", v).apply()

    /** 표시 시간대: Asia/Seoul(기본) 또는 "local"(기기 시간대) */
    var displayTz: String
        get() = sp.getString("displayTz", "Asia/Seoul") ?: "Asia/Seoul"
        set(v) = sp.edit().putString("displayTz", v).apply()

    /** 딸 기기에 동기화된 알람 캐시 */
    fun saveAlarms(alarms: List<Alarm>) {
        sp.edit().putString("alarms", gson.toJson(alarms)).apply()
    }

    fun getAlarms(): List<Alarm> {
        val json = sp.getString("alarms", null) ?: return emptyList()
        val type = object : TypeToken<List<Alarm>>() {}.type
        return runCatching { gson.fromJson<List<Alarm>>(json, type) }.getOrNull() ?: emptyList()
    }

    /** 오늘 하루 그만 울리기 플래그 */
    fun stopForToday(alarmId: String) {
        sp.edit().putString("stopped_$alarmId", LocalDate.now().toString()).apply()
    }

    fun isStoppedToday(alarmId: String): Boolean =
        sp.getString("stopped_$alarmId", null) == LocalDate.now().toString()
}
