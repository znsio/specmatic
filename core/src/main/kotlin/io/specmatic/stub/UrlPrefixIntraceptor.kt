package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.URLParts
import io.specmatic.core.decodePath
import io.specmatic.core.utilities.Flags.Companion.BASE_URL

class UrlPrefixIntraceptor : RequestInterceptor {
    override fun interceptRequest(httpRequest: HttpRequest): HttpRequest? {

        val baseUrl = System.getProperty(BASE_URL)
        val decodedPath = urlDecodePathSegments(httpRequest.path!!, baseUrl)
        return httpRequest.copy(path = decodedPath)
    }
    private fun urlDecodePathSegments(url: String, baseURL: String): String {
        val normalizedBaseURL = baseURL?.let { "/${it.trim('/')}" }
        val relativePath = getRelativePath(url, normalizedBaseURL)

        return if (relativePath.contains("://")) {
            URLParts(relativePath).withDecodedPathSegments()
        } else {
            decodePath(relativePath)
        }
    }

    private fun getRelativePath(url: String, normalizedBaseURL: String?): String {
        return when {
            normalizedBaseURL == null || normalizedBaseURL == "/" -> url
            url.contains(normalizedBaseURL) -> {
                url.removePrefix(normalizedBaseURL).takeIf { it.startsWith("/") } ?: ""
            }
            else -> ""
        }
    }

}
