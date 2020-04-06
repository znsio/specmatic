package run.qontract.core

import run.qontract.core.utilities.URIUtils.parseQuery
import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value
import run.qontract.mock.HttpMockException
import io.netty.buffer.ByteBuf
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

data class HttpRequest(var method: String? = null, var path: String? = null, val headers: HashMap<String, String> = HashMap(), var body: Value? = EmptyString, var queryParams: HashMap<String, String> = HashMap(), val formFields: Map<String, String> = emptyMap()) {
    fun updateQueryParams(queryParams: Map<String, String>) {
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

    fun updateHeader(key: String, value: String): HttpRequest {
        headers[key] = value
        return this
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

    fun toJSON(): Map<String, Value> {
        val requestMap = mutableMapOf<String, Value>()

        requestMap["path"] = path?.let { StringValue(it) } ?: StringValue("/")
        method?.let { requestMap["method"] = StringValue(it) } ?: throw HttpMockException("Can't serialise the request without a method.")
        body?.let { requestMap["body"] = it }

        if (queryParams.size > 0) requestMap["query"] = JSONObjectValue(queryParams.mapValues { StringValue(it.value) })
        if (headers.size > 0) requestMap["headers"] = JSONObjectValue(headers.mapValues { StringValue(it.value) })

        return requestMap
    }

    fun setHeaders(addedHeaders: Map<String, String>) {
        headers.putAll(addedHeaders)
    }

    fun toLogString(prefix: String = ""): String {
        val methodString = method ?: "NO_METHOD"

        val pathString = path ?: "NO_PATH"
        val queryParamString = queryParams.map { "${it.key}=${it.value}"}.joinToString("&").let { if(it.isNotEmpty()) "?$it" else it }
        val urlString = "$pathString$queryParamString"

        val firstLine = "$methodString $urlString"
        val headerString = headers.map { "${it.key}: ${it.value}" }.joinToString("\n")
        val bodyString = when {
            formFields.isNotEmpty() -> formFields.map { "${it.key}=${it.value}"}.joinToString("&")
            else -> body.toString()
        }

        val firstPart = listOf(firstLine, headerString).joinToString("\n").trim()
        val requestString = listOf(firstPart, "", bodyString).joinToString("\n")
        return startLinesWith(requestString, prefix)
    }
}

fun s(json: Map<String, Value>, key: String): String = (json.getValue(key) as StringValue).string

fun requestFromJSON(json: Map<String, Value>): HttpRequest {
    val httpRequest = HttpRequest()
    httpRequest.updateMethod(s(json, "method"))
    httpRequest.updatePath(if ("path" in json) s(json, "path") else "/")
    httpRequest.updateQueryParams(if ("query" in json)
        (json["query"] as JSONObjectValue).jsonObject.mapValues { it.value.toString() }
    else emptyMap())
    httpRequest.setHeaders(if ("headers" in json) (json["headers"] as JSONObjectValue).jsonObject.mapValues { it.value.toString() } else emptyMap())

    if ("body" in json)
        httpRequest.updateBody(json.getValue("body"))

    return httpRequest
}

internal fun startLinesWith(str: String, startValue: String): String {
    return str.split("\n").map { "$startValue$it" }.joinToString("\n")
}

