package run.qontract.core

import io.ktor.http.*
import run.qontract.conversions.guessType
import run.qontract.core.GherkinSection.Then
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.*

internal const val QONTRACT_RESULT_HEADER = "X-Qontract-Result"

data class HttpResponse(val status: Int = 0, val headers: Map<String, String> = mapOf(CONTENT_TYPE to "text/plain"), val body: Value = EmptyString) {
    constructor(status: Int = 0, body: String? = "", headers: Map<String, String> = mapOf(CONTENT_TYPE to "text/plain")) : this(status, headers, body?.let { parsedValue(it) } ?: EmptyString)

    private val statusText: String
        get() =
            when(status) {
                0 -> ""
                else -> HttpStatusCode.fromValue(status).description
            }

    fun updateBodyWith(content: Value): HttpResponse {
        return copy(body = content, headers = headers.minus(CONTENT_TYPE).plus(CONTENT_TYPE to content.httpContentType))
    }

    fun toJSON(): JSONObjectValue =
        JSONObjectValue(mutableMapOf<String, Value>().also { json ->
            json["status"] = NumberValue(status)
            json["body"] = body
            if (statusText.isNotEmpty()) json["status-text"] = StringValue(statusText)
            if (headers.isNotEmpty()) json["headers"] = JSONObjectValue(headers.mapValues { StringValue(it.value) })
        })

    fun toLogString(prefix: String = ""): String {
        val statusLine = "$status $statusText"
        val headerString = headers.map { "${it.key}: ${it.value}" }.joinToString("\n")

        val firstPart = listOf(statusLine, headerString).joinToString("\n").trim()

        val formattedBody = body.toStringValue()

        val responseString = listOf(firstPart, "", formattedBody).joinToString("\n")
        return startLinesWith(responseString, prefix)
    }

    fun selectValue(selector: String): String {
        return when {
            selector.startsWith("response-header.") -> {
                val headerName = selector.removePrefix("response-header.").trim()
                this.headers[headerName] ?: throw ContractException("Couldn't find header name $headerName specified in $selector")
            }
            selector.startsWith("response-body") -> {
                val bodySelector = selector.removePrefix("response-body").trim()
                if(bodySelector.isBlank())
                    this.body.toStringValue()
                else {
                    if(this.body !is JSONObjectValue)
                        throw ContractException("JSON selector can only be used for JSON body")

                    val jsonBodySelector = bodySelector.removePrefix(".")
                    this.body.findFirstChildByPath(jsonBodySelector)?.toStringValue() ?: throw ContractException("JSON selector $selector was not found")
                }
            }
            else -> throw ContractException("Selector $selector is unexpected. It must either start with response-header or response-body.")
        }
    }

    fun export(bindings: Map<String, String>): Map<String, String> {
        return bindings.entries.fold(emptyMap()) { acc, setter ->
            acc.plus(setter.key to selectValue(setter.value))
        }
    }

    companion object {
        val ERROR_400 = HttpResponse(400, "This request did not match any scenario.", emptyMap())
        val OK = HttpResponse(200, emptyMap())
        fun OK(body: Number): HttpResponse {
            val bodyValue = NumberValue(body)
            return HttpResponse(200, mapOf(CONTENT_TYPE to bodyValue.httpContentType), bodyValue)
        }
        fun OK(body: String): HttpResponse {
            val bodyValue = StringValue(body)
            return HttpResponse(200, mapOf(CONTENT_TYPE to bodyValue.httpContentType), bodyValue)
        }
        fun OK(body: Value) = HttpResponse(200, mapOf(CONTENT_TYPE to body.httpContentType), body)
        val EMPTY = HttpResponse(0, emptyMap())

        fun jsonResponse(jsonData: String?): HttpResponse {
            return HttpResponse(200, jsonData, mapOf(CONTENT_TYPE to "application/json"))
        }

        fun xmlResponse(xmlData: String?): HttpResponse {
            return HttpResponse(200, xmlData, mapOf(CONTENT_TYPE to "application/xml"))
        }

        fun from(status: Int, body: String?) = bodyToHttpResponse(body, status)

        private fun bodyToHttpResponse(body: String?, status: Int): HttpResponse {
            val bodyValue = parsedValue(body)
            return HttpResponse(status, mutableMapOf(CONTENT_TYPE to bodyValue.httpContentType), bodyValue)
        }

        fun fromJSON(jsonObject: Map<String, Value>): HttpResponse {
            val body = jsonObject["body"]
            if(body is NullValue)
                throw ContractException("Either body should have a value or the key should be absent from http-request")

            return HttpResponse(
                    nativeInteger(jsonObject, "status") ?: throw ContractException("http-response must contain a key named status, whose value is the http status in the response"),
                    nativeStringStringMap(jsonObject, "headers").toMutableMap(),
                    jsonObject.getOrDefault("body", StringValue()))
        }
    }
}

fun nativeInteger(json: Map<String, Value>, key: String): Int? {
    val keyValue = json[key] ?: return null

    val errorMessage = "$key must be an integer"
    if(keyValue is StringValue)
        return try { keyValue.string.toInt() } catch(e: Throwable) { throw ContractException(errorMessage) }

    if(keyValue !is NumberValue)
        throw ContractException("Expected $key to be a string value")

    return try { keyValue.number.toInt() } catch(e: Throwable) { throw ContractException(errorMessage) }
}


val responseHeadersToExcludeFromConversion = listOf("Vary", QONTRACT_RESULT_HEADER)

fun toGherkinClauses(response: HttpResponse, types: Map<String, Pattern> = emptyMap()): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    return try {
        val cleanedUpResponse = dropContentAndCORSResponseHeaders(response)

        return Triple(emptyList<GherkinClause>(), types, DiscardExampleDeclarations()).let { (clauses, types, examples) ->
            val status = when {
                cleanedUpResponse.status > 0 -> cleanedUpResponse.status
                else -> throw ContractException("Can't generate a contract without a response status")
            }
            Triple(clauses.plus(GherkinClause("status $status", Then)), types, examples)
        }.let { (clauses, types, _) ->
            val (newClauses, newTypes, _) = headersToGherkin(cleanedUpResponse.headers, "response-header", types, DiscardExampleDeclarations(), Then)
            Triple(clauses.plus(newClauses), newTypes, DiscardExampleDeclarations())
        }.let { (clauses, types, examples) ->
            when (val result = responseBodyToGherkinClauses("ResponseBody", guessType(cleanedUpResponse.body), types)) {
                null -> Triple(clauses, types, examples)
                else -> {
                    val (newClauses, newTypes, _) = result
                    Triple(clauses.plus(newClauses), newTypes, DiscardExampleDeclarations())
                }
            }
        }
    } catch(e: NotImplementedError) {
        Triple(emptyList(), types, DiscardExampleDeclarations())
    }
}

fun dropContentAndCORSResponseHeaders(response: HttpResponse) =
        response.copy(headers = response.headers.filterNot { it.key in responseHeadersToExcludeFromConversion || it.key.startsWith("Content-") || it.key.startsWith("Access-Control-") })
