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
