package io.specmatic.core

import io.specmatic.conversions.guessType
import io.specmatic.core.GherkinSection.When
import io.specmatic.core.pattern.*
import io.specmatic.core.utilities.URIUtils.parseQuery
import io.specmatic.core.value.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.specmatic.core.utilities.Flags.Companion.getBooleanValue
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.message.BasicNameValuePair
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.*
import java.nio.charset.StandardCharsets

const val FORM_FIELDS_JSON_KEY = "form-fields"
const val MULTIPART_FORMDATA_JSON_KEY = "multipart-formdata"

fun urlToQueryParams(uri: URI): Map<String, String> {
    if (uri.query == null)
        return emptyMap()

    return uri.query.split("&").associate {
        val parts = it.split("=".toRegex(), 2)
        Pair(parts[0], parts[1])
    }
}

data class HttpRequest(
    val method: String? = null,
    val path: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val body: Value = EmptyString,
    val queryParams: QueryParameters = QueryParameters(),
    val formFields: Map<String, String> = emptyMap(),
    val multiPartFormData: List<MultiPartFormDataValue> = emptyList()
) {
    constructor(method: String, uri: URI) : this(method, uri.path, queryParametersMap = urlToQueryParams(uri))
    constructor(
        method: String? = null,
        path: String? = null,
        headers: Map<String, String> = emptyMap(),
        body: Value = EmptyString,
        queryParametersMap: Map<String, String> = emptyMap(),
        formFields: Map<String, String> = emptyMap(),
        multiPartFormData: List<MultiPartFormDataValue> = emptyList(),
        marker: String = "Dummy"
    ) : this(
        method = method,
        path = path,
        headers = headers,
        body = body,
        queryParams = QueryParameters(queryParametersMap),
        formFields = formFields,
        multiPartFormData = multiPartFormData,
    )

    fun updateQueryParams(otherQueryParams: Map<String, String>): HttpRequest =
        copy(queryParams = queryParams.plus(otherQueryParams))

    fun withHost(host: String) = this.copy(headers = this.headers.plus("Host" to host))

    fun updatePath(path: String): HttpRequest {
        return try {
            val urlParam = URI(path)
            updateWith(urlParam)
        } catch (e: URISyntaxException) {
            val pieces = path.split("?", limit = 2)
            updateWithPathAndQuery(pieces.get(0), pieces.getOrNull(1))
//            copy(path = path)
        } catch (e: UnsupportedEncodingException) {
            val pieces = path.split("?", limit = 2)
            updateWithPathAndQuery(pieces.get(0), pieces.getOrNull(1))
//            copy(path = path)
        }
    }

    fun updateQueryParam(key: String, value: String): HttpRequest = copy(queryParams = queryParams.plus(key to value))

    fun updateBody(body: Value): HttpRequest = copy(body = body)

    fun updateBody(body: String?): HttpRequest = copy(body = parsedValue(body))

    fun updateWith(url: URI): HttpRequest {
//        val path = url.path
//        val queryParams = parseQuery(url.query)
//        return copy(path = path, queryParams = QueryParameters(queryParams))

        return updateWithPathAndQuery(url.path, url.query)
    }

    fun updateWithPathAndQuery(path: String, query: String?): HttpRequest {
        val queryParams = parseQuery(query)
        return copy(path = path, queryParams = QueryParameters(queryParams))
    }

    fun updateMethod(name: String): HttpRequest = copy(method = name.uppercase())

    fun updateHeader(key: String, value: String): HttpRequest = copy(headers = headers.plus(key to value))

    val bodyString: String
        get() = body.toString()

    fun getURL(baseURL: String?): String {
        val cleanBase = baseURL?.removeSuffix("/")
        val cleanPath = path?.removePrefix("/")
        val fullUrl = URLParts(concatNonNulls(cleanBase, cleanPath, "/")).withEncodedPathSegments()
        val queryPart = URLEncodedUtils.format(queryParams.paramPairs.map { BasicNameValuePair(it.first, it.second) }, Charsets.UTF_8)
        return concatNonNulls(fullUrl, queryPart, "?")
    }

    private fun concatNonNulls(first: String?, second: String?, separator: String) =
        listOf(first, second).filterNot { it.isNullOrBlank() }.joinToString(separator)

    fun toJSON(): JSONObjectValue {
        val requestMap = mutableMapOf<String, Value>()

        requestMap["path"] = path?.let { StringValue(it) } ?: StringValue("/")
        method?.let { requestMap["method"] = StringValue(it) }
            ?: throw ContractException("Can't serialise the request without a method.")

        setIfNotEmpty(requestMap, "query", queryParams.asMap())
        setIfNotEmpty(requestMap, "headers", headers)

        when {
            formFields.isNotEmpty() -> requestMap[FORM_FIELDS_JSON_KEY] =
                JSONObjectValue(formFields.mapValues { StringValue(it.value) })

            multiPartFormData.isNotEmpty() -> requestMap[MULTIPART_FORMDATA_JSON_KEY] =
                JSONArrayValue(multiPartFormData.map { it.toJSONObject() })

            else -> requestMap["body"] = body
        }

        return JSONObjectValue(requestMap)
    }

    fun setHeaders(addedHeaders: Map<String, String>): HttpRequest = copy(headers = headers.plus(addedHeaders))

    fun toLogString(prefix: String = ""): String {
        val methodString = method ?: "NO_METHOD"

        val pathString = path ?: "NO_PATH"
        val queryParamString =
            queryParams.paramPairs.map { "${it.first}=${it.second}" }.joinToString("&").let { if (it.isNotEmpty()) "?$it" else it }
        val urlString = "$pathString$queryParamString"

        val firstLine = "$methodString $urlString"
        val headerString = headers.map { "${it.key}: ${it.value}" }.joinToString("\n")
        val bodyString = when {
            formFields.isNotEmpty() -> formFields.map { "${it.key}=${it.value}" }.joinToString("&")
            multiPartFormData.isNotEmpty() -> {
                multiPartFormData.joinToString("\n") { part -> part.toDisplayableValue() }
            }

            else -> body.toString()
        }.let { formatJson(it) }

        val firstPart = listOf(firstLine, headerString).joinToString("\n").trim()
        val requestString = listOf(firstPart, "", bodyString).joinToString("\n")
        return startLinesWith(requestString, prefix)
    }

    fun toPattern(): HttpRequestPattern {
        val pathForPattern = path ?: "/"

        return HttpRequestPattern(
            headersPattern = HttpHeadersPattern(mapToPattern(headers)),
            httpPathPattern = HttpPathPattern(pathToPattern(pathForPattern), pathForPattern),
            httpQueryParamPattern = HttpQueryParamPattern(mapToQueryParameterPattern(queryParams)),
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

    private fun mapToQueryParameterPattern(queryParams: QueryParameters): Map<String, Pattern> {
        val queryParamGroups = queryParams.paramPairs.groupBy { it.first }
            .mapValues { (_, keyValuePairs) ->
                keyValuePairs.map { (_,value) ->
                    if (isPatternToken(value))
                        parsedPattern(value)
                    else
                        ExactValuePattern(StringValue(value))
                }
            }
        return queryParamGroups.map { (parameterKey, parameterPatterns) ->
            if(parameterPatterns.size > 1) {
                parameterKey to QueryParameterArrayPattern(parameterPatterns, parameterKey)
            }
            else {
                parameterKey to QueryParameterScalarPattern(parameterPatterns.single())
            }
        }.toMap()
    }

    fun buildKTORRequest(httpRequestBuilder: HttpRequestBuilder, url: URL?) {
        httpRequestBuilder.method = HttpMethod.parse(method as String)

        val listOfExcludedHeaders: List<String> = listOfExcludedHeaders()

        withoutDuplicateHostHeader(headers, url)
            .map { Triple(it.key.trim(), it.key.trim().lowercase(), it.value.trim()) }
            .filter { (_, loweredKey, _) -> loweredKey !in listOfExcludedHeaders }
            .forEach { (key, _, value) ->
                httpRequestBuilder.header(key, value)
            }

        httpRequestBuilder.url.let {
            if (it.port == DEFAULT_PORT || it.port == it.protocol.defaultPort)
                httpRequestBuilder.header("Host", it.authority)
        }

        if(body !is NoBodyValue) {
            httpRequestBuilder.setBody(
                when {
                    formFields.isNotEmpty() -> {
                        val parameters = formFields.mapValues { listOf(it.value) }.toList()
                        FormDataContent(parametersOf(*parameters.toTypedArray()))
                    }

                    multiPartFormData.isNotEmpty() -> {
                        MultiPartFormDataContent(formData {
                            multiPartFormData.forEach { value ->
                                value.addTo(this)
                            }
                        })
                    }

                    else -> {
                        when {
                            headers.containsKey(CONTENT_TYPE) -> TextContent(
                                bodyString,
                                ContentType.parse(headers[CONTENT_TYPE] as String)
                            )

                            else -> TextContent(bodyString, ContentType.parse(body.httpContentType))
                        }
                    }
                }
            )
        }
    }

    private fun withoutDuplicateHostHeader(headers: Map<String, String>, url: URL?): Map<String, String> {
        if (url === null)
            return headers

        if (isNotIPAddress(url.host))
            return headers - "Host"

        return headers
    }

    private fun isNotIPAddress(host: String): Boolean {
        return !isIPAddress(host) && host != "localhost"
    }

    private fun isIPAddress(host: String): Boolean {
        return try {
            host.split(".").map { it.toInt() }.isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    fun loadFileContentIntoParts(): HttpRequest {
        val parts = multiPartFormData

        val newMultiPartFormData: List<MultiPartFormDataValue> = parts.map { part ->
            when (part) {
                is MultiPartContentValue -> part
                is MultiPartFileValue -> {
                    val partFile = File(part.filename.removePrefix("@"))
                    val binaryContent = if (partFile.exists()) {
                        MultiPartContent(partFile)
                    } else {
                        MultiPartContent(StringPattern().generate(Resolver()).toStringLiteral())
                    }
                    part.copy(content = binaryContent)
                }
            }
        }

        return copy(multiPartFormData = newMultiPartFormData)
    }

    interface RequestNotRecognizedMessages {
        fun soap(soapActionHeaderValue: String, path: String): String

        fun xmlOverHttp(method: String, path: String): String

        fun restful(method: String, path: String): String
    }

    class LenientRequestNotRecognizedMessages : RequestNotRecognizedMessages {
        override fun soap(soapActionHeaderValue: String, path: String): String {
            return "No matching SOAP stub or contract found for SOAPAction $soapActionHeaderValue and path $path"
        }

        override fun xmlOverHttp(method: String, path: String): String {
            return "No matching XML-REST stub or contract found for method $method and path $path (assuming you're looking for a REST API since no SOAPAction header was detected)"
        }

        override fun restful(method: String, path: String): String {
            return "No matching REST stub or contract found for method $method and path $path (assuming you're looking for a REST API since no SOAPAction header was detected)"
        }
    }

    class StrictRequestNotRecognizedMessages : RequestNotRecognizedMessages {
        override fun soap(soapActionHeaderValue: String, path: String): String {
            return "No matching SOAP stub (strict mode) found for SOAPAction $soapActionHeaderValue and path $path"
        }

        override fun xmlOverHttp(method: String, path: String): String {
            return "No matching XML-REST stub (strict mode) found for method $method and path $path (assuming you're looking for a REST API since no SOAPAction header was detected)"
        }

        override fun restful(method: String, path: String): String {
            return "No matching REST stub (strict mode) found for method $method and path $path (assuming you're looking for a REST API since no SOAPAction header was detected)"
        }
    }

    fun requestNotRecognized(requestNotRecognizedMessages: RequestNotRecognizedMessages): String {
        val soapActionHeader = "SOAPAction"

        val method = this.method!!
        val path = this.path ?: "/"

        return when {
            this.headers.containsKey(soapActionHeader) ->
                requestNotRecognizedMessages.soap(this.headers.getValue(soapActionHeader), path)

            this.body is XMLNode ->
                requestNotRecognizedMessages.xmlOverHttp(method, path)

            else ->
                requestNotRecognizedMessages.restful(method, path)
        }
    }

    fun requestNotRecognized(): String {
        return requestNotRecognized(LenientRequestNotRecognizedMessages())
    }

    fun requestNotRecognizedInStrictMode(): String {
        return requestNotRecognized(StrictRequestNotRecognizedMessages())
    }

    fun withoutDynamicHeaders(): HttpRequest = copy(headers = headers.withoutDynamicHeaders())
}

private fun setIfNotEmpty(dest: MutableMap<String, Value>, key: String, data: Map<String, String>) {
    if (data.isNotEmpty())
        dest[key] = JSONObjectValue(data.mapValues { StringValue(it.value) })
}

fun nativeString(json: Map<String, Value>, key: String): String? {
    val keyValue = json[key] ?: return null

    if (keyValue !is StringValue)
        throw ContractException("Expected $key to be a string value")

    return keyValue.string
}

fun requestFromJSON(json: Map<String, Value>) =
    HttpRequest()
        .updateMethod(
            nativeString(json, "method")
                ?: throw ContractException("http-request must contain a key named method whose value is the method in the request")
        )
        .updatePath(nativeString(json, "path") ?: "/")
        .updateQueryParams(nativeStringStringMap(json, "query"))
        .setHeaders(nativeStringStringMap(json, "headers"))
        .let { httpRequest ->
            when {
                FORM_FIELDS_JSON_KEY in json -> httpRequest.copy(
                    formFields = nativeStringStringMap(
                        json,
                        FORM_FIELDS_JSON_KEY
                    )
                )

                MULTIPART_FORMDATA_JSON_KEY in json -> {
                    val parts = arrayValue(
                        json.getValue(MULTIPART_FORMDATA_JSON_KEY),
                        "$MULTIPART_FORMDATA_JSON_KEY must be a json array."
                    )

                    val multiPartData: List<MultiPartFormDataValue> = parts.list.map {
                        val part = objectValue(it, "All multipart parts must be json object values.")

                        val multiPartSpec = part.jsonObject
                        val name = nativeString(multiPartSpec, "name")
                            ?: throw ContractException("One of the multipart entries does not have a name key")

                        parsePartType(multiPartSpec, name)
                    }

                    httpRequest.copy(multiPartFormData = httpRequest.multiPartFormData.plus(multiPartData))
                }

                "body" in json -> {
                    val body = notNull(
                        json.getOrDefault("body", NullValue),
                        "Either body should have a value or the key should be absent from http-response"
                    )
                    httpRequest.updateBody(body)
                }

                else -> httpRequest
            }
        }

private fun parsePartType(multiPartSpec: Map<String, Value>, name: String): MultiPartFormDataValue {
    return when {
        multiPartSpec.containsKey("content") -> MultiPartContentValue(
            name,
            multiPartSpec.getValue("content"),
            specifiedContentType = multiPartSpec["contentType"]?.toStringLiteral()
        )

        multiPartSpec.containsKey("filename") -> MultiPartFileValue(
            name,
            multiPartSpec.getValue("filename").toStringLiteral().removePrefix("@"),
            multiPartSpec["contentType"]?.toStringLiteral(),
            multiPartSpec["contentEncoding"]?.toStringLiteral()
        )

        else -> throw ContractException("Multipart entry $name must have either a content key or a filename key")
    }
}

fun objectValue(value: Value, errorMessage: String): JSONObjectValue {
    if (value !is JSONObjectValue)
        throw ContractException(errorMessage)

    return value
}

fun arrayValue(value: Value, errorMessage: String): JSONArrayValue {
    if (value !is JSONArrayValue)
        throw ContractException(errorMessage)

    return value
}

fun notNull(value: Value, errorMessage: String): Value {
    if (value is NullValue)
        throw ContractException(errorMessage)

    return value
}

internal fun nativeStringStringMap(json: Map<String, Value>, key: String): Map<String, String> {
    val queryValue = json[key] ?: return emptyMap()

    if (queryValue !is JSONObjectValue)
        throw ContractException("Expected $key to be a json object")

    return queryValue.jsonObject.mapValues { it.value.toString() }
}

internal fun startLinesWith(str: String, startValue: String) =
    str.split("\n").joinToString("\n") { "$startValue$it" }

fun toGherkinClauses(request: HttpRequest): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    return Triple(
        emptyList<GherkinClause>(),
        emptyMap<String, Pattern>(),
        UseExampleDeclarations()
    ).let { (clauses, types, exampleDeclaration) ->
        val (newClauses, newTypes, newExamples) = firstLineToGherkin(request, types, exampleDeclaration)
        Triple(clauses.plus(newClauses), newTypes, newExamples)
    }.let { (clauses, types, examples) ->
        val (newClauses, newTypes, newExamples) = headersToGherkin(
            request.headers,
            "request-header",
            types,
            examples,
            When
        )
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

fun queryParamsToValueMap(queryParams: QueryParameters) =
    queryParams.paramPairs.map { (key, value) -> key to guessType(parsedValue(value)) }.toMap()

fun bodyToGherkin(
    request: HttpRequest,
    types: Map<String, Pattern>,
    exampleDeclarations: ExampleDeclarations
): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    return when {
        request.multiPartFormData.isNotEmpty() -> multiPartFormDataToGherkin(
            request.multiPartFormData,
            types,
            exampleDeclarations
        )

        request.formFields.isNotEmpty() -> formFieldsToGherkin(request.formFields, types, exampleDeclarations)
        else -> requestBodyToGherkinClauses(request.body, types, exampleDeclarations)
    }
}

fun multiPartFormDataToGherkin(
    multiPartFormData: List<MultiPartFormDataValue>,
    types: Map<String, Pattern>,
    exampleDeclarations: ExampleDeclarations
): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    return multiPartFormData.fold(
        Triple(
            emptyList(),
            types,
            exampleDeclarations
        )
    ) { (clauses, newTypes, examples), part ->
        part.toClauseData(clauses, newTypes, examples)
    }
}

fun firstLineToGherkin(
    request: HttpRequest,
    types: Map<String, Pattern>,
    exampleDeclarationsStore: ExampleDeclarations
): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    val method = request.method ?: throw ContractException("Can't generate a spec file without the http method.")

    if (request.path == null)
        throw ContractException("Can't generate a contract without the url.")

    val (query, newTypes, newExamples) = when {
        request.queryParams.isNotEmpty() -> {
            val (dictionaryType, newTypes, examples) = dictionaryToDeclarations(
                queryParamsToValueMap(request.queryParams),
                types,
                exampleDeclarationsStore
            )

            val query =
                dictionaryType.entries.joinToString("&") { (key, typeDeclaration) -> "$key=${typeDeclaration.pattern}" }
            Triple("?$query", newTypes, examples)
        }

        else -> Triple("", emptyMap(), exampleDeclarationsStore)
    }

    val path = "${escapeSpaceInPath(request.path)}$query"

    val requestLineGherkin = GherkinClause("$method $path", When)

    return Triple(listOf(requestLineGherkin), newTypes, newExamples)
}

fun formFieldsToGherkin(
    formFields: Map<String, String>,
    types: Map<String, Pattern>,
    exampleDeclarations: ExampleDeclarations
): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclarations> {
    val (dictionaryTypeMap, newTypes, newExamples) = dictionaryToDeclarations(
        stringMapToValueMap(formFields),
        types,
        exampleDeclarations
    )

    val formFieldClauses =
        dictionaryTypeMap.entries.map { entry -> GherkinClause("form-field ${entry.key} ${entry.value.pattern}", When) }

    return Triple(formFieldClauses, newTypes, exampleDeclarations.plus(newExamples))
}

fun listOfExcludedHeaders(): List<String> = HttpHeaders.UnsafeHeadersList.plus(
    arrayOf(
        HttpHeaders.ContentLength,
        HttpHeaders.ContentType,
        HttpHeaders.TransferEncoding,
        HttpHeaders.Upgrade
    )
).distinct().map { it.lowercase() }

fun escapeSpaceInPath(path: String): String {
    return path.split("/").joinToString("/") { segment ->
        URLEncoder.encode(segment, StandardCharsets.UTF_8.toString()).replace("+", "%20")
    }
}

fun urlDecodePathSegments(url: String): String {
    if("://" !in url)
        return decodePath(url)

    return URLParts(url).withDecodedPathSegments()
}

fun decodePath(path: String): String {
    return path.split("/").joinToString("/") { segment ->
        URLDecoder.decode(segment, StandardCharsets.UTF_8.toString())
    }
}

fun singleLineJson(json: String): String {
    return json
        .replace(Regex("\\s*([{}\\[\\]:,])\\s*"), "$1") // Remove spaces around structural characters
        .replace(Regex("\\s+"), " ") // Replace any remaining sequences of whitespace with a single space
}

fun formatJson(json: String): String {
    return if (getBooleanValue("SPECMATIC_PRETTY_PRINT", true))
        json
    else
        singleLineJson(json)
}