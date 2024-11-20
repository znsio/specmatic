package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.utilities.Flags.Companion.PATH_PREFIX

class UrlPrefixInterceptor : RequestInterceptor {
    override fun interceptRequest(httpRequest: HttpRequest): HttpRequest {

        val pathPrefix = System.getProperty(PATH_PREFIX)
        val prefixedPath = urlPrefixPathSegments(httpRequest.path!!, pathPrefix)
        return httpRequest.copy(path = prefixedPath)
    }

    private fun urlPrefixPathSegments(url: String, rawPrefix: String?): String {
        val pathPrefix = rawPrefix?.let { "/${it.trim('/')}" }
        val relativePath = getRelativePath(url, pathPrefix)
        return relativePath;
    }

    private fun getRelativePath(url: String, pathPrefix: String?): String {
        return when {
            pathPrefix == null || pathPrefix == "/" -> url
            url.contains(pathPrefix) -> {
                url.removePrefix(pathPrefix).takeIf { it.startsWith("/") } ?: ""
            }

            else -> ""
        }
    }

}
