package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceMainJsTest {

    @Test
    fun isJsSourceDerivedFromMainJs() {
        val source = BookSource(bookSourceUrl = "https://a.test")
        assertFalse(source.isJsSource())
        source.mainJs = ""
        assertFalse(source.isJsSource())
        source.mainJs = "  "
        assertFalse(source.isJsSource())
        source.mainJs = "var config = {}"
        assertTrue(source.isJsSource())
    }

    @Test
    fun getLoginJsPrefersMainJsForJsSource() {
        val source = BookSource(
            bookSourceUrl = "https://a.test",
            loginUrl = "https://a.test/login",
        )
        // 声明式:loginUrl 非 <js> 形态原样返回
        assertEquals("https://a.test/login", source.getLoginJs())
        val js = "var config = {}\nfunction login() {}"
        source.mainJs = js
        // JS 源:mainJs 即登录脚本载体,login/按钮 action 可用其全部顶层函数
        assertEquals(js, source.getLoginJs())
    }

    @Test
    fun getLoginJsKeepsInlineJsExtractionForDeclarative() {
        val source = BookSource(
            bookSourceUrl = "https://b.test",
            loginUrl = "<js>function login(){}</js>",
        )
        assertEquals("function login(){}", source.getLoginJs())
    }

    @Test
    fun equalCoversMainJs() {
        val a = BookSource(bookSourceUrl = "https://a.test", bookSourceName = "a")
        val b = a.copy()
        assertTrue(a.equal(b))
        b.mainJs = "var config = {}"
        assertFalse(a.equal(b))
    }
}
