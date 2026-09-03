package com.lannie.morningalarm

import com.lannie.morningalarm.ui.daysLabel
import com.lannie.morningalarm.ui.two
import org.junit.Assert.assertEquals
import org.junit.Test

/** 화면 표시용 보조 함수 */
class CommonTest {

    @Test
    fun `empty days means every day`() {
        assertEquals("매일", daysLabel(emptyList()))
    }

    @Test
    fun `days are shown sorted with korean weekday names`() {
        assertEquals("월·수·금", daysLabel(listOf(5, 1, 3)))
        assertEquals("토·일", daysLabel(listOf(7, 6)))
    }

    @Test
    fun `two-digit padding for clock display`() {
        assertEquals("07", two(7))
        assertEquals("00", two(0))
        assertEquals("23", two(23))
    }
}
