package com.fieldreport.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmTest {
    @Test fun readsAPlainJsonReply() =
        assertEquals("금형 온도를 60도로 확정.", Llm.parseChatResponse("""{"choices":[{"message":{"role":"assistant","content":"금형 온도를 60도로 확정."},"finish_reason":"stop"}]}""", false))

    @Test fun joinsAnEventStreamReply() {
        val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"보압 \"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"50MPa 유지\"}}]}\n\ndata: [DONE]\n"
        assertEquals("보압 50MPa 유지", Llm.parseChatResponse(sse, true)); assertEquals("보압 50MPa 유지", Llm.parseChatResponse(sse, false))
    }

    @Test fun dropsReasoningAndSurvivesBrokenBodies() {
        assertEquals("요약입니다.", Llm.parseChatResponse("""{"choices":[{"message":{"content":"<think>생각 중</think>\n요약입니다."}}]}""", false))
        assertEquals("", Llm.parseChatResponse("not json", false)); assertEquals("", Llm.parseChatResponse("""{"error":"x"}""", false))
    }
}

class YesNoTest {
    @org.junit.Test fun onlyAClearYesMarksAPhoto() {
        listOf("YES", "yes.", " Yes", "예", "네, 사출 조건 화면입니다").forEach { org.junit.Assert.assertTrue(it, saysYes(it)) }
        listOf("NO", "no", "아니오", "", "Not sure", "모르겠습니다").forEach { org.junit.Assert.assertFalse(it, saysYes(it)) }
    }
}

class MeetingAiTest {
    @org.junit.Test fun splitsSummaryAndOrganized() {
        val (s, o) = parseMeetingAi("[요약]\n보압을 50MPa로 확정했다.\n\n[정리]\n성형 조건\n- 보압 50MPa\n\n후속 조치\n- 없음")
        org.junit.Assert.assertEquals("보압을 50MPa로 확정했다.", s); org.junit.Assert.assertTrue(o.startsWith("성형 조건")); org.junit.Assert.assertTrue(o.endsWith("- 없음"))
    }
    @org.junit.Test fun replyWithoutHeadingsStillGivesBoth() {
        val (s, o) = parseMeetingAi("금형 온도는 60도로 정했다.\n다음 주에 재시험한다.")
        org.junit.Assert.assertEquals("금형 온도는 60도로 정했다. 다음 주에 재시험한다.", s); org.junit.Assert.assertTrue(o.contains("다음 주"))
    }
    @org.junit.Test fun longSummaryIsCutToAbout200AndVersionsSwitch() {
        val (s, _) = parseMeetingAi("[요약]\n" + "가".repeat(400) + "\n[정리]\n내용")
        org.junit.Assert.assertEquals(201, s.length)
        val m = Meeting("m", "t", "회의", 0, raw = "원본 내용", summary = "요약", organized = "정리본")
        org.junit.Assert.assertEquals("원본 내용", m.preview()); org.junit.Assert.assertEquals("원본 내용", m.full())
        val ai = m.copy(showAi = true)
        org.junit.Assert.assertEquals("요약", ai.preview()); org.junit.Assert.assertEquals("정리본", ai.full()); org.junit.Assert.assertEquals("원본 내용", ai.raw)
    }
}
