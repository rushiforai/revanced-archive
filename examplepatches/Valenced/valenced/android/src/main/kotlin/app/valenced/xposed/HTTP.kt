package app.valenced.xposed

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

internal val httpClient by lazy {
    HttpClient(CIO) {
        expectSuccess = false
        install(UserAgent) { agent = ValencedConstants.USER_AGENT }
        install(HttpRedirect) {}
        install(HttpTimeout) {}
    }
}

internal fun HttpResponse.refusedHttpsDowngradeTarget(): Url? {
    // Same as HttpRedirect's (private) isRedirect()
    if (status.value !in setOf(301, 302, 303, 307, 308)) return null
    val location = headers[HttpHeaders.Location] ?: return null
    val target = URLBuilder(request.url).takeFrom(location).build()
    return target.takeIf { request.url.protocol.isSecure() && !it.protocol.isSecure() }
}

internal sealed class ETagFetchResult {
    /** A fresh body was fetched. */
    class Fetched(val bytes: ByteArray, val etag: String?) : ETagFetchResult()

    /** The server responded `304 Not Modified`. The cached copy is up-to-date. */
    object NotModified : ETagFetchResult()
}

internal suspend fun HttpClient.getWithETag(
    url: String,
    etag: String?,
    timeoutMillis: Long? = null,
): ETagFetchResult {
    val response = get(url) {
        etag?.let { headers.append(HttpHeaders.IfNoneMatch, it) }
        timeoutMillis?.let { timeout { requestTimeoutMillis = it } }
    }

    return when (response.status) {
        HttpStatusCode.OK -> ETagFetchResult.Fetched(
            bytes = response.body(),
            etag = response.headers[HttpHeaders.ETag]?.takeIf { it.isNotEmpty() },
        )

        HttpStatusCode.NotModified -> ETagFetchResult.NotModified

        else -> throw ResponseException(
            response,
            response.refusedHttpsDowngradeTarget()
                ?.let { "$url redirects to insecure $it. The host should enforce HTTPS." }
                ?: "Received status: ${response.status}",
        )
    }
}