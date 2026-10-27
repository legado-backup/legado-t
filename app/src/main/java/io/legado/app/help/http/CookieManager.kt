package io.legado.app.help.http

import android.webkit.CookieManager
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.help.CacheManager
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Connection

@Suppress("ConstPropertyName")
object CookieManager {
    /**
     * <domain>_session_cookie 会话期 cookie，应用重启后失效
     * <domain>_cookie cookies 缓存
     */

    const val cookieJarHeader = "CookieJar"
    const val taskCookieScopeHeader = "TaskCookieScope"

    /**
     * 从响应中保存Cookies
     */
    fun saveResponse(response: Response) {
        val url = response.request.url
        val headers = response.headers
        val taskScope = response.request
            .tag(CookieJarRequestTag::class.java)
            ?.taskScope
            ?: response.request.header(taskCookieScopeHeader)
        saveCookiesFromHeaders(url, headers, taskScope)
    }

    fun saveResponse(response: Connection.Response, taskScope: String? = null) {
        val url = response.url().toHttpUrlOrNull() ?: return
        val headers = response.multiHeaders().toHeaders()
        saveCookiesFromHeaders(url, headers, taskScope)
    }

    private fun saveCookiesFromHeaders(url: HttpUrl, headers: Headers, taskScope: String? = null) {
        val domain = NetworkUtils.getSubDomain(url.toString())
        val cookies = Cookie.parseAll(url, headers)

        if (taskScope != null) {
            CookieStore.updateTaskCookie(taskScope, url.toString(), cookies)
        } else {
            val sessionCookie = cookies.filter { !it.persistent }.getString()
            updateSessionCookie(domain, sessionCookie)
            val cookieString = cookies.filter { it.persistent }.getString()
            CookieStore.replaceCookie(domain, cookieString)
        }
    }

    /**
     * 加载Cookies到请求中
     */
    fun loadRequest(request: Request): Request {
        val url = request.url.toString()
        val requestTag = request.tag(CookieJarRequestTag::class.java)
        val taskScope = requestTag?.taskScope
            ?: request.header(taskCookieScopeHeader)
        val cookie = if (taskScope != null) {
            CookieStore.getCookie(taskScope, url)
        } else {
            CookieStore.getCookie(url)
        }
        val explicitCookie = if (requestTag?.cookieHeaderGenerated == true) {
            requestTag.explicitCookieHeader.takeIf {
                requestTag.explicitCookieHost.equals(request.url.host, ignoreCase = true)
            }
        } else {
            request.header("Cookie")
        }
        val explicitCookieHost = if (explicitCookie == null) {
            null
        } else if (requestTag?.cookieHeaderGenerated == true) {
            requestTag.explicitCookieHost
        } else {
            request.url.host
        }
        val newCookie = mergeCookies(cookie, explicitCookie)
        val newTag = CookieJarRequestTag(
            taskScope = taskScope,
            cookieHeaderGenerated = true,
            explicitCookieHeader = explicitCookie,
            explicitCookieHost = explicitCookieHost
        )

        val cleanedRequest = request.newBuilder()
            .removeHeader(cookieJarHeader)
            .removeHeader(taskCookieScopeHeader)
            .tag(CookieJarRequestTag::class.java, newTag)
            .build()
        return kotlin.runCatching {
            request.newBuilder()
                .apply {
                    if (newCookie.isNullOrBlank()) removeHeader("Cookie")
                    else header("Cookie", newCookie)
                }
                .removeHeader(cookieJarHeader)
                .removeHeader(taskCookieScopeHeader)
                .tag(CookieJarRequestTag::class.java, newTag)
                .build()
        }.onFailure {
            if (taskScope != null) {
                CookieStore.removeTaskCookie(taskScope, url)
            } else {
                CookieStore.removeCookie(url)
            }
            val msg = "设置cookie出错，已清除cookie ${NetworkUtils.getSubDomain(url)} cookie:$newCookie\n$it"
            AppLog.put(msg, it)
        }.getOrElse { cleanedRequest }
    }

    private fun getSessionCookieMap(domain: String): MutableMap<String, String>? {
        return getSessionCookie(domain)?.let { CookieStore.cookieToMap(it) }
    }

    fun getSessionCookie(domain: String): String? {
        return CacheManager.getFromMemory("${domain}_session_cookie") as? String
    }

    private fun updateSessionCookie(domain: String, cookies: String) {
        val sessionCookie = getSessionCookie(domain)
        if (sessionCookie.isNullOrEmpty()) {
            CacheManager.putMemory("${domain}_session_cookie", cookies)
            return
        }

        val ck = mergeCookies(sessionCookie, cookies) ?: return
        CacheManager.putMemory("${domain}_session_cookie", ck)
    }

    fun mergeCookies(vararg cookies: String?): String? {
        val cookieMap = mergeCookiesToMap(*cookies)
        return CookieStore.mapToCookie(cookieMap)
    }

    fun mergeCookiesToMap(vararg cookies: String?): MutableMap<String, String> {
        return cookies.filterNotNull().fold(mutableMapOf()) { acc, cookie ->
            acc.apply { putAll(CookieStore.cookieToMap(cookie)) }
        }
    }

    /**
     * 删除单个Cookie
     */
    fun removeCookie(url: String, key: String) {
        val domain = NetworkUtils.getSubDomain(url)

        getSessionCookieMap(domain)?.let {
            it.remove(key)
            val cookie = CookieStore.mapToCookie(it)
            if (cookie.isNullOrEmpty()) {
                CacheManager.deleteMemory("${domain}_session_cookie")
            } else {
                CacheManager.putMemory("${domain}_session_cookie", cookie)
            }
        }

        val cookie = getCookieNoSession(url)
        if (cookie.isNotEmpty()) {
            val cookieMap = CookieStore.cookieToMap(cookie).apply { remove(key) }
            val updatedCookie = CookieStore.mapToCookie(cookieMap)
            if (updatedCookie.isNullOrEmpty()) {
                val domain = NetworkUtils.getSubDomain(url)
                appDb.cookieDao.delete(domain)
                CacheManager.deleteMemory("${domain}_cookie")
            } else {
                CookieStore.setCookie(url, updatedCookie)
            }
        }
    }

    fun getCookieNoSession(url: String): String {
        val domain = NetworkUtils.getSubDomain(url)
        val cacheCookie = CacheManager.getFromMemory("${domain}_cookie") as? String

        return if (cacheCookie != null) {
            cacheCookie
        } else {
            val cookieBean = appDb.cookieDao.get(domain)
            cookieBean?.cookie ?: ""
        }
    }

    fun applyToWebView(
        url: String,
        taskScope: String? = null,
        cookieManager: android.webkit.CookieManager = CookieManager.getInstance(),
        onApplied: (() -> Unit)? = null
    ) {
        val baseUrl = NetworkUtils.getBaseUrl(url) ?: return onApplied?.invoke() ?: Unit
        val currentDomain = NetworkUtils.getSubDomain(url)
        val applyCookies: () -> Unit = {
            val cookieSeeds = if (taskScope != null) {
                CookieStore.getTaskCookiesByDomain(taskScope)
                    .map { (domain, cookies) ->
                        val cookieUrl = if (domain == currentDomain) {
                            baseUrl
                        } else {
                            "https://$domain"
                        }
                        cookieUrl to cookies
                    }
                    .toMutableList()
                    .apply {
                        if (none { it.first == baseUrl }) {
                            add(baseUrl to CookieStore.getCookie(taskScope, url))
                        }
                    }
            } else {
                listOf(baseUrl to CookieStore.getCookie(url))
            }
            val pending = cookieSeeds.sumOf { (_, cookies) -> cookies.splitNotBlank(";").size }
            if (pending == 0) {
                cookieManager.flush()
                onApplied?.invoke()
            } else {
                val remaining = java.util.concurrent.atomic.AtomicInteger(pending)
                cookieSeeds.forEach { (cookieUrl, cookies) ->
                    cookies.splitNotBlank(";").forEach { cookie ->
                        cookieManager.setCookie(cookieUrl, cookie) {
                            if (remaining.decrementAndGet() == 0) {
                                cookieManager.flush()
                                onApplied?.invoke()
                            }
                        }
                    }
                }
            }
        }
        if (taskScope != null) {
            // Reconcile each stored domain before the first task WebView navigation.
            cookieManager.removeAllCookies { applyCookies() }
        } else if (onApplied != null) {
            cookieManager.removeSessionCookies { applyCookies() }
        } else {
            cookieManager.removeSessionCookies(null)
            applyCookies()
        }
    }

    fun List<Cookie>.getString() = buildString {
        this@getString.forEachIndexed { index, cookie ->
            if (index > 0) append("; ")
            append(cookie.name).append('=').append(cookie.value)
        }
    }

    private fun Map<String, List<String>>.toHeaders(): Headers {
        return Headers.Builder().apply {
            this@toHeaders.forEach { (k, v) ->
                v.forEach {
                    add(k, it)
                }
            }
        }.build()
    }

}
