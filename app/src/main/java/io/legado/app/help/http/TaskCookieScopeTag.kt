package io.legado.app.help.http

/** Internal OkHttp metadata; never serialized into an HTTP header. */
data class CookieJarRequestTag(
    val taskScope: String?,
    val cookieHeaderGenerated: Boolean = false,
    val explicitCookieHeader: String? = null,
    val explicitCookieHost: String? = null
)
