package com.lannie.morningalarm

import com.lannie.morningalarm.util.normalizePhone
import org.junit.Assert.assertEquals
import org.junit.Test

/** 전화번호 정규화: 어떻게 입력해도 같은 사람은 같은 ID(+국가번호+번호)가 되어야 한다 */
class PhoneTest {

    @Test
    fun `korean number drops leading zero and adds +82`() {
        assertEquals("+821042885499", normalizePhone("82", "01042885499"))
    }

    @Test
    fun `hyphens and spaces are ignored`() {
        assertEquals("+821042885499", normalizePhone("82", "010-4288-5499"))
        assertEquals("+821042885499", normalizePhone("82", "010 4288 5499"))
    }

    @Test
    fun `number without leading zero is accepted too`() {
        assertEquals("+821042885499", normalizePhone("82", "1042885499"))
    }

    @Test
    fun `already international format is kept regardless of selected country`() {
        assertEquals("+821042885499", normalizePhone("1", "+82 10-4288-5499"))
        assertEquals("+821042885499", normalizePhone("82", "+821042885499"))
    }

    @Test
    fun `canadian number gets +1`() {
        assertEquals("+16135550100", normalizePhone("1", "(613) 555-0100"))
        assertEquals("+16135550100", normalizePhone("1", "613 555 0100"))
    }

    @Test
    fun `country code with plus sign is tolerated`() {
        assertEquals("+821042885499", normalizePhone("+82", "01042885499"))
    }

    @Test
    fun `same person entered on two phones matches`() {
        val onKoreanPhone = normalizePhone("82", "010-4288-5499")
        val onCanadianPhone = normalizePhone("82", "01042885499")
        assertEquals(onKoreanPhone, onCanadianPhone)
    }
}
