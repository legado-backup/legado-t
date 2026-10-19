package io.legado.app.data.entities

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * jsLib/enabledCookieJar 是 BaseSource 统一字段,运行链路(AnalyzeUrl/evalJS)消费它们;
 * 导入解析必须逐字读取,否则带这两个字段的引擎 JSON 会被静默丢弃
 */
class HttpTtsAuxFieldsImportTest {

    @Test
    fun `import reads jsLib and enabledCookieJar`() {
        val json = """
            {"id":1,"name":"某引擎","url":"http://a.test/tts",
             "jsLib":"function sign(t){return java.md5Encode(t)}",
             "enabledCookieJar":true}
        """.trimIndent()
        val httpTTS = HttpTTS.fromJson(json).getOrThrow()
        assertEquals("function sign(t){return java.md5Encode(t)}", httpTTS.jsLib)
        assertTrue(httpTTS.enabledCookieJar == true)
    }

    @Test
    fun `absent fields fall back to defaults`() {
        val httpTTS = HttpTTS.fromJson(
            """{"id":1,"name":"某引擎","url":"http://a.test/tts"}"""
        ).getOrThrow()
        assertNull(httpTTS.jsLib)
        assertFalse(httpTTS.enabledCookieJar == true)
    }

    @Test
    fun `export then import preserves both fields`() {
        val httpTTS = HttpTTS(
            id = 7,
            name = "某引擎",
            url = "http://a.test/tts",
            jsLib = "function sign(t){return java.md5Encode(t)}",
            enabledCookieJar = true
        )
        val back = HttpTTS.fromJson(GSON.toJson(httpTTS)).getOrThrow()
        assertEquals(httpTTS.jsLib, back.jsLib)
        assertEquals(httpTTS.enabledCookieJar, back.enabledCookieJar)
    }
}
