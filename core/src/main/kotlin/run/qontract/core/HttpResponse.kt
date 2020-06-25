package run.qontract.core

import io.ktor.http.HttpStatusCode
import run.qontract.conversions.guessType
import run.qontract.core.GherkinSection.Then
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.*

data class HttpResponse(val status: Int = 0, val headers: Map<String, String> = mapOf("Content-Type" to "text/plain"), val body: Value? = EmptyString) {
    constructor(status: Int = 0, body: String? = "", headers: Map<String, String> = mapOf("Content-Type" to "text/plain")) : this(status, headers, body?.let { parsedValue(it) } ?: EmptyString)

    private val statusText: String
        get() =
            when(status) {
                0 -> ""
                else -> HttpStatusCode.fromValue(status).description
            }

    fun updateBodyWith(content: Value): HttpResponse {
        return copy(body = content, headers = headers.minus("Content-Type").plus("Content-Type" to content.httpContentType))
    }

    fun toJSON(): MutableMap<String, Value> =
        mutableMapOf<String, Value>().also { json ->
            json["status"] = NumberValue(status)
            json["body"] = body ?: EmptyString
            if (statusText.isNotEmpty()) json["status-text"] = StringValue(statusText)
            if (headers.isNotEmpty()) json["headers"] = JSONObjectValue(headers.mapValues { StringValue(it.value) })
        }

    fun toLogString(prefix: String = ""): String {
        val statusLine = "$status $statusText"
        val headerString = headers.map { "${it.key}: ${it.value}" }.joinToString("\n")

        val firstPart = listOf(statusLine, headerString).joinToString("\n").trim()

        val formattedBody = (body ?: EmptyString).toStringValue()

        val responseString = listOf(firstPart, "", formattedBody).joinToString("\n")
        return startLinesWith(responseString, prefix)
    }

    companion object {
        val ERROR_400 = HttpResponse(400, "This request did not match any scenario.", emptyMap())
        val OK = HttpResponse(200, emptyMap())
        fun OK(body: Number): HttpResponse {
            val bodyValue = NumberValue(body)
            return HttpResponse(200, mapOf("Content-Type" to bodyValue.httpContentType), bodyValue)
        }
        fun OK(body: Value) = HttpResponse(200, mapOf("Content-Type" to body.httpContentType), body)
        val EMPTY = HttpResponse(0, emptyMap())

        fun jsonResponse(jsonData: String?): HttpResponse {
            return HttpResponse(200, jsonData, mapOf("Content-Type" to "application/json"))
        }

        fun xmlResponse(xmlData: String?): HttpResponse {
            return HttpResponse(200, xmlData, mapOf("Content-Type" to "application/xml"))
        }

        fun from(status: Int, body: String?) = bodyToHttpResponse(body, status)

        private fun bodyToHttpResponse(body: String?, status: Int): HttpResponse {
            val bodyValue = parsedValue(body)
            return HttpResponse(status, mutableMapOf("Content-Type" to bodyValue.httpContentType), bodyValue)
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


fun getHeaders(jsonObject: Map<String, Value>): MutableMap<String, String> =
        (jsonObject.getOrDefault("headers", JSONObjectValue()) as JSONObjectValue).jsonObject.mapValues {
            it.value.toString()
        }.toMutableMap()

val responseHeadersToExcludeFromConversion = listOf("Vary", "X-Qontract-Result")

fun toGherkinClauses(response: HttpResponse, types: Map<String, Pattern> = emptyMap()): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclaration> {
    return try {
        val cleanedUpResponse = dropContentAndCORSResponseHeaders(response)

        return Triple(emptyList<GherkinClause>(), types, ExampleDeclaration()).let { (clauses, types, examples) ->
            val status = when {
                cleanedUpResponse.status > 0 -> cleanedUpResponse.status
                else -> throw ContractException("Can't generate a contract without a response status")
            }
            Triple(clauses.plus(GherkinClause("status $status", Then)), types, examples)
        }.let { (clauses, types, _) ->
            val (newClauses, newTypes, _) = headersToGherkin(cleanedUpResponse.headers, "response-header", types, ExampleDeclaration(), Then)
            Triple(clauses.plus(newClauses), newTypes, ExampleDeclaration())
        }.let { (clauses, types, examples) ->
            when (val result = responseBodyToGherkinClauses("ResponseBody", cleanedUpResponse.body?.let { guessType(it) }, types)) {
                null -> Triple(clauses, types, examples)
                else -> {
                    val (newClauses, newTypes, _) = result
                    Triple(clauses.plus(newClauses), newTypes, ExampleDeclaration())
                }
            }
        }
    } catch(e: NotImplementedError) {
        Triple(emptyList(), types, ExampleDeclaration())
    }
}

fun dropContentAndCORSResponseHeaders(response: HttpResponse) =
        response.copy(headers = response.headers.filterNot { it.key in responseHeadersToExcludeFromConversion || it.key.startsWith("Content-") || it.key.startsWith("Access-Control-") })
