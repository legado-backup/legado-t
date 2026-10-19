package io.legado.app.model.jsSource

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导入徽标(新增/更新/已存在)按 lastUpdateTime 版本语义判定,JS源的该值来自脚本内
 * source.lastUpdateTime 的真实求值——此处锁 extract 侧的契约:声明值原样透传、
 * 未声明恒为 0、同脚本重复提取结果稳定(声明须是写死数值,求值才可复现)。
 */
class JsSourceImportBadgeProbeTest {

    private fun script(configLines: String) = """
        var config = {
          bookSourceUrl: "https://example.test",
          bookSourceName: "探针源"$configLines
        }
        function search(key, page) { return [] }
        function getChapters(book) { return [] }
        function getContent(chapter, book) { return "" }
    """.trimIndent()

    @Test
    fun declaredTimestampPassesThrough() {
        val s = JsSourceConfig.extract(script(",\n  lastUpdateTime: 1752449000000"))
        assertEquals(1752449000000L, s.lastUpdateTime)
    }

    @Test
    fun missingTimestampIsZeroAndStableAcrossExtracts() {
        val text = script("")
        val first = JsSourceConfig.extract(text)
        val second = JsSourceConfig.extract(text)
        assertEquals(0L, first.lastUpdateTime)
        assertEquals(first.lastUpdateTime, second.lastUpdateTime)
    }
}
