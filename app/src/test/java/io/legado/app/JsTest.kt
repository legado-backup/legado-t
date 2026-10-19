package io.legado.app

import com.script.ScriptBindings
import com.script.ScriptException
import com.script.rhino.RhinoScriptEngine
import com.sun.net.httpserver.HttpServer
import io.legado.app.data.entities.BookChapter
import java.net.InetSocketAddress
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

class JsTest {

    @Language("js")
    private val printJs = """
        function print(str, newline) {
            if (typeof(str) == 'undefined') {
                str = 'undefined';
            } else if (str == null) {
                str = 'null';
            } 
            java.lang.System.out.print(String(str));
            if (newline) java.lang.System.out.print("\n");
        }
        function println(str) { 
            print(str, true);
        }
    """.trimIndent()

    @Test
    fun analyzeRuleJavaDelegateRetainsStringOverloads() {
        val previousFactory = io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory
        try {
            io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory = {
                io.legado.app.model.analyzeRule.AnalyzeRuleAndroidDelegate(it)
            }
            val rule = io.legado.app.model.analyzeRule.AnalyzeRule()
            val result = rule.evalJS(
                "java.setContent('<span class=\\\"title\\\">Works</span>'); java.getString('span.title@text')"
            )
            Assert.assertEquals("Works", result)
        } finally {
            io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory = previousFactory
        }
    }

    @Test
    fun analyzeRuleJavaDelegateExposesRhinoDefaultArgumentEntrypoints() {
        val previousFactory = io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory
        try {
            io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory = {
                io.legado.app.model.analyzeRule.AnalyzeRuleAndroidDelegate(it)
            }
            val rule = io.legado.app.model.analyzeRule.AnalyzeRule()
            val result = rule.evalJS(
                """
                java.setContent('<span class=\"title\">Works</span>');
                [
                    java.getString('span.title@text'),
                    java.getStringList('span.title@text').get(0),
                    java.splitSourceRule('span.title@text').size(),
                    java.evalJS('1 + 1')
                ].join('|')
                """.trimIndent()
            )
            Assert.assertEquals("Works|Works|1|2.0", result)
        } finally {
            io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory = previousFactory
        }
    }

    @Test
    fun analyzeRuleJavaDelegateKeepsMigratedMethodSurface() {
        val previousFactory = io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory
        try {
            io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory = {
                io.legado.app.model.analyzeRule.AnalyzeRuleAndroidDelegate(it)
            }
            val rule = io.legado.app.model.analyzeRule.AnalyzeRule()
            val names = listOf(
                "ajax", "ajaxAll", "connect", "get", "put", "setContent", "setBaseUrl",
                "setRedirectUrl", "setLocal", "evalJS", "getString", "getStringList",
                "getElement", "getElements", "splitSourceRule", "reGetBook", "refreshTocUrl",
                "toURL", "getWebViewUA", "getCookie", "getFile", "readFile", "readTxtFile",
                "deleteFile", "cacheFile", "importScript", "toast", "longToast", "log",
                "startBrowser", "startBrowserAwait", "openUrl", "showBrowser", "copyText",
                "refreshBookInfo", "refreshBookToc", "refreshContent", "singleFlight", "lock", "tick",
            )
            names.forEach { name ->
                Assert.assertEquals("missing java.$name", "function", rule.evalJS("typeof java.$name"))
            }
        } finally {
            io.legado.app.model.analyzeRule.AnalyzeRule.javaDelegateFactory = previousFactory
        }
    }

    @Test
    fun analyzeUrlJavaDelegateKeepsMigratedMethodSurface() {
        val previousFactory = io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory
        try {
            io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory = {
                io.legado.app.model.analyzeRule.AnalyzeUrlAndroidDelegate(it)
            }
            val analyzeUrl = io.legado.app.model.analyzeRule.AnalyzeUrl("data:text/plain,ok")
            val names = listOf(
                "initUrl", "evalJS", "put", "get", "getStrResponse", "getResponse",
                "getByteArray", "getInputStream", "upload", "isPost", "getUserAgent",
                "getGlideUrl", "getMediaItem", "getHeaderMap",
            )
            names.forEach { name ->
                Assert.assertEquals("missing java.$name", "function", analyzeUrl.evalJS("typeof java.$name"))
            }
            Assert.assertEquals(true, analyzeUrl.evalJS("java.url != null"))
            Assert.assertEquals(true, analyzeUrl.evalJS("java.ruleUrl != null"))
        } finally {
            io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory = previousFactory
        }
    }

    @Test
    fun analyzeRuleAndAnalyzeUrlDelegatesKeepRhinoVisibleOverloads() {
        fun assertMethod(type: Class<*>, name: String, vararg parameterTypes: Class<*>) {
            Assert.assertNotNull(
                "${type.simpleName}.$name(${parameterTypes.joinToString { it.simpleName }})",
                type.getMethod(name, *parameterTypes),
            )
        }

        val ruleDelegate = io.legado.app.model.analyzeRule.AnalyzeRuleAndroidDelegate::class.java
        assertMethod(ruleDelegate, "setContent", Any::class.java)
        assertMethod(ruleDelegate, "evalJS", String::class.java)
        assertMethod(ruleDelegate, "getStringList", String::class.java)
        assertMethod(ruleDelegate, "getStringList", List::class.java)
        assertMethod(ruleDelegate, "getString", String::class.java)
        assertMethod(ruleDelegate, "getString", List::class.java)
        assertMethod(ruleDelegate, "splitSourceRule", String::class.java)

        val urlDelegate = io.legado.app.model.analyzeRule.AnalyzeUrlAndroidDelegate::class.java
        assertMethod(urlDelegate, "evalJS", String::class.java)
        assertMethod(urlDelegate, "getStrResponse")
        assertMethod(urlDelegate, "getStrResponse", String::class.java)
        assertMethod(urlDelegate, "getStrResponse", String::class.java, String::class.java)
    }

    @Test
    fun analyzeUrlJavaDelegateExposesNoArgumentResponseEntrypoint() = runBlocking {
        val previousFactory = io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory
        try {
            io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory = {
                io.legado.app.model.analyzeRule.AnalyzeUrlAndroidDelegate(it)
            }
            val analyzeUrl = io.legado.app.model.analyzeRule.AnalyzeUrl(
                "data:text/plain,ok"
            )
            val result = analyzeUrl.evalJS("java.getStrResponse().body()")
            Assert.assertEquals("ok", result)
        } finally {
            io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory = previousFactory
        }
    }

    @Test
    fun httpTtsJavaDelegateEncodesBaiduFormBody() = runBlocking {
        val previousFactory = io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory
        val previousClientFactory = io.legado.app.help.http.HttpBridge.proxyClient
        var requestBody = ""
        try {
            io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory = {
                io.legado.app.model.analyzeRule.AnalyzeUrlAndroidDelegate(it)
            }
            io.legado.app.help.http.HttpBridge.proxyClient = {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val buffer = okio.Buffer()
                        chain.request().body?.writeTo(buffer)
                        requestBody = buffer.readUtf8()
                        okhttp3.Response.Builder()
                            .request(chain.request())
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(byteArrayOf(1, 2, 3).toResponseBody())
                            .build()
                    }
                    .build()
            }
            val url = """http://tts.baidu.com/text2audio,{"method":"POST","body":"tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{(speakSpeed + 5) / 10 + 4}}&per=3&cuid=baidu_speech_demo&idx=1&cod=2&lan=zh&ctp=1&pdt=505&vol=5&aue=6&pit=5&res_tag=audio"} """
            io.legado.app.model.analyzeRule.AnalyzeUrl(
                url,
                speakText = "中文 text",
                speakSpeed = 5
            ).getResponseAwait()
            Assert.assertTrue(requestBody, requestBody.contains("tex=%25E4%25B8%25AD%25E6%2596%2587%2Btext"))
            Assert.assertTrue(requestBody, requestBody.contains("spd=5"))
            Assert.assertTrue(requestBody, requestBody.contains("per=3"))
        } finally {
            io.legado.app.model.analyzeRule.AnalyzeUrl.javaDelegateFactory = previousFactory
            io.legado.app.help.http.HttpBridge.proxyClient = previousClientFactory
        }
    }

    @Test
    fun analyzeUrlReturnsDataUriAsTextWithoutHttpRequest() = runBlocking {
        val response = io.legado.app.model.analyzeRule.AnalyzeUrl(
            "data:text/plain;base64,SGVsbG8="
        ).getStrResponseAwait(useWebView = false)

        Assert.assertEquals("Hello", response.body)
        val percentEncodedResponse = io.legado.app.model.analyzeRule.AnalyzeUrl(
            "data:text/plain;charset=UTF-8,Hello%20world"
        ).getStrResponseAwait(useWebView = false)
        Assert.assertEquals("Hello world", percentEncodedResponse.body)
    }

    @Test
    fun analyzeUrlSendsUrlOptionsHeadersQueryAndJsonPostBody() = runBlocking {
        val previousClientFactory = io.legado.app.help.http.HttpBridge.proxyClient
        var requestUrl = ""
        var requestHeader = ""
        var requestBody = ""
        try {
            io.legado.app.help.http.HttpBridge.proxyClient = {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        requestUrl = chain.request().url.toString()
                        requestHeader = chain.request().header("X-Fixture").orEmpty()
                        val buffer = okio.Buffer()
                        chain.request().body?.writeTo(buffer)
                        requestBody = buffer.readUtf8()
                        okhttp3.Response.Builder()
                            .request(chain.request())
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("ok".toResponseBody())
                            .build()
                    }
                    .build()
            }
            val url = "http://fixture.test/api?q=one,{\"method\":\"POST\",\"headers\":{\"X-Fixture\":\"yes\",\"Content-Type\":\"application/json\"},\"body\":{\"key\":\"value\"}}"
            val response = io.legado.app.model.analyzeRule.AnalyzeUrl(url)
                .getStrResponseAwait(useWebView = false)

            Assert.assertEquals("ok", response.body)
            Assert.assertEquals("http://fixture.test/api?q=one", requestUrl)
            Assert.assertEquals("yes", requestHeader)
            Assert.assertEquals(
                "{\"key\":\"value\"}",
                requestBody.replace(Regex("\\s"), "")
            )
        } finally {
            io.legado.app.help.http.HttpBridge.proxyClient = previousClientFactory
        }
    }

    @Test
    fun analyzeUrlFollowsRedirectAndDecodesDeclaredCharset() = runBlocking {
        val previousClientFactory = io.legado.app.help.http.HttpBridge.proxyClient
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/final")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/final") { exchange ->
            val bytes = "中文".toByteArray(charset("GBK"))
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=GBK")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            io.legado.app.help.http.HttpBridge.proxyClient = { okhttp3.OkHttpClient() }
            val response = io.legado.app.model.analyzeRule.AnalyzeUrl(
                "http://127.0.0.1:${server.address.port}/start"
            ).getStrResponseAwait(useWebView = false)

            Assert.assertEquals("中文", response.body)
            Assert.assertEquals("http://127.0.0.1:${server.address.port}/final", response.url)
        } finally {
            server.stop(0)
            io.legado.app.help.http.HttpBridge.proxyClient = previousClientFactory
        }
    }

    @Test
    fun testMap() {
        val map = hashMapOf("id" to "3242532321")
        val bindings = ScriptBindings()
        bindings["result"] = map
        @Language("js")
        val jsMap = "$=result;id=$.id;id"
        val result = RhinoScriptEngine.eval(jsMap, bindings)
        Assert.assertEquals("3242532321", result)
        @Language("js")
        val jsMap1 = """result.get("id")"""
        val result1 = RhinoScriptEngine.eval(jsMap1, bindings)
        Assert.assertEquals("3242532321", result1)
    }

    @Test
    fun testFor() {
        val scope = RhinoScriptEngine.run {
            val scope = getRuntimeScope(ScriptBindings())
            eval(printJs, scope)
            scope
        }

        @Language("js")
        val jsFor = """
            let result = 0
            let a=[1,2,3]
            let l=a.length
            for (let i = 0;i<l;i++){
            	result = result + a[i]
                println(i)
            }
            for (let o of a){
            	result = result + o
                println(o)
            }
            for (let o in a){
            	result = result + o
                println(o)
            }
            result
        """.trimIndent()
        val result = RhinoScriptEngine.eval(jsFor, scope)
        Assert.assertEquals("12012", result)
    }

    @Test
    fun testReturnNull() {
        val result = RhinoScriptEngine.eval("null")
        Assert.assertEquals(null, result)
    }

    @Test
    fun compiledScriptErrorShowsNestedFailureSource() {
        val source = """
            function inner() {
                var value = null
                return value.missing()
            }
            function outer() {
                return inner()
            }
        """.trimIndent()
        val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
        RhinoScriptEngine.compile(source).eval(scope)

        val error = Assert.assertThrows(ScriptException::class.java) {
            RhinoScriptEngine.compile("outer()").eval(scope)
        }

        Assert.assertTrue(error.message, error.message.contains("return value.missing()"))
        Assert.assertEquals(3, error.lineNumber)
    }

    @Test
    fun testReplace() {
        @Language("js")
        val js = """
            s=result.match(/(.{1,6}?)(第.*)/);
            n=s[2].length-parseInt(6-s[1].length);
            s[2].substr(0,n);
        """.trimIndent()
        val x = RhinoScriptEngine.run {
            val bindings = ScriptBindings()
            bindings["result"] = "筳彩涫第七百一十四章 人头树鮺舦綸"
            eval(js, bindings)
        }
        Assert.assertEquals(x, "第七百一十四章 人头树")
    }


    @Test
    fun chapterText() {
        val chapter = BookChapter(title = "xxxyyy")
        val bindings = ScriptBindings()
        bindings["chapter"] = chapter
        @Language("js")
        val js = "chapter.title"
        val result = RhinoScriptEngine.eval(js, bindings)
        Assert.assertEquals(result, "xxxyyy")
    }

    @Test
    fun javaListForEach() {
        val list = arrayListOf(1, 2, 3)
        val bindings = ScriptBindings()
        bindings["list"] = list
        @Language("js")
        val js = """
            var result = 0
            list.forEach(item => {result = result + item})
            result
        """.trimIndent()
        val result = RhinoScriptEngine.eval(js, bindings)
        Assert.assertEquals(result, 6.0)
    }

    class ElementsProvider {
        private val doc = org.jsoup.Jsoup.parse(
            """<div id="video-artist-name"><a href="/artist/1">n</a></div>"""
        )

        fun getElements(rule: String): List<Any> = doc.select("$rule a")
    }

    @Test
    fun javaListSubclassMethods() {
        val provider = ElementsProvider()
        val bindings = ScriptBindings()
        bindings["java"] = provider
        bindings["result"] = provider.getElements("#video-artist-name")
        // 方法返回值路径(staticType = List<Any>),对应 java.getElements(rule).attr('href')
        val viaMethod = RhinoScriptEngine.eval(
            "java.getElements('#video-artist-name').attr('href')", bindings
        )
        Assert.assertEquals("/artist/1", viaMethod)
        // 直接绑定路径(staticType 为空,按运行时类包装)
        val viaBinding = RhinoScriptEngine.eval("result.attr('href')", bindings)
        Assert.assertEquals("/artist/1", viaBinding)
    }

    /**
     * java.getElements 的返回列表须保留原始集合类型:书源 JS 依赖 JSoup Elements
     * 自身的方法(attr/text/html 等),归一化拷贝成普通 ArrayList 会让这些调用报
     * "找不到函数 attr"。
     */
    @Test
    fun analyzeRuleGetElementsKeepsCollectionMethods() {
        val analyzeRule = io.legado.app.model.analyzeRule.AnalyzeRule()
        analyzeRule.setContent(
            """<div id="video-artist-name"><a href="/artist/1">n</a></div>"""
        )
        val bindings = ScriptBindings()
        bindings["java"] = analyzeRule
        val result = RhinoScriptEngine.eval(
            "java.getElements('#video-artist-name a').attr('href')", bindings
        )
        Assert.assertEquals("/artist/1", result)
    }

    /**
     * ES6 兼容性边界探针(2026-07-17 随引擎 5.3.0-legado.1 重定界),书源 skill 的兼容表
     * 依据(legado-source-skill/references/js-api.md)。引擎(htmlunit-core-js)升级后
     * 若本测试翻红,说明支持边界变了,同步更新兼容表。
     */
    @Test
    fun es6CompatBoundary() {
        // 解析期报错的语法:class / async / 函数调用展开
        listOf(
            "class A { }; new A()",
            "typeof (async () => 42)",
            "Math.max(...[1, 2, 5])",
        ).forEach { js ->
            val outcome = runCatching { RhinoScriptEngine.eval(js, ScriptBindings()) }
            Assert.assertTrue("引擎已支持(原判不支持): $js", outcome.isFailure)
        }
        // Promise 构造器存在、then 可注册,但无事件循环微任务不排水——回调从不执行,
        // Promise 链在书源 JS 里实际不可用(勿作为 async/await 的替代方案)
        @Language("js")
        val promiseJs = "var r = 0; Promise.resolve(7).then(function(v){ r = v }); '' + r"
        Assert.assertEquals("0", RhinoScriptEngine.eval(promiseJs, ScriptBindings()))
    }

    /**
     * ES6+ 支持面正面清单(es6CompatBoundary 的反面):模板/文档允许使用的新语法
     * 逐条锚定在此,升级 rhino 版本后跑此测试重新定界。
     * 每条求值结果同时断言,防"解析通过但语义错"。
     */
    @Test
    fun es6SupportedFeatures() {
        listOf(
            // 语法 to 期望值
            "let x = 1; const y = 2; '' + (x + y)" to "3",
            "var f = (a, b) => a + b; '' + f(1, 2)" to "3",
            "var n = 6; `p\${n}q`" to "p6q",
            "var s = 0; for (var v of [1, 2, 3]) s += v; '' + s" to "6",
            "var {a, b} = {a: 1, b: 2}; '' + (a + b)" to "3",
            "var [p, q] = [7, 8]; '' + (p + q)" to "15",
            "function g(a, b) { b = b || 10; return a + b }; '' + g(5)" to "15",
            "var k = 'dyn'; var o = {[k]: 9, m() { return this[k] } }; '' + o.m()" to "9",
            "var arr = [1, 2]; var arr2 = [0].concat(arr); '' + arr2.length" to "3",
            "'' + [3, 1, 2].includes(2)" to "true",
            "'' + Object.assign({}, {a: 1}, {b: 2}).b" to "2",
            "'' + Array.from('ab').length" to "2",
            "'' + 'x'.repeat(3)" to "xxx",
            "'' + '5'.padStart(3, '0')" to "005",
            "function d(a, b = 10) { return a + b }; '' + d(5)" to "15",
            "var a1 = [1, 2]; var a2 = [0, ...a1]; '' + a2.length" to "3",
            "var o1 = {a: 1}; var o2 = {...o1, b: 2}; '' + (o2.a + o2.b)" to "3",
            "var u = {v: {w: 5}}; '' + (u.v?.w)" to "5",
            "var z = null; '' + (z ?? 'dft')" to "dft",
            "var la = null; la ??= 5; '' + la" to "5",
            "var lb = 1; lb &&= 7; '' + lb" to "7",
            "var lc = 0; lc ||= 9; '' + lc" to "9",
            "function r(a, ...rest) { return '' + rest.length }; r(1, 2, 3)" to "2",
            "let [h, ...t] = [1, 2, 3]; t.join('-')" to "2-3",
            "var {q1, ...qr} = {q1: 1, q2: 2}; '' + qr.q2" to "2",
        ).forEach { (js, expect) ->
            val outcome = runCatching { RhinoScriptEngine.eval(js, ScriptBindings()) }
            Assert.assertEquals("求值失败或结果不符: $js -> ${outcome.exceptionOrNull()?.message}",
                expect, outcome.getOrNull())
        }
        // 顶层声明可见性:JsSourceConfig.extract 经 ScriptableObject.getProperty 取
        // config/函数,var/let/const 三种顶层声明均须可见
        listOf("var", "let", "const").forEach { kw ->
            val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
            RhinoScriptEngine.eval("$kw config = { a: 1 }", scope)
            val found = org.htmlunit.corejs.javascript.ScriptableObject.getProperty(scope, "config")
            Assert.assertNotEquals("顶层 $kw 声明经 getProperty 不可见",
                org.htmlunit.corejs.javascript.Scriptable.NOT_FOUND, found)
        }
    }

    /**
     * jsLib 函数动态 this 探针(FEATURE_LEGADO_DYNAMIC_DEFAULT_THIS,引擎 fork 补丁
     * JSFunction.getThisObj):共享作用域(jsLib)里声明的非严格函数被书源裸调用时,
     * this = 当次执行环境的 globalThis——社区书源 `const { java, cache } = this` 惯用法
     * 依赖此语义。引擎升级后本测试翻红即补丁丢失。
     */
    @Test
    fun jsLibFunctionDynamicThis() {
        val shared = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
        RhinoScriptEngine.eval("function libFn() { return this.cache }", shared)
        val bindings = ScriptBindings()
        bindings["cache"] = "EXEC_ENV"
        bindings.chainTo(shared)
        Assert.assertEquals("EXEC_ENV", RhinoScriptEngine.eval("libFn()", bindings))
    }

    /**
     * 间接 eval 动态 realm 探针(FEATURE_LEGADO_DYNAMIC_EVAL_REALM,引擎 fork 补丁
     * NativeGlobal.js_eval/BaseFunction.dynamicConstructorScope):经函数对象调用的 eval
     * ((0,eval)/别名/this.eval)与 Function 构造器在当次顶层调用作用域求值,
     * java/cookie 等运行时绑定对被 eval 代码可见——书源混淆代码常用此形态调
     * java.createSymmetricCrypto 等能力。引擎升级后本测试翻红即补丁丢失。
     */
    @Test
    fun indirectEvalDynamicRealm() {
        val bindings = ScriptBindings()
        bindings["cache"] = "EXEC_ENV"
        Assert.assertEquals("EXEC_ENV", RhinoScriptEngine.eval("(0, eval)('cache')", bindings))
        Assert.assertEquals("EXEC_ENV", RhinoScriptEngine.eval("new Function('return cache')()", bindings))
        // 对照:直接 eval(裸名调用,SPECIALCALL_EVAL)不经此特性,天生走调用处词法链——
        // 运行时绑定与函数局部同时可见;间接形态回顶层 scope,函数局部不可见
        Assert.assertEquals("EXEC_ENV-L", RhinoScriptEngine.eval(
            "function f() { var loc = '-L'; return eval('cache + loc') }; f()", bindings))
        // jsLib 函数体内经 this.eval 的间接调用同样回当次执行环境
        val shared = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
        RhinoScriptEngine.eval("function libEval(code) { return this.eval(code) }", shared)
        val chained = ScriptBindings()
        chained["cache"] = "EXEC_ENV2"
        chained.chainTo(shared)
        Assert.assertEquals("EXEC_ENV2", RhinoScriptEngine.eval("libEval('cache')", chained))
    }

    /**
     * Jsoup 经顶层包对象可达(RhinoClassShutter 未拦 org.jsoup)——
     * JS 源(java=JsExtensions,无 AnalyzeRule 选择器函数)的 HTML 解析通道。
     */
    @Test
    fun jsoupFromJsScope() {
        @Language("js")
        val js = """
            var doc = org.jsoup.Jsoup.parse('<div><a class="t">x</a><a class="t">y</a></div>')
            var out = []
            for (var e of doc.select('a.t')) out.push(e.text())
            out.join(',')
        """.trimIndent()
        Assert.assertEquals("x,y", RhinoScriptEngine.eval(js, ScriptBindings()))
    }

    /**
     * Java 互操作字符串边界探针(2026-07-17 实测锚定),书源 skill 归一化条目的依据
     * (legado-source-skill/references/js-source-format.md、js-api.md)。
     * 裸绑定字符串(result/key/baseUrl)即 Rhino 原生 string,JS 方法全量可用;
     * 经 Java 对象成员访问取得的字符串(实体属性、Jsoup text()/attr()、java.* 返回值)
     * 是 WrapFactory 包装对象:同名 Java 方法优先分派,未被遮蔽的名字回落
     * String.prototype;String() 归一化后与原生 string 无差。
     */
    @Test
    fun javaStringInteropBoundary() {
        val chapter = BookChapter(title = "第1章", url = "https://a/b/", tag = "")
        val bindings = ScriptBindings()
        bindings["chapter"] = chapter
        fun ev(js: String) = RhinoScriptEngine.eval(js, bindings)

        // 包装对象体征:typeof object、length 是 Java 方法非属性、空串真值、
        // === 与同文本 JS 字符串不等(== 相等)
        Assert.assertEquals("object", ev("typeof chapter.title"))
        Assert.assertEquals("object", ev("typeof org.jsoup.Jsoup.parse('<a>x</a>').select('a').text()"))
        Assert.assertEquals("function", ev("typeof chapter.title.length"))
        Assert.assertEquals("T", ev("chapter.tag ? 'T' : 'F'"))
        Assert.assertEquals("false:true", ev("(chapter.title === '第1章') + ':' + (chapter.title == '第1章')"))

        // 同名 Java 方法优先分派:(RegExp, string) 对 String.replace 两个重载都不唯一 → 歧义报错;
        // split 走 Java 语义,尾部空串被丢弃(JS 语义应为 5)
        val ambiguous = runCatching { ev("chapter.url.replace(/b/, 'X')") }
        Assert.assertTrue("正则 replace 应因 Java 重载歧义报错", ambiguous.isFailure)
        Assert.assertEquals("4", ev("'' + chapter.url.split('/').length"))

        // 未被 Java 遮蔽的名字回落 String.prototype;Java 自有方法可直调
        Assert.assertEquals("M", ev("chapter.title.match(/1/) ? 'M' : 'N'"))
        Assert.assertEquals("0", ev("'' + chapter.url.indexOf('http')"))

        // String() 归一化后 JS 全套可用;包装串作返回对象字段值经 JSON.stringify 正确解包
        Assert.assertEquals("https://a/X/", ev("String(chapter.url).replace(/b/, 'X')"))
        Assert.assertEquals("""{"u":"https://a/b/"}""", ev("JSON.stringify({u: chapter.url})"))
    }

    @Test
    fun typeofString() {
        val bindings = ScriptBindings()
        @Language("js")
        val js = """
            s = "" + String()
            typeof s
        """.trimIndent()
        val result = RhinoScriptEngine.eval(js, bindings)
        Assert.assertEquals(result, "string")
    }

    // 取消契约锚:3 参 eval 挂协程上下文后,指令计数中断使死循环可被 job.cancel 终止
    @Test
    fun evalCancellationViaCoroutineContext() = runBlocking {
        val job = launch(Dispatchers.IO) {
            val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
            runCatching {
                RhinoScriptEngine.eval("while(true){}", scope, coroutineContext)
            }
        }
        delay(500)
        job.cancel()
        withTimeout(5000) { job.join() }
    }

}
