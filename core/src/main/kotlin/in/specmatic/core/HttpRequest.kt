package `in`.specmatic.core

import io.netty.buffer.ByteBuf
import `in`.specmatic.conversions.guessType
import `in`.specmatic.core.GherkinSection.When
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.URIUtils.parseQuery
import `in`.specmatic.core.value.*
import `in`.specmatic.core.value.UseExampleDeclarations
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

const val FORM_FIELDS_JSON_KEY = "form-fields"
const val MULTIPART_FORMDATA_JSON_KEY = "multipart-formdata"

data class HttpRequest(val method: String? = null, val path: String? = null, val headers: Map<String, String> = emptyMap(), val body: Value = EmptyString, val queryParams: Map<String, String> = emptyMap(), val formFields: Map<String, String> = emptyMap(), val multiPartFormData: List<MultiPartFormDataValue> = emptyList()) {
    fun updateQueryParams(queryParams: Map<String, String>): HttpRequest = copy(queryParams = queryParams.plus(queryParams))

    fun withHost(host: String) = this.copy(headers = this.headers.plus("Host" to host))

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

    fun updateMethod(name: String): HttpRequest = copy(method = name.uppercase())

    private fun updateBody(contentBuffer: ByteBuf) {
        val bodyString = contentBuffer.toString(Charset.defaultCharset())
        updateBody(bodyString)
    }

    fun updateHeader(key: String, value: String): HttpRequest = copy(headers = headers.plus(key to value))

    val bodyString: String
        get() = body.toString()

    fun getURL(baseURL: String?): String {
        val cleanBase = baseURL?.let {
            if(it.isNotBlank() && !it.endsWith("/"))
                "$it/"
            else
                it
        } ?: ""
        val cleanPath = path?.let {
            if(it.isNotBlank() && it.startsWith("/") && cleanBase.isNotBlank())
                it.removePrefix("/")
            else
                it
        } ?: ""

        return "$cleanBase$cleanPath" + if (queryParams.isNotEmpty()) {
            val joinedQueryParams =
                queryParams.toList()
                    .joinToString("&") {
                        "${it.first}=${URLEncoder.encode(it.second, StandardCharsets.UTF_8.toString())}"
                    }
            "?$joinedQueryParams"
        } else ""
    }

    fun toJSON(): JSONObjectValue {
        val requestMap = mutableMapOf<String, Value>()

        requestMap["path"] = path?.let { StringValue(it) } ?: StringValue("/")
        method?.let { requestMap["method"] = StringValue(it) } ?: throw ContractException("Can't serialise the request without a method.")

        setIfNotEmpty(requestMap, "query", queryParams)
        setIfNotEmpty(requestMap, "headers", headers)

        when {
            formFields.isNotEmpty() -> requestMap[FORM_FIELDS_JSON_KEY] = JSONObjectValue(formFields.mapValues { StringValue(it.value) })
            multiPartFormData.isNotEmpty() -> requestMap[MULTIPART_FORMDATA_JSON_KEY] = JSONArrayValue(multiPartFormData.map { it.toJSONObject() })
            else -> requestMap["body"] = body
        }

        return JSONObjectValue(requestMap)
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
                multiPartFormData.joinToString("\n") { part -> part.toDisplayableValue() }
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
            body = this.body.exactMatchElseType(),
            formFieldsPattern = mapToPattern(formFields),
            multiPartFormDataPattern = multiPartFormData.map { it.inferType() }
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

private fun setIfNotEmpty(dest: MutableMap<String, Value>, key: String, data: Map<String, String>) {
    if(data.isNotEmpty())
        dest[key] = JSONObjectValue(data.mapValues { StringValue(it.value) })
}

fun nativeString(json: Map<String, Value>, key: String): String? {
    val keyValue = json[key] ?: return null

    if(keyValue !is StringValue)
        throw ContractException("Expected $key to be a string value")

    return keyValue.string
}

fun requestFromJSON(json: Map<String, Value>) =
    HttpRequest()
        .updateMethod(nativeString(json, "method") ?: throw ContractException("http-request must contain a key named method whose value is the method in the request"))
        .updatePath(nativeString(json, "path") ?: "/")
        .updateQueryParams(nativeStringStringMap(json, "query"))
        .setHeaders(nativeStringStringMap(json, "headers"))
        .let { httpRequest ->
            when {
                FORM_FIELDS_JSON_KEY in json -> httpRequest.copy(formFields = nativeStringStringMap(json, FORM_FIELDS_JSON_KEY))
                MULTIPART_FORMDATA_JSON_KEY in json -> {
                    val parts = arrayValue(json.getValue(MULTIPART_FORMDATA_JSON_KEY), "$MULTIPART_FORMDATA_JSON_KEY must be a json array.")

                    val multiPartData: List<MultiPartFormDataValue> = parts.list.map {
                        val part = objectValue(it, "All multipart parts must be json object values.")

                        val multiPartSpec = part.jsonObject
                        val name = nativeString(multiPartSpec, "name") ?: throw ContractException("One of the multipart entries does not have a name key")

                        parsePartType(multiPartSpec, name)
                    }

                    httpRequest.copy(multiPartFormData = httpRequest.multiPartFormData.plus(multiPartData))
                }
                "body" in json -> {
                    val body = notNull(json.getOrDefault("body", NullValue), "Either body should have a value or the key should be absent from http-response")
                    httpRequest.updateBody(body)
                }
                else -> httpRequest
            }
        }

private fun parsePartType(multiPartSpec: Map<String, Value>, name: String): MultiPartFormDataValue {
    return when {
        multiPartSpec.containsKey("content") -> MultiPartContentValue(name, multiPartSpec.getValue("content"))
        multiPartSpec.containsKey("filename") -> MultiPartFileValue(name, multiPartSpec.getValue("filename").toStringLiteral().removePrefix("@"), multiPartSpec["contentType"]?.toStringLiteral(), multiPartSpec["contentEncoding"]?.toStringLiteral())
        else -> throw ContractException("Multipart entry $name must have either a content key or a filename key")
    }
}

fun objectValue(value: Value, errorMessage: String): JSONObjectValue {
    if(value !is JSONObjectValue)
        throw ContractException(errorMessage)

    return value
}

fun arrayValue(value: Value, errorMessage: String): JSONArrayValue {
    if(value !is JSONArrayValue)
        throw ContractException(errorMessage)

    return value
}

fun notNull(value: Value, errorMessage: String): Value {
    if(value is NullValue)
        throw ContractException(errorMessage)

    return value
}

internal fun nativeStringStringMap(json: Map<String, Value>, key: String): Map<String, String> {
    val queryValue = json[key] ?: return emptyMap()

    if(queryValue !is JSONObjectValue)
        throw ContractException("Expected $key to be a json object")

    return queryValue.jsonObject.mapValues { it.value.toString() }
}

internal fun startLinesWith(str: String, startValue: String) =
        str.split("\n").joinToString("\n") { "$startValue$it" }

fun toGherkinClauses(request: HttpRequest): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    return Triple(emptyList<GherkinClause>(), emptyMap<String, Pattern>(), UseExampleDeclarations()).let { (clauses, types, exampleDeclaration) ->
        val (newClauses, newTypes, newExamples) = firstLineToGherkin(request, types, exampleDeclaration)
        Triple(clauses.plus(newClauses), newTypes, newExamples)
    }.let { (clauses, types, examples) ->
        val (newClauses, newTypes, newExamples) = headersToGherkin(request.headers, "request-header", types, examples, When)
        Triple(clauses.plus(newClauses), newTypes, newExamples)
    }.let { (clauses, types, examples) ->
        val (newClauses, newTypes, newExamples) = bodyToGherkin(request, types, examples)
        Triple(clauses.plus(newClauses), newTypes, newExamples)
    }.let { (clauses, types, examples) ->
        Triple(clauses, types, examples)
    }
}

fun stringMapToValueMap(stringStringMap: Map<String, String>) =
        stringStringMap.mapValues { guessType(parsedValue(it.value)) }

fun bodyToGherkin(request: HttpRequest, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    return when {
        request.multiPartFormData.isNotEmpty() -> multiPartFormDataToGherkin(request.multiPartFormData, types, exampleDeclarations)
        request.formFields.isNotEmpty() -> formFieldsToGherkin(request.formFields, types, exampleDeclarations)
        else -> requestBodyToGherkinClauses(request.body, types, exampleDeclarations)
    }
}

fun multiPartFormDataToGherkin(multiPartFormData: List<MultiPartFormDataValue>, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    return multiPartFormData.fold(Triple(emptyList(), types, exampleDeclarations)) { (clauses, newTypes, examples), part ->
        part.toClauseData(clauses, newTypes, examples)
    }
}

fun firstLineToGherkin(request: HttpRequest, types: Map<String, Pattern>, exampleDeclarationsStore: ExampleDeclarations): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    val method = request.method ?: throw ContractException("Can't generate a spec file without the http method.")

    if (request.path == null)
        throw ContractException("Can't generate a contract without the url.")

    val (query, newTypes, newExamples) = when {
        request.queryParams.isNotEmpty() -> {
            val (dictionaryType, newTypes, examples) = dictionaryToDeclarations(stringMapToValueMap(request.queryParams), types, exampleDeclarationsStore)

            val query = dictionaryType.entries.joinToString("&") { (key, typeDeclaration) -> "$key=${typeDeclaration.pattern}" }
            Triple("?$query", newTypes, examples)
        }
        else -> Triple("", emptyMap(), exampleDeclarationsStore)
    }

    val path = "${request.path}$query"

    val requestLineGherkin = GherkinClause("$method $path", When)

    return Triple(listOf(requestLineGherkin), newTypes, newExamples)
}

fun formFieldsToGherkin(formFields: Map<String, String>, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    val (dictionaryTypeMap, newTypes, newExamples) = dictionaryToDeclarations(stringMapToValueMap(formFields), types, exampleDeclarations)

    val formFieldClauses = dictionaryTypeMap.entries.map { entry -> GherkinClause("form-field ${entry.key} ${entry.value.pattern}", When) }

    return Triple(formFieldClauses, newTypes, exampleDeclarations.plus(newExamples))
}
