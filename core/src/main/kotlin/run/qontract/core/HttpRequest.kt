package run.qontract.core

import run.qontract.core.utilities.URIUtils.parseQuery
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.NoValue
import run.qontract.core.value.Value
import run.qontract.mock.HttpMockException
import io.netty.buffer.ByteBuf
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

data class HttpRequest(var method: String? = null, var path: String? = null, val headers: HashMap<String?, String?> = HashMap(), var body: Value? = NoValue(), var queryParams: HashMap<String, String> = HashMap(), val formFields: Map<String, String> = emptyMap()) {
    private fun updateQueryParams(queryParams: Map<String, String>) {
        this.queryParams.putAll(queryParams)
    }

    fun updatePath(path: String): HttpRequest {
        try {
            val urlParam = URI(path)
            updateWith(urlParam)
        } catch (e: URISyntaxException) {
            this.path = path
        } catch (e: UnsupportedEncodingException) {
            this.path = path
        }
        return this
    }

    fun updateQueryParam(key: String, value: String): HttpRequest {
        queryParams[key] = value
        return this
    }

    fun updateBody(body: Value): HttpRequest {
        this.body = body
        return this
    }

    fun updateBody(body: String?): HttpRequest {
        this.body = parsedValue(body)
        return this
    }

    fun updateWith(url: URI) {
        path = url.path
        queryParams = parseQuery(url.query)
    }

    fun updateMethod(name: String): HttpRequest {
        method = name.toUpperCase()
        return this
    }

    private fun updateBody(contentBuffer: ByteBuf) {
        val bodyString = contentBuffer.toString(Charset.defaultCharset())
        updateBody(bodyString)
    }

    fun updateHeader(key: String?, value: String?) {
        headers[key] = value
    }

    val bodyString: String
        get() = body.toString()

    fun getURL(baseURL: String?): String {
        val url = StringBuilder().append(baseURL).append(path)
        if (!queryParams.isEmpty()) {
            val keys: Set<String?> = queryParams.keys
            val parts: MutableList<String> = ArrayList()
            for (key in keys) {
                val value = queryParams[key]
                parts.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.toString()))
            }
            val joinedQueryParams = java.lang.String.join("&", parts)
            url.append("?").append(joinedQueryParams)
        }
        return url.toString()
    }

    fun toJSON(): Map<String, Any?> {
        val requestMap = mutableMapOf<String, Any?>()

        if (path != null) requestMap["path"] = path
        if (queryParams.size > 0) requestMap["query"] = queryParams
        if (method == null) throw HttpMockException("Can't serialise the request without a method.")
        requestMap["method"] = method
        if (headers.size > 0) requestMap["headers"] = headers
        if (body != null) requestMap["body"] = body.toString()
        return requestMap
    }

    private fun setHeaders(addedHeaders: Map<String, String>) {
        headers.putAll(addedHeaders)
    }

    companion object {
        fun fromJSON(json: Map<String, Any?>): HttpRequest {
            val httpRequest = HttpRequest()
            httpRequest.updateMethod(json["method"] as String)
            httpRequest.updatePath(if ("path" in json) json["path"] as String else "/")
            httpRequest.updateQueryParams(if ("query" in json)
                (json["query"] as Map<String, Any>).mapValues { it.value.toString() }
            else HashMap())
            httpRequest.setHeaders(if ("headers" in json) json["headers"] as Map<String, String> else HashMap())
            if ("body" in json) {
                httpRequest.updateBody(json["body"] as String)
            }
            return httpRequest
        }
    }
}
