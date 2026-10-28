package io.legado.app.ui.association

import io.legado.app.exception.NoStackTraceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRssSourceParserTest {

    @Test
    fun parsesSingleRssSourceObject() {
        val result = ImportRssSourceParser.parse(
            """{"sourceUrl":"https://rss.test/feed","sourceName":"Feed"}"""
        )

        assertTrue(result is RssSourceImportData.Sources)
        val source = (result as RssSourceImportData.Sources).items.single()
        assertEquals("https://rss.test/feed", source.sourceUrl)
        assertEquals("Feed", source.sourceName)
    }

    @Test
    fun keepsRssSourceArrayImport() {
        val result = ImportRssSourceParser.parse(
            """[{"sourceUrl":"https://rss.test/a"},{"sourceUrl":"https://rss.test/b"}]"""
        )

        assertEquals(
            listOf("https://rss.test/a", "https://rss.test/b"),
            (result as RssSourceImportData.Sources).items.map { it.sourceUrl }
        )
    }

    @Test
    fun keepsSourceUrlManifestImport() {
        val result = ImportRssSourceParser.parse(
            """{"sourceUrls":["https://rss.test/list"]}"""
        )

        assertEquals(
            listOf("https://rss.test/list"),
            (result as RssSourceImportData.SourceUrls).urls
        )
    }

    @Test
    fun rejectsObjectWithoutSourceUrl() {
        val error = runCatching {
            ImportRssSourceParser.parse("""{"sourceName":"Missing URL"}""")
        }.exceptionOrNull()

        assertTrue(error is NoStackTraceException)
    }
}
