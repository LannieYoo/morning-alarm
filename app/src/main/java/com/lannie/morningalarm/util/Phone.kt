package com.lannie.morningalarm.util

/**
 * 전화번호 정규화: 국가번호 + 지역번호 앞 0 제거.
 * 예) (+82, 010-1234-5678) -> +821012345678
 *     (+1, 613 555 0100)   -> +16135550100
 * 이미 +로 시작하면 그대로 숫자만 정리한다.
 */
fun normalizePhone(countryCode: String, raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("+")) {
        return "+" + trimmed.filter { it.isDigit() }
    }
    val cc = countryCode.filter { it.isDigit() }
    var body = trimmed.filter { it.isDigit() }
    if (body.startsWith("0")) body = body.drop(1)
    return "+$cc$body"
}

/** 표시용: +8210... -> 안전하게 그대로, UI에서만 사용 */
fun displayPhone(p: String): String = p
