package com.fieldreport.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    @Test fun parsesTitleTagsAndPrefersRoadAddress() {
        val json = """{"total":2,"items":[
            {"title":"<b>태산</b>솔루션즈 &amp; 랩","category":"기업>제조","telephone":"031-000-0000","address":"경기도 화성시 비봉면 양노리 670","roadAddress":"경기도 화성시 비봉면 화성로1616번길 60"},
            {"title":"서연그룹 R&amp;D센터","category":"","telephone":"","address":"경기도 안양시 동안구 관양동 1","roadAddress":""}]}"""
        val r = parseNaverLocal(json)
        assertEquals(2, r.size)
        assertEquals("태산솔루션즈 & 랩", r[0].name); assertEquals("경기도 화성시 비봉면 화성로1616번길 60", r[0].address); assertEquals("031-000-0000", r[0].phone)
        assertEquals("서연그룹 R&D센터", r[1].name); assertEquals("경기도 안양시 동안구 관양동 1", r[1].address)
    }

    @Test fun emptyOrBrokenResponseGivesNoResults() {
        assertTrue(parseNaverLocal("""{"items":[]}""").isEmpty()); assertTrue(parseNaverLocal("not json").isEmpty()); assertTrue(parseNaverLocal("{}").isEmpty())
    }

    @Test fun addressPutsATripOnTheMapWhenTheNameAloneDoesNot() {
        assertEquals(null, cityOf("서연그룹 R&D센터")); assertEquals("안양", cityOf("서연그룹 R&D센터 경기도 안양시 동안구 부림로"))
    }
}
