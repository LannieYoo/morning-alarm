package com.lannie.morningalarm

import com.lannie.morningalarm.data.Question
import com.lannie.morningalarm.util.answersMatch
import com.lannie.morningalarm.util.anyAnswerMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 그만 울리기 질문 정답 비교 규칙 */
class AnswerTest {

    @Test
    fun `exact match`() {
        assertTrue(answersMatch("미미", "미미"))
    }

    @Test
    fun `surrounding and inner spaces are ignored`() {
        assertTrue(answersMatch("  미 미 ", "미미"))
        assertTrue(answersMatch("서울 특별시", "서울특별시"))
    }

    @Test
    fun `case insensitive for latin letters`() {
        assertTrue(answersMatch("Mimi", "mimi"))
        assertTrue(answersMatch("PARIS", "paris"))
    }

    @Test
    fun `wrong answer is rejected`() {
        assertFalse(answersMatch("코코", "미미"))
        assertFalse(answersMatch("", "미미"))
    }

    @Test
    fun `any of up to three answers counts`() {
        val q = Question(q = "1+0은?", a = "1", a2 = "일", a3 = "하나")
        assertTrue(anyAnswerMatches("1", q.answers()))
        assertTrue(anyAnswerMatches(" 일 ", q.answers()))
        assertTrue(anyAnswerMatches("하나", q.answers()))
        assertFalse(anyAnswerMatches("둘", q.answers()))
        assertEquals(listOf("1", "일", "하나"), q.answers())
        assertEquals(listOf("미미"), Question(q = "강아지?", a = "미미").answers())
    }

    @Test
    fun `empty expected answer never matches`() {
        assertFalse(answersMatch("", ""))
        assertFalse(answersMatch("아무거나", "   "))
    }
}
