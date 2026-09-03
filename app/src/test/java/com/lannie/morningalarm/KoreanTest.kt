package com.lannie.morningalarm

import com.lannie.morningalarm.util.alarmPresets
import com.lannie.morningalarm.util.vocative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 이름 뒤 호격 조사(아/야) */
class KoreanTest {

    @Test
    fun `name ending with consonant gets 아`() {
        assertEquals("유진아", vocative("유진"))
        assertEquals("엄마야", vocative("엄마"))
        assertEquals("민수야", vocative("민수"))
        assertEquals("지혜야", vocative("지혜"))
        assertEquals("하늘아", vocative("하늘"))
    }

    @Test
    fun `non-korean name is used as is`() {
        assertEquals("Mom", vocative("Mom"))
        assertEquals("", vocative("   "))
    }

    @Test
    fun `presets include the name with the right particle`() {
        val p = alarmPresets("유진")
        assertEquals("유진아 일어나, 아침이야!", p[0])
        assertEquals("유진아 전화 부탁해요", p[1])
        assertTrue(alarmPresets("").all { !it.startsWith(" ") })
    }
}
