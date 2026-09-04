package com.lannie.morningalarm.util

/**
 * 그만 울리기 질문의 정답 비교.
 * 앞뒤 공백·대소문자·중간 공백을 무시한다. 예) " 미미 " == "미미", "Mi Mi" == "mimi"
 */
fun normalizeAnswer(s: String): String = s.trim().lowercase().replace(" ", "")

fun answersMatch(input: String, expected: String): Boolean =
    normalizeAnswer(expected).isNotEmpty() && normalizeAnswer(input) == normalizeAnswer(expected)

/** 여러 정답 중 하나만 맞아도 정답 */
fun anyAnswerMatches(input: String, expected: List<String>): Boolean = expected.any { answersMatch(input, it) }
