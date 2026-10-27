package io.legado.app.help.http

import io.legado.app.help.http.api.CookieManagerInterface

/** Cookie facade exposed to a scheduled task's JavaScript environment. */
class TaskCookieStore(
    private val scope: String,
    private val sourceKey: String
) : CookieManagerInterface {

    override fun setCookie(url: String, cookie: String?) {
        if (cookie.isNullOrBlank()) {
            CookieStore.removeTaskCookie(scope, url)
        } else {
            CookieStore.setTaskCookie(scope, url, cookie)
        }
    }

    override fun replaceCookie(url: String, cookie: String) {
        if (cookie.isBlank()) {
            CookieStore.removeTaskCookie(scope, url)
            return
        }
        CookieStore.mergeTaskCookie(scope, url, cookie)
    }

    override fun getCookie(url: String): String = CookieStore.getCookie(scope, url)

    override fun getCookie(scope: String, url: String): String = getCookie(url)

    override fun removeCookie(url: String) {
        CookieStore.removeTaskCookie(this.scope, url)
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> = CookieStore.cookieToMap(cookie)

    override fun mapToCookie(cookieMap: Map<String, String>?): String? = CookieStore.mapToCookie(cookieMap)

    fun getKey(url: String, key: String): String = CookieStore.cookieToMap(getCookie(url))[key].orEmpty()
}
