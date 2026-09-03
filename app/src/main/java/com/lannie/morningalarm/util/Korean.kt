package com.lannie.morningalarm.util

/**
 * 이름 뒤에 붙는 호격 조사(아/야).
 * 받침 있으면 "아"(유진아), 없으면 "야"(민수야). 한글이 아니면 조사 없이 이름만.
 */
fun vocative(name: String): String {
    val n = name.trim()
    if (n.isEmpty()) return ""
    val last = n.last()
    if (last !in '가'..'힣') return n
    val hasBatchim = (last - '가') % 28 != 0
    return n + if (hasBatchim) "아" else "야"
}

/** 자주 쓰는 알람 문장 (받는 사람 이름 반영) */
fun alarmPresets(name: String): List<String> {
    val v = vocative(name)
    val call = if (v.isEmpty()) "" else "$v "
    return listOf(
        "${call}일어나, 아침이야!",
        "${call}전화 부탁해요",
        "${call}밥 챙겨 먹었어?",
        "${call}약 먹을 시간이야"
    )
}
