package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.utilities.Flags.Companion.PATH_PREFIX

class  UrlPrefixInterceptor(private val serverUrlFromOpenSpecs: String?) : RequestInterceptor {
    override fun interceptRequest(httpRequest: HttpRequest): HttpRequest {

        val pathPrefix = System.getProperty(PATH_PREFIX)
        val prefixedPath = urlPrefixPathSegments(httpRequest.path!!, pathPrefix, serverUrlFromOpenSpecs)
        return httpRequest.copy(path = prefixedPath)

    }

    private fun urlPrefixPathSegments(url: String, rawPrefix: String?,serverUrlFromOpenSpecs: String?): String {
        val serverUrlPrefix = serverUrlFromOpenSpecs?.let {
            java.net.URL(it).path
        }
        val pathPrefix = rawPrefix?.let { "/${it.trim('/')}" } ?: serverUrlPrefix
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
//    val serverURL = description?.let {
//        getOpenApiSpecificationFromFilePath(getConfigFilePath()).getURLByDescription(it)
//    }
//
//    val prefixFromServerURL = serverURL?.substringBeforeLast('/')
//
//    val finalURL = pathPrefix?.let {
//        "$protocol://$host$computedPortString/${it.trim('/')}"
//    } ?: prefixFromServerURL?.let {
//        "$it"
//    } ?: "$protocol://$host$computedPortString"
//
//    return finalURL

}
