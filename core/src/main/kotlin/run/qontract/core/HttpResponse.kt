package run.qontract.core

import io.ktor.http.HttpStatusCode
import run.qontract.core.pattern.parsedValue
import run.qontract.core.utilities.nativeMapToJsonString
import run.qontract.core.utilities.prettifyJsonString
import run.qontract.core.utilities.valueMapToPrettyJsonString
import run.qontract.core.value.*
import java.util.*

data class HttpResponse(var status: Int = 0, var body: String? = "", val headers: MutableMap<String, String> = mutableMapOf("Content-Type" to "text/plain")) {
    val statusText: String
        get() =
            when(status) {
                0 -> ""
                else -> HttpStatusCode.fromValue(status).description
            }

    fun updateBodyWith(content: Value): HttpResponse {
        body = content.toString()
        headers.put("Content-Type", content.httpContentType)
        return this
    }

    fun toJSON(): MutableMap<String, Value> =
        mutableMapOf<String, Value>().also { json ->
            json["status"] = NumberValue(status)
            json["body"] = body?.let { StringValue(it) } ?: EmptyString
            if (statusText.isNotEmpty()) json["status-text"] = StringValue(statusText)
            if (headers.isNotEmpty()) json["headers"] = JSONObjectValue(headers.mapValues { StringValue(it.value) })
        }

    fun toLogString(prefix: String = ""): String {
        val statusLine = "$status $statusText"
        val headerString = headers.map { "${it.key}: ${it.value}" }.joinToString("\n")

        val firstPart = listOf(statusLine, headerString).joinToString("\n").trim()

        val trimmedBody = body?.trim() ?: ""
        val formattedBody = if(trimmedBody.startsWith("{") || trimmedBody.startsWith("[")) prettifyJsonString(trimmedBody) else trimmedBody

        val responseString = listOf(firstPart, "", formattedBody).joinToString("\n")
        return startLinesWith(responseString, prefix)
    }

    companion object {
        var HTTP_400 = HttpResponse(400, "This request did not match any scenario.", HashMap())
        var EMPTY_200 = HttpResponse(200, "", HashMap())

        fun jsonResponse(jsonData: String?): HttpResponse {
            return HttpResponse(200, jsonData, object : HashMap<String, String>() {
                init {
                    put("Content-Type", "application/json")
                }
            })
        }

        fun xmlResponse(xmlData: String?): HttpResponse {
            return HttpResponse(200, xmlData, object : HashMap<String, String>() {
                init {
                    put("Content-Type", "application/xml")
                }
            })
        }

        fun from(status: Int, body: String?) = bodyToHttpResponse(body, status)

        private fun bodyToHttpResponse(body: String?, status: Int): HttpResponse {
            val bodyValue = parsedValue(body)
            return HttpResponse(status, bodyValue.toString(), mutableMapOf("Content-Type" to bodyValue.httpContentType))
        }

        fun fromJSON(jsonObject: Map<String, Value>) =
            HttpResponse(
                Integer.parseInt(jsonObject["status"].toString()),
                jsonObject.getOrDefault("body", StringValue()).toString(),
                getHeaders(jsonObject))
    }
}

fun getHeaders(jsonObject: Map<String, Value>): MutableMap<String, String> =
        (jsonObject.getOrDefault("headers", JSONObjectValue()) as JSONObjectValue).jsonObject.mapValues {
            it.value.toString()
        }.toMutableMap()