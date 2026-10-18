package io.legado.app.ui.login

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceCallbackJsExtensionsJsTest {

    @Test
    fun openIsVisibleWhenCallbackExtensionIsBound() {
        val bindings = ScriptBindings()
        bindings["java"] = SourceCallbackJsExtensions(null, null)

        assertEquals("function", RhinoScriptEngine.eval("typeof java.open", bindings))
        assertEquals("function", RhinoScriptEngine.eval("typeof java.openUrl", bindings))
    }

    @Test
    fun fourArgumentRssCallIsAcceptedByRhinoEvenWithoutAnActivity() {
        val bindings = ScriptBindings().apply { put("java", SourceCallbackJsExtensions(null, null)) }
        assertNull(
            RhinoScriptEngine.eval(
                "java.open('rss', 'https://rss.test/article', '书名', 'https://rss.test')",
                bindings,
            )
        )
    }
}
