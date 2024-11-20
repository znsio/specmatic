package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.URLParts
import io.specmatic.core.decodePath

class UrlDecodeInterceptor : RequestInterceptor {
    override fun interceptRequest(httpRequest: HttpRequest): HttpRequest {
        val decodedPath = urlDecodePathSegments(httpRequest.path!!)
        return httpRequest.copy(path = decodedPath)
    }

    private fun urlDecodePathSegments(url: String): String {
        return if (url.contains("://")) {
            URLParts(url).withDecodedPathSegments()
        } else {
            decodePath(url)
        }
    }
}