package run.qontract.core

import io.netty.buffer.ByteBuf
import run.qontract.conversions.guessType
import run.qontract.core.GherkinSection.When
import run.qontract.core.pattern.*
import run.qontract.core.utilities.URIUtils.parseQuery
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
    return Pair(emptyList<GherkinClause>(), ExampleDeclaration()).let { (clauses, exampleDeclaration) ->
        val (newClauses, newExamples) = firstLineToGherkin(request, exampleDeclaration)
        Pair(clauses.plus(newClauses), exampleDeclaration.plus(newExamples))
    }.let { (clauses, examples) ->
        val (newClauses, newExamples) = headersToGherkin(request.headers, "request-header", examples, When)
        Pair(clauses.plus(newClauses), examples.plus(newExamples))
    }.let { (clauses, examples) ->
        val (newClauses, newExamples) = bodyToGherkin(request, examples)
        Pair(clauses.plus(newClauses), examples.plus(newExamples))
    }
}

fun typeDeclarationsToGherkin(typeDeclarations: Map<String, TypeDeclaration>) =
        typeDeclarations.entries.flatMap { typeDeclaration -> typeDeclaration.value.types.map { toClause(it.key, it.value) } }

fun stringMapToValueMap(stringStringMap: Map<String, String>) =
        stringStringMap.mapValues { guessType(parsedValue(it.value)) }

fun bodyToGherkin(request: HttpRequest, examples: ExampleDeclaration): Pair<List<GherkinClause>, ExampleDeclaration> {
    return when {
        request.multiPartFormData.isNotEmpty() -> multiPartFormDataToGherkin(request.multiPartFormData, examples)
        request.formFields.isNotEmpty() -> formFieldsToGherkin(request.formFields, examples)
        else -> requestBodyToGherkinClauses(request.body, examples)?: Pair(emptyList(), ExampleDeclaration())
    }
}

fun firstLineToGherkin(request: HttpRequest, exampleDeclaration: ExampleDeclaration): Pair<List<GherkinClause>, ExampleDeclaration> {
    val method = request.method ?: throw ContractException("Can't generate a qontract without the http method.")

    if (request.path == null)
        throw ContractException("Can't generate a qontract without the url.")

    val (query, typeDeclarations, newExamples) = when {
        request.queryParams.isNotEmpty() -> {
            val (typeDeclarations, examples) = dictionaryToDeclarations(stringMapToValueMap(request.queryParams), exampleDeclaration)

            val query = typeDeclarations.entries.joinToString("&") { (key, typeDeclaration) -> "$key=${typeDeclaration.typeValue}" }
            Triple("?$query", typeDeclarations, examples)
        }
        else -> Triple("", emptyMap(), exampleDeclaration)
    }

    val path = "${request.path}$query"

    val requestLineGherkin = GherkinClause("$method $path", When)
    val newClauses = typeDeclarationsToGherkin(typeDeclarations).plus(requestLineGherkin)

    return Pair(newClauses, newExamples)
}

fun multiPartFormDataToGherkin(multiPartFormData: List<MultiPartFormDataValue>, exampleDeclaration: ExampleDeclaration): Pair<List<GherkinClause>, ExampleDeclaration> {
    return multiPartFormData.fold(Pair(emptyList(), exampleDeclaration)) { acc, part ->
        val (clauses, examples) = acc

        when(part) {
            is MultiPartContentValue -> {
                val typeName = part.name
                val (typeDeclaration, newExamples) = part.content.typeDeclarationWithKey(typeName, examples)

                Pair(clauses.plus(toGherkinClauses(typeDeclaration.types).plus(GherkinClause("request-part ${part.name} ${typeDeclaration.typeValue}", When))),
                        examples.plus(newExamples))
            }
            is MultiPartFileValue -> {
                val contentType = part.contentType
                val contentEncoding = contentType?.let { part.contentEncoding }

                Pair(listOf(GherkinClause("request-part ${part.name} ${part.filename} $contentType $contentEncoding".trim(), When)), examples)
            }
        }
    }
}

fun formFieldsToGherkin(formFields: Map<String, String>, exampleDeclaration: ExampleDeclaration): Pair<List<GherkinClause>, ExampleDeclaration> {
    val (typeDeclarations, newExamples) = dictionaryToDeclarations(stringMapToValueMap(formFields), exampleDeclaration)

    val clauses = typeDeclarationsToGherkin(typeDeclarations)
    val formFieldClauses = typeDeclarations.entries.map { entry -> GherkinClause("form-field ${entry.key} ${entry.value.typeValue}", When) }

    return Pair(clauses.plus(formFieldClauses), exampleDeclaration.plus(newExamples))
}
