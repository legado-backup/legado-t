package io.legado.app.web.mcp

import io.legado.app.data.dao.McpExploreInsertPolicy
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class McpExploreInputsTest {
    private val kinds = listOf(
        ExploreKind("分区标题", null),
        ExploreKind("ERROR:加载失败", "stack"),
        ExploreKind("热门", "https://example.com/hot"),
        ExploreKind("更多", "https://example.com/fantasy"),
        ExploreKind("更多", "https://example.com/city"),
    )

    private fun prepare(json: String, sourceKinds: List<ExploreKind> = kinds): ExploreContainer =
        McpExploreInputs.prepare(McpExploreInputs.parse(json), "测试书源", true, sourceKinds)

    @Test
    fun `single defaults to first valid category not all categories`() {
        val result = prepare("""{"sourceUrl":"source"}""")
        assertEquals("热门", result.kindTitle)
        assertEquals("https://example.com/hot", result.kindUrl)
        assertEquals("热门", result.kindTitles)
        assertEquals(0L, result.id)
        assertTrue(result.enabled)
    }

    @Test
    fun `kindUrl-only selects exact URL and restores its title`() {
        val result = prepare("""{"sourceUrl":"source","kindUrl":"https://example.com/city"}""")
        assertEquals("更多", result.kindTitle)
        assertEquals("https://example.com/city", result.kindUrl)
    }

    @Test
    fun `single snapshot disambiguates duplicate titles and allows removed category fallback`() {
        val exact = prepare("""{"sourceUrl":"source","kindTitle":"更多","kindUrl":"https://example.com/city"}""")
        assertEquals("https://example.com/city", exact.kindUrl)
        val fallback = prepare("""{"sourceUrl":"source","kindTitle":"旧分类","kindUrl":"https://example.com/old"}""")
        assertEquals("https://example.com/old", fallback.kindUrl)
    }

    @Test
    fun `multiple source URLs generate one full-category container per source in stable order`() {
        val inputs = McpExploreInputs.parseBatch(
            """{"sourceUrls":["source-a","source-b","source-a"],"groupName":"批量","style":1,"listCount":8}"""
        )
        assertEquals(listOf("source-a", "source-b"), inputs.map { it.container.sourceUrl })
        val results = inputs.map { McpExploreInputs.prepare(it, "源", true, kinds) }
        assertEquals(listOf("热门,更多,更多", "热门,更多,更多"), results.map { it.kindTitles })
        assertEquals(listOf("批量", "批量"), results.map { it.groupName })
        assertEquals(8, results.first().listCount)
        assertEquals(ExploreContainer.STYLE_LIST, results.first().style)
    }

    @Test
    fun `JSON array batch preserves single default and can opt into allKinds`() {
        val singleDefault = McpExploreInputs.parseBatch(
            """{"containers":"[{\"sourceUrl\":\"a\"},{\"sourceUrl\":\"b\",\"kindUrl\":\"https://example.com/city\"}]"}"""
        )
        assertEquals(listOf(McpExploreKindMode.SELECTED, McpExploreKindMode.SELECTED), singleDefault.map { it.mode })
        val all = McpExploreInputs.parseBatch(
            """{"containers":"[{\"sourceUrl\":\"a\"}]","allKinds":true}"""
        ).single()
        assertEquals(McpExploreKindMode.ALL, all.mode)
        assertEquals("热门,更多,更多", McpExploreInputs.prepare(all, "源", true, kinds).kindTitles)
    }

    @Test
    fun `dynamic mode keeps first URL fallback but leaves selection unfrozen`() {
        val result = prepare("""{"sourceUrl":"source","dynamic":true}""")
        assertEquals("热门", result.kindTitle)
        assertEquals("https://example.com/hot", result.kindUrl)
        assertEquals("", result.kindTitles)
        assertEquals("", result.kindUrls)
        val batch = McpExploreInputs.parseBatch("""{"sourceUrls":["a"],"dynamic":true}""").single()
        assertEquals(McpExploreKindMode.DYNAMIC, batch.mode)
    }

    @Test
    fun `delimiter URLs use dynamic selections rather than corrupted snapshots`() {
        val result = prepare(
            """{"sourceUrl":"source","allKinds":true}""",
            listOf(ExploreKind("全部", "https://example.com/all<,-{{page}}>.html")),
        )
        assertEquals("https://example.com/all<,-{{page}}>.html", result.kindUrl)
        assertEquals("", result.kindUrls)
        assertEquals("", result.kindTitles)
    }

    @Test
    fun `explicit null never reaches nonnullable entity fields`() {
        listOf("sourceUrl", "sourceName", "kindTitle", "kindUrl", "kindTitles", "kindUrls", "groupName", "enabled", "style", "listCount", "id", "sortOrder").forEach { key ->
            val json = if (key == "sourceUrl") """{"sourceUrl":null}""" else {
                """{"sourceUrl":"source","$key":null}"""
            }
            assertThrows("null $key", IllegalArgumentException::class.java) { McpExploreInputs.parse(json) }
        }
        assertEquals(null, prepare("""{"sourceUrl":"source","customTitle":null}""").customTitle)
    }

    @Test
    fun `malformed types ranges and mixed category modes are rejected`() {
        listOf(
            """{"sourceUrl":123}""",
            """{"sourceUrl":"source","style":2}""",
            """{"sourceUrl":"source","style":0.5}""",
            """{"sourceUrl":"source","listCount":0}""",
            """{"sourceUrl":"source","enabled":"true"}""",
            """{"sourceUrl":"source","id":9223372036854775808}""",
            """{"sourceUrl":"source","dynamic":true,"kindTitle":"热门"}""",
            """{"sourceUrl":"source","dynamic":true,"allKinds":true}""",
            """{"sourceUrl":"source","kindUrls":"url"}""",
            "null", "[]",
        ).forEach { json -> assertThrows(json, IllegalArgumentException::class.java) { McpExploreInputs.parse(json) } }
    }

    @Test
    fun `invalid batch shape empty over-limit and null elements are rejected before prepare`() {
        val tooMany = (1..51).joinToString(",", "[", "]") { "\"s$it\"" }
        listOf(
            "{}",
            """{"sourceUrls":[]}""",
            """{"sourceUrls":[null]}""",
            """{"sourceUrls":["a"],"containers":"[]"}""",
            """{"sourceUrls":["a"],"allKinds":true,"dynamic":true}""",
            """{"containers":"[null]"}""",
            """{"containers":"[]"}""",
            """{"sourceUrls":$tooMany}""",
        ).forEach { json ->
            assertThrows(json, IllegalArgumentException::class.java) { McpExploreInputs.parseBatch(json) }
        }
    }

    @Test
    fun `disabled sources no valid categories and missing requested URLs fail preparation`() {
        val input = McpExploreInputs.parse("""{"sourceUrl":"source"}""")
        assertThrows(IllegalArgumentException::class.java) { McpExploreInputs.prepare(input, "源", false, kinds) }
        assertThrows(IllegalArgumentException::class.java) { McpExploreInputs.prepare(input, "源", true, emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            prepare("""{"sourceUrl":"source","kindUrl":"not-present"}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            prepare("""{"sourceUrl":"source","kindTitles":"热门,更多","kindUrls":"one"}""")
        }
    }

    @Test
    fun `presentation update retains the active category from a multi-category snapshot`() {
        val current = prepare(
            """{"sourceUrl":"source","kindTitle":"更多","kindUrl":"https://example.com/city","kindTitles":"热门,更多","kindUrls":"https://example.com/hot,https://example.com/city","customTitle":"新标题"}"""
        )
        assertEquals("更多", current.kindTitle)
        assertEquals("https://example.com/city", current.kindUrl)
        assertEquals("热门,更多", current.kindTitles)
    }

    @Test
    fun `cache key includes the whole category set but ignores presentation changes`() {
        val current = prepare("""{"sourceUrl":"source"}""")
        assertFalse(McpExploreInputs.sameTargetAndKinds(current, current.copy(kindTitles = "更多")))
        assertFalse(McpExploreInputs.sameTargetAndKinds(current, current.copy(kindUrls = "new")))
        assertTrue(McpExploreInputs.sameTargetAndKinds(current, current.copy(customTitle = "改标题", style = 1)))
    }

    @Test
    fun `new row policy ignores conflicting IDs and orders and appends without integer overflow`() {
        val input = ExploreContainer(id = 123L, sourceUrl = "source", sortOrder = 5)
        val rows = McpExploreInsertPolicy.newRows(listOf(input, input), 12)
        assertEquals(listOf(0L, 0L), rows.map { it.id })
        assertEquals(listOf(13, 14), rows.map { it.sortOrder })
        assertEquals(123L, input.id)
        assertEquals(5, input.sortOrder)
        assertThrows(IllegalArgumentException::class.java) {
            McpExploreInsertPolicy.newRows(listOf(input), Int.MAX_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            McpExploreInsertPolicy.newRows(listOf(input, input), Int.MAX_VALUE - 1)
        }
    }
}
