package org.mpc.domain.session

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

val eeclassAjaxTests by testSuite {
    test("a top-level envelope gives its data") {
        val reply = assertIs<EeclassAjax.Done>(eeclassAjax("""{"status":true,"data":{"html":"<p>規則</p>"}}"""))

        assertEquals(JsonPrimitive("<p>規則</p>"), reply.data.jsonObject["html"])
    }

    test("a ret envelope succeeds with a boolean or string status") {
        val submitted = assertIs<EeclassAjax.Done>(eeclassAjax("""{"ret":{"status":true,"msg":"ok","focus":""}}"""))
        assertEquals(JsonNull, submitted.data)
        assertIs<EeclassAjax.Done>(eeclassAjax("""{"ret":{"status":"true","action":{"parentUrl":"/homework/report/1"}}}"""))
    }

    test("a refusal carries the site's message") {
        assertEquals("已超過繳交期限", assertIs<EeclassAjax.Refused>(eeclassAjax("""{"ret":{"status":false,"msg":"已超過繳交期限"}}""")).message)
        assertEquals("請填寫內容", assertIs<EeclassAjax.Refused>(eeclassAjax("""{"ret":{"status":"false","msg":"請填寫內容"}}""")).message)
        assertEquals("登入逾時", assertIs<EeclassAjax.Refused>(eeclassAjax("""{"status":false,"msg":"登入逾時"}""")).message)
        assertNull(assertIs<EeclassAjax.Refused>(eeclassAjax("""{"ret":{"status":false,"msg":null}}""")).message)
    }

    test("a script the site asks to run is dropped") {
        val reply = assertIs<EeclassAjax.Done>(eeclassAjax("""{"status":true,"customJs":"alert(1)","data":{"id":1}}"""))

        assertEquals(setOf("id"), reply.data.jsonObject.keys)
    }

    test("anything but an envelope is not a reply") {
        listOf("<html></html>", "", "[]", "{}", """{"ret":{}}""", """{"status":"maybe"}""", """{"status":null}""").forEach { text ->
            assertNull(eeclassAjax(text), text)
        }
    }
}
