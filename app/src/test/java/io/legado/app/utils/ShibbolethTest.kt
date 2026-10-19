package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShibbolethTest {

    private val mappingHeavyUrl = "https://example.com/path/file.json?key=4%2F5"
    private val fixedTime = 1_789_344_000_000L

    @Test
    fun `all supported types round trip`() {
        ShibbolethType.entries.forEachIndexed { index, type ->
            val encoded = Shibboleth.encode(mappingHeavyUrl, type, 0, fixedTime + index)

            assertTrue(encoded != null)
            assertEquals(
                ShibbolethParseResult.Valid(ShibbolethToken(mappingHeavyUrl, type, 0)),
                Shibboleth.parse(encoded!!, fixedTime),
            )
        }
    }

    @Test
    fun `book source permanent token uses LegadoT envelope`() {
        val encoded = Shibboleth.encode(mappingHeavyUrl, ShibbolethType.BOOK_SOURCE, 0, fixedTime)!!

        assertTrue(encoded.startsWith("复制口令到阅读导入#L:"))
        assertTrue(encoded.contains("！sy©0¥LegadoT^"))
    }

    @Test
    fun `expired positive timestamp is structured result`() {
        val expiresAt = 1_789_344_000_000L
        val encoded = Shibboleth.encode(
            mappingHeavyUrl,
            ShibbolethType.BOOK_SOURCE,
            expiresAt,
            fixedTime,
        )!!

        assertEquals(
            ShibbolethParseResult.Expired(expiresAt),
            Shibboleth.parse(encoded, expiresAt + 1),
        )
    }

    @Test
    fun `ordinary clipboard text is not a token`() {
        assertEquals(ShibbolethParseResult.NotShibboleth, Shibboleth.parse("ordinary clipboard text"))
    }

    @Test
    fun `raw HTTP sibling envelope parses as valid`() {
        val url = "http://example.test/path/file.json?key=4%2F5"
        val text = "复制口令到阅读导入$url！sy©0¥Sigma^"

        assertEquals(
            ShibbolethParseResult.Valid(
                ShibbolethToken(url, ShibbolethType.BOOK_SOURCE, 0),
            ),
            Shibboleth.parse(text, fixedTime),
        )
    }

    @Test
    fun `missing or disordered delimiters are invalid without throwing`() {
        val malformed = listOf(
            "复制口令到阅读导入#L:example.test",
            "复制口令到阅读导入#L:example.test！sy",
            "复制口令到阅读导入#L:example.test！sy©0",
            "复制口令到阅读导入#L:example.test！sy©0¥Sigma",
            "复制口令到阅读导入#L:example.test©0！sy¥Sigma^",
        )

        malformed.forEach {
            assertEquals(
                ShibbolethParseResult.Invalid(ShibbolethParseResult.Reason.MALFORMED),
                Shibboleth.parse(it),
            )
        }
    }

    @Test
    fun `parser accepts arbitrary custom tail marker`() {
        listOf("Sigma", "LegadoT", "Omega", "").forEach { marker ->
            val text = "复制口令到阅读导入#L:example电🛜1！sy©0¥$marker^"
            assertTrue(Shibboleth.parse(text, fixedTime) is ShibbolethParseResult.Valid)
        }
    }

    @Test
    fun `tail marker still requires closing caret`() {
        val text = "复制口令到阅读导入#L:example电🛜1！sy©0¥LegadoT"
        assertEquals(
            ShibbolethParseResult.Invalid(ShibbolethParseResult.Reason.MALFORMED),
            Shibboleth.parse(text, fixedTime),
        )
    }

    @Test
    fun `truncated token fields are invalid without throwing`() {
        val truncated = listOf(
            "复制口令到阅读导入#L:",
            "复制口令到阅读导入#L:example。com！",
            "复制口令到阅读导入#L:example。com！sy©",
            "复制口令到阅读导入#L:example。com！sy©1234567¥",
        )

        truncated.forEach {
            assertEquals(
                ShibbolethParseResult.Invalid(ShibbolethParseResult.Reason.MALFORMED),
                Shibboleth.parse(it),
            )
        }
    }

    @Test
    fun `unknown type is invalid`() {
        val text = "复制口令到阅读导入#L:example电🛜1！xx©0¥Sigma^"

        assertEquals(
            ShibbolethParseResult.Invalid(ShibbolethParseResult.Reason.UNKNOWN_TYPE),
            Shibboleth.parse(text),
        )
    }

    @Test
    fun `encoder accepts only valid https URLs`() {
        assertTrue(Shibboleth.canEncode("https://example.com/path"))
        assertFalse(Shibboleth.canEncode("http://example.test/path"))
        assertFalse(Shibboleth.canEncode("HTTPS://example.test/path"))
        assertFalse(Shibboleth.canEncode("Https://example.test/path"))
        assertFalse(Shibboleth.canEncode("https:///missing-host"))
        assertNull(Shibboleth.encode("http://example.test", ShibbolethType.BOOK_SOURCE, 0, fixedTime))
        assertNull(Shibboleth.encode("HTTPS://example.test", ShibbolethType.BOOK_SOURCE, 0, fixedTime))
        assertNull(Shibboleth.encode("Https://example.test", ShibbolethType.BOOK_SOURCE, 0, fixedTime))
        assertNull(Shibboleth.encode("not a url", ShibbolethType.BOOK_SOURCE, 0, fixedTime))
    }

    @Test
    fun `fixed donor fixture encodes and decodes all sibling compatible types`() {
        val expiresAt = 1_800_000_000_000L
        val fixtures = mapOf(
            ShibbolethType.BOOK_SOURCE to
                "复制口令到阅读导入#L:example店🛜1刚path钢file店串?key=🕓拜2F五！sy©1800000¥Sigma^",
            ShibbolethType.RSS_SOURCE to
                "复制口令到阅读导入#L:example店🛜1刚path钢file店串?key=🕓拜2F五！dy©1800000¥Sigma^",
            ShibbolethType.DICT_RULE to
                "复制口令到阅读导入#L:example店🛜1刚path钢file店串?key=🕓拜2F五！zd©1800000¥Sigma^",
            ShibbolethType.REPLACE_RULE to
                "复制口令到阅读导入#L:example店🛜1刚path钢file店串?key=🕓拜2F五！jh©1800000¥Sigma^",
            ShibbolethType.TOC_RULE to
                "复制口令到阅读导入#L:example店🛜1刚path钢file店串?key=🕓拜2F五！ml©1800000¥Sigma^",
            ShibbolethType.TTS_RULE to
                "复制口令到阅读导入#L:example店🛜1刚path钢file店串?key=🕓拜2F五！ld©1800000¥Sigma^",
        )

        fixtures.forEach { (type, fixture) ->
            assertEquals(
                fixture.replace("¥Sigma^", "¥LegadoT^"),
                Shibboleth.encode(mappingHeavyUrl, type, expiresAt, fixedTime),
            )
            assertEquals(
                ShibbolethParseResult.Valid(ShibbolethToken(mappingHeavyUrl, type, expiresAt)),
                Shibboleth.parse(fixture, fixedTime),
            )
        }
    }

    @Test
    fun `encoder rejects mapping literals and structural delimiters`() {
        val unsafeUrls = listOf(
            "https://example.com/path/电",
            "https://example.com/path！value",
            "https://example.com/path©value",
            "https://example.com/path¥value",
            "https://example.com/path^value",
        )

        unsafeUrls.forEach { url ->
            assertFalse(Shibboleth.canEncode(url))
            assertNull(Shibboleth.encode(url, ShibbolethType.BOOK_SOURCE, 0, fixedTime))
        }
    }

    @Test
    fun `encoder rejects positive expiry shorter than seven digits`() {
        assertNull(
            Shibboleth.encode(
                mappingHeavyUrl,
                ShibbolethType.BOOK_SOURCE,
                999_999L,
                fixedTime,
            ),
        )
    }

    @Test
    fun `permanent and future expiry fields are valid`() {
        val permanent = Shibboleth.encode(mappingHeavyUrl, ShibbolethType.BOOK_SOURCE, 0, fixedTime)!!
        val futureExpiry = 1_800_000_000_000L
        val future = Shibboleth.encode(
            mappingHeavyUrl,
            ShibbolethType.BOOK_SOURCE,
            futureExpiry,
            fixedTime,
        )!!

        assertEquals(
            ShibbolethParseResult.Valid(
                ShibbolethToken(mappingHeavyUrl, ShibbolethType.BOOK_SOURCE, 0),
            ),
            Shibboleth.parse(permanent, fixedTime),
        )
        assertEquals(
            ShibbolethParseResult.Valid(
                ShibbolethToken(mappingHeavyUrl, ShibbolethType.BOOK_SOURCE, futureExpiry),
            ),
            Shibboleth.parse(future, fixedTime),
        )
    }

    @Test
    fun `nonnumeric expiry fields are malformed`() {
        listOf("abc", "12a4567", "١٢٣٤٥٦٧", "").forEach { expiry ->
            val text = "复制口令到阅读导入#L:example电🛜1！sy©$expiry¥Sigma^"

            assertEquals(
                ShibbolethParseResult.Invalid(ShibbolethParseResult.Reason.MALFORMED),
                Shibboleth.parse(text),
            )
        }
    }

    @Test
    fun `unconventional numeric expiry widths import as permanent`() {
        listOf("00", "123456", "12345678").forEach { expiry ->
            val text = "复制口令到阅读导入#L:example电🛜1！sy©$expiry¥Sigma^"

            assertEquals(
                ShibbolethParseResult.Valid(
                    ShibbolethToken("https://example.com", ShibbolethType.BOOK_SOURCE, 0),
                ),
                Shibboleth.parse(text, fixedTime),
            )
        }
    }

    @Test
    fun `full second and millisecond expiry widths normalize`() {
        val expected = ShibbolethParseResult.Valid(
            ShibbolethToken("https://example.com", ShibbolethType.BOOK_SOURCE, 1_800_000_000_000L),
        )
        listOf("1800000000000", "1800000000").forEach { expiry ->
            val text = "复制口令到阅读导入#L:example电🛜1！sy©$expiry¥Sigma^"

            assertEquals(expected, Shibboleth.parse(text, fixedTime))
        }
        assertEquals(
            ShibbolethParseResult.Expired(1_500_000_000_000L),
            Shibboleth.parse("复制口令到阅读导入#L:example电🛜1！sy©1500000000000¥Sigma^", fixedTime),
        )
    }

    @Test
    fun `decoration between prefix and marker still parses`() {
        listOf("：", " ", "\n", "，口令是").forEach { junk ->
            val text = "复制口令到阅读导入$junk#L:example电🛜1！sy©0¥Sigma^"

            assertEquals(
                ShibbolethParseResult.Valid(
                    ShibbolethToken("https://example.com", ShibbolethType.BOOK_SOURCE, 0),
                ),
                Shibboleth.parse(text, fixedTime),
            )
        }
    }

    @Test
    fun `sibling prefix variants and bare token body parse`() {
        listOf(
            "复制口令到阅读星空导入#L:example电🛜1！sy©0¥Sigma^",
            "复制口令到阅读App导入#L:example电🛜1！sy©0¥Sigma^",
            "#L:example电🛜1！sy©0¥Sigma^",
        ).forEach { text ->
            assertEquals(
                ShibbolethParseResult.Valid(
                    ShibbolethToken("https://example.com", ShibbolethType.BOOK_SOURCE, 0),
                ),
                Shibboleth.parse(text, fixedTime),
            )
        }
    }

    @Test
    fun `bare marker without delimiter chain stays silent`() {
        assertEquals(
            ShibbolethParseResult.NotShibboleth,
            Shibboleth.parse("代码片段里出现#L:标记的普通剪贴板文本"),
        )
    }

    @Test
    fun `all sibling mapping replacements restore exactly`() {
        val variants = listOf(
            "#L:example电🛜1/path?percent=白20&slash=杠&zip=压&json=串&digits=四五六零&domains=example电🛜2,example电🛜3,example电🛜7,example电🛜8,example电🛜9",
            "#L:example店🌐1/path?percent=百20&slash=刚&zip=亚&json=穿&digits=是武刘另&domains=example店🌐2,example店🌐3,example店🌐7,example店🌐8,example店🌐9",
            "#L:example垫🌏1/path?percent=拜20&slash=钢&zip=呀&json=船&digits=时误留玲&domains=example垫🌏2,example垫🌏3,example垫🌏7,example垫🌏8,example垫🌏9",
            "#L:example.com/path?percent=摆20&slash=岗&zip=牙&json=传&digits=丝勿陆灵&dot=example殿com",
            "#L:example.com/path?percent=💯20&slash=🎹&zip=🦆&json=🚢&digits=🕓🕔🕕⏰&dot=example。com",
        )
        val expectedPrefix = "https://example.com/path?percent=%20&slash=/&zip=zip&json=json&digits=4560"
        val expectedDomains = "&domains=example.cn,example.net,example.org,example.xyz,example.me"

        variants.forEachIndexed { index, encodedUrl ->
            val text = "复制口令到阅读导入$encodedUrl！sy©0¥Sigma^"
            val expectedUrl = expectedPrefix + if (index < 3) expectedDomains else "&dot=example.com"

            assertEquals(
                ShibbolethParseResult.Valid(
                    ShibbolethToken(expectedUrl, ShibbolethType.BOOK_SOURCE, 0),
                ),
                Shibboleth.parse(text, fixedTime),
            )
        }
    }
}
