package run.qontract.core

import run.qontract.core.utilities.URIUtils.parseQuery
import io.netty.buffer.ByteBuf
import run.qontract.conversions.guessType
import run.qontract.core.GherkinSection.When
import run.qontract.core.pattern.*
import run.qontract.core.value.*
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class HttpRequest(val method: String? = null, val path: String? = null, val headers: Map<String, String> = emptyMap(), val body: Value? = EmptyString, val queryParams: Map<String, String> = emptyMap(), val formFields: Map<String, String> = emptyMap(), val multiPartFormData: List<MultiPartFormDataValue> = emptyList()) {
    fun updateQueryParams(queryParams: Map<String, String>): HttpRequest = copy(queryParams = queryParams.plus(queryParams))

    fun updatePath(path: String): HttpRequest {
        return try {
            val urlParam = URI(path)
            updateWith(urlParam)
        } catch (e: URISyntaxException) {
            copy(path = path)
        } catch (e: UnsupportedEncodingException) {
            copy(path = path)
        }
    }

    fun updateQueryParam(key: String, value: String): HttpRequest = copy(queryParams = queryParams.plus(key to value))

    fun updateBody(body: Value): HttpRequest = copy(body = body)

    fun updateBody(body: String?): HttpRequest = copy(body = parsedValue(body))

    fun updateWith(url: URI): HttpRequest {
        val path = url.path
        val queryParams = parseQuery(url.query)
        return copy(path = path, queryParams = queryParams)
    }

    fun updateMethod(name: String): HttpRequest = copy(method = name.toUpperCase())

    private fun updateBody(contentBuffer: ByteBuf) {
        val bodyString = contentBuffer.toString(Charset.defaultCharset())
        updateBody(bodyString)
    }

    fun updateHeader(key: String, value: String): HttpRequest = copy(headers = headers.plus(key to value))

    val bodyString: String
        get() = body.toString()

    fun getURL(baseURL: String?): String =
        "$baseURL$path" + if (queryParams.isNotEmpty()) {
            val joinedQueryParams =
                    queryParams.toList()
                            .joinToString("&") {
                                "${it.first}=${URLEncoder.encode(it.second, StandardCharsets.UTF_8.toString())}"
                            }
            "?$joinedQueryParams"
        } else ""

    fun toJSON(): Map<String, Value> {
        val requestMap = mutableMapOf<String, Value>()

        requestMap["path"] = path?.let { StringValue(it) } ?: StringValue("/")
        method?.let { requestMap["method"] = StringValue(it) } ?: throw ContractException("Can't serialise the request without a method.")
        body?.let { requestMap["body"] = it }

        if (queryParams.isNotEmpty()) requestMap["query"] = JSONObjectValue(queryParams.mapValues { StringValue(it.value) })
        if (headers.isNotEmpty()) requestMap["headers"] = JSONObjectValue(headers.mapValues { StringValue(it.value) })

        if(formFields.isNotEmpty()) requestMap["form-fields"] = JSONObjectValue(formFields.mapValues { StringValue(it.value) })

        return requestMap
    }

    fun setHeaders(addedHeaders: Map<String, String>): HttpRequest = copy(headers = headers.plus(addedHeaders))

    fun toLogString(prefix: String = ""): String {
        val methodString = method ?: "NO_METHOD"

        val pathString = path ?: "NO_PATH"
        val queryParamString = queryParams.map { "${it.key}=${it.value}"}.joinToString("&").let { if(it.isNotEmpty()) "?$it" else it }
        val urlString = "$pathString$queryParamString"

        val firstLine = "$methodString $urlString"
        val headerString = headers.map { "${it.key}: ${it.value}" }.joinToString("\n")
        val bodyString = when {
            formFields.isNotEmpty() -> formFields.map { "${it.key}=${it.value}"}.joinToString("&")
            multiPartFormData.isNotEmpty() -> {
                multiPartFormData.mapIndexed { index, part -> part.toDisplayableValue() }.joinToString("\n")
            }
            else -> body.toString()
        }

        val firstPart = listOf(firstLine, headerString).joinToString("\n").trim()
        val requestString = listOf(firstPart, "", bodyString).joinToString("\n")
        return startLinesWith(requestString, prefix)
    }

    fun toPattern(): HttpRequestPattern {
        val pathForPattern = path ?: "/"

        return HttpRequestPattern(
            headersPattern = HttpHeadersPattern(mapToPattern(headers)),
            urlMatcher = URLMatcher(mapToPattern(queryParams), pathToPattern(pathForPattern), pathForPattern),
            method = this.method,
            body = this.body?.toExactType() ?: NoContentPattern,
            formFieldsPattern = mapToPattern(formFields),
            multiPartFormDataPattern = multiPartFormData.map { it.toPattern() }
        )
    }

    private fun mapToPattern(map: Map<String, String>): Map<String, Pattern> {
        return map.mapValues { (_, value) ->
            if (isPatternToken(value))
                parsedPattern(value)
            else
                ExactValuePattern(StringValue(value))
        }
    }
}

fun s(json: Map<String, Value>, key: String): String = (json.getValue(key) as StringValue).string

fun requestFromJSON(json: Map<String, Value>) =
    HttpRequest()
        .updateMethod(s(json, "method"))
        .updatePath(if ("path" in json) s(json, "path") else "/")
        .updateQueryParams(if ("query" in json)
            (json["query"] as JSONObjectValue).jsonObject.mapValues { it.value.toString() }
            else emptyMap())
        .setHeaders(if ("headers" in json) (json["headers"] as JSONObjectValue).jsonObject.mapValues { it.value.toString() } else emptyMap())
        .let { httpRequest ->
            when {
                "form-fields" in json -> {
                    val formFields = json.getValue("form-fields")
                    if(formFields !is JSONObjectValue)
                        throw ContractException("form-fields must be a json object.")

                    httpRequest.copy(formFields = formFields.jsonObject.mapValues { it.value.toStringValue() })
                }
                "multipart-formdata" in json -> {
                    val parts = json.getValue("multipart-formdata")
                    if(parts !is JSONArrayValue)
                        throw ContractException("multipart-formdata must be a json array.")

                    val multiPartData: List<MultiPartFormDataValue> = parts.list.map {
                        if(it !is JSONObjectValue)
                            throw ContractException("All multipart parts must be json object values.")

                        val multiPartSpec = it.jsonObject
                        val name = multiPartSpec.getValue("name").toStringValue()

                        when {
                            multiPartSpec.containsKey("content") -> MultiPartContentValue(name, multiPartSpec.getValue("content"))
                            else -> MultiPartFileValue(name, multiPartSpec.getValue("filename").toStringValue(), multiPartSpec.getValue("contentType").toStringValue(), multiPartSpec["contentEncoding"]?.toStringValue())
                        }
                    }

                    httpRequest.copy(multiPartFormData = httpRequest.multiPartFormData.plus(multiPartData))
                }
                "body" in json -> httpRequest.updateBody(json.getValue("body"))
                else -> httpRequest
            }
        }

internal fun startLinesWith(str: String, startValue: String) =
        str.split("\n").map { "$startValue$it" }.joinToString("\n")

fun toGherkinClauses(request: HttpRequest): Pair<List<GherkinClause>, ExampleDeclaration> {
    val gherkinClauses = emptyList<GherkinClause>().let {
        val method = request.method ?: throw ContractException("Can't generate a qontract without the http method.")

        if (request.path == null)
            throw ContractException("Can't generate a qontract without the url.")

        val query = when {
            request.queryParams.isNotEmpty() -> {
                val query = request.queryParams.entries.joinToString("&") { (key, value) -> "$key=${guessType(parsedValue(value)).type().pattern}" }
                "?$query"
            }
            else -> ""
        }

        val path = "${request.path}$query"

        it.plus(GherkinClause("$method $path", When))
    }.plus(headersToGherkin(request.headers, "request-header", When))

    val (bodyClauses, bodyExamples) = when {
        request.multiPartFormData.isNotEmpty() -> Pair(multiPartFormDataToGherkin(request.multiPartFormData), ExampleDeclaration())
        request.formFields.isNotEmpty() -> formFieldsToGherkin(request.formFields)
        else -> bodyToGherkinClauses("RequestBody", "request-body", request.body, When)?: Pair(emptyList<GherkinClause>(), ExampleDeclaration())
    }

    return Pair(gherkinClauses.plus(bodyClauses), bodyExamples)
}

fun multiPartFormDataToGherkin(multiPartFormData: List<MultiPartFormDataValue>): List<GherkinClause> {
    return multiPartFormData.flatMap { part ->
        when(part) {
            is MultiPartContentValue -> {
                val typeName = "FormData${part.name}"
                val (typeDeclaration, _) = part.content.typeDeclaration(typeName)

                toGherkinClauses(typeDeclaration.types).plus(GherkinClause("request-part ${part.name} ${typeDeclaration.typeValue}", When))
            }
            is MultiPartFileValue -> {
                val contentType = part.contentType
                val contentEncoding = contentType?.let { part.contentEncoding }

                listOf(GherkinClause("request-part ${part.name} ${part.filename} $contentType $contentEncoding".trim(), When))
            }
        }
    }
}

fun formFieldsToGherkin(formFields: Map<String, String>): Pair<List<GherkinClause>, ExampleDeclaration> {
    val declarations = formFields.mapValues { guessType(parsedValue(it.value)) }.entries.map {
        val typeName = "FormField${it.key.capitalize()}"
        val (typeDeclaration, exampleDeclaration) = it.value.typeDeclaration(typeName)

        Pair(toGherkinClauses(typeDeclaration.types).plus(GherkinClause("form-field ${it.key} ${typeDeclaration.typeValue}", When)), exampleDeclaration)
    }

    val clauses = declarations.flatMap { it.first }
    val examples = declarations.map { it.second }.fold(ExampleDeclaration()) { acc, exampleDeclaration ->
        ExampleDeclaration(acc.examples.plus(exampleDeclaration.examples))
    }

    return Pair(clauses, examples)
}
