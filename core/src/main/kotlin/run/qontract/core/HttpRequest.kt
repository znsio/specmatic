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

data class HttpRequest(val method: String? = null, val path: String? = null, val headers: Map<String, String> = emptyMap(), val body: Value = EmptyString, val queryParams: Map<String, String> = emptyMap(), val formFields: Map<String, String> = emptyMap(), val multiPartFormData: List<MultiPartFormDataValue> = emptyList()) {
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

    fun toJSON(): JSONObjectValue {
        val requestMap = mutableMapOf<String, Value>()

        requestMap["path"] = path?.let { StringValue(it) } ?: StringValue("/")
        method?.let { requestMap["method"] = StringValue(it) } ?: throw ContractException("Can't serialise the request without a method.")

        if (queryParams.isNotEmpty()) requestMap["query"] = JSONObjectValue(queryParams.mapValues { StringValue(it.value) })
        if (headers.isNotEmpty()) requestMap["headers"] = JSONObjectValue(headers.mapValues { StringValue(it.value) })

        when {
            formFields.isNotEmpty() -> requestMap["form-fields"] = JSONObjectValue(formFields.mapValues { StringValue(it.value) })
            multiPartFormData.isNotEmpty() -> {
                val multiPartData = multiPartFormData.map {
                    JSONObjectValue(when(it) {
                        is MultiPartContentValue ->
                            mapOf("name" to StringValue(it.name), "content" to StringValue(it.content.toStringValue()), "contentType" to StringValue(it.content.httpContentType))
                        is MultiPartFileValue ->
                            mapOf("name" to StringValue(it.name), "filename" to StringValue("@${it.filename}")).let { map ->
                                when(it.contentType) {
                                    null -> map
                                    else -> map.plus("contentType" to StringValue(it.contentType))
                                }
                            }.let { map ->
                                when (it.contentEncoding) {
                                    null -> map
                                    else -> map.plus("contentEncoding" to StringValue(it.contentEncoding))
                                }
                            }
                    })
                }

                requestMap["multipart-formdata"] = JSONArrayValue(multiPartData)
            }
            else -> {
                requestMap["body"] = body
            }
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
                multiPartFormData.map { part -> part.toDisplayableValue() }.joinToString("\n")
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
                "form-fields" in json -> {
                    httpRequest.copy(formFields = nativeStringStringMap(json, "form-fields"))
                }
                "multipart-formdata" in json -> {
                    val parts = json.getValue("multipart-formdata")
                    if(parts !is JSONArrayValue)
                        throw ContractException("multipart-formdata must be a json array.")

                    val multiPartData: List<MultiPartFormDataValue> = parts.list.map {
                        if(it !is JSONObjectValue)
                            throw ContractException("All multipart parts must be json object values.")

                        val multiPartSpec = it.jsonObject
                        val name = nativeString(multiPartSpec, "name") ?: throw ContractException("One of the multipart entries does not have a name key")

                        when {
                            multiPartSpec.containsKey("content") -> MultiPartContentValue(name, multiPartSpec.getValue("content"))
                            multiPartSpec.containsKey("filename") -> MultiPartFileValue(name, multiPartSpec.getValue("filename").toStringValue(), multiPartSpec["contentType"]?.toStringValue(), multiPartSpec["contentEncoding"]?.toStringValue())
                            else -> throw ContractException("Multipart entry $name must have either a content key or a filename key")
                        }
                    }

                    httpRequest.copy(multiPartFormData = httpRequest.multiPartFormData.plus(multiPartData))
                }
                "body" in json -> {
                    val body = json["body"]

                    if(body is NullValue)
                        throw ContractException("Either body should have a value or the key should be absent from http-response")

                    httpRequest.updateBody(json.getValue("body"))
                }
                else -> httpRequest
            }
        }

internal fun nativeStringStringMap(json: Map<String, Value>, key: String): Map<String, String> {
    val queryValue = json[key] ?: return emptyMap()

    if(queryValue !is JSONObjectValue)
        throw ContractException("Expected $key to be a json object")

    return queryValue.jsonObject.mapValues { it.value.toString() }
}

internal fun startLinesWith(str: String, startValue: String) =
        str.split("\n").joinToString("\n") { "$startValue$it" }

fun toGherkinClauses(request: HttpRequest): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclaration> {
    return Triple(emptyList<GherkinClause>(), emptyMap<String, Pattern>(), ExampleDeclaration()).let { (clauses, types, exampleDeclaration) ->
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

fun bodyToGherkin(request: HttpRequest, types: Map<String, Pattern>, examples: ExampleDeclaration): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclaration> {
    return when {
        request.multiPartFormData.isNotEmpty() -> multiPartFormDataToGherkin(request.multiPartFormData, types, examples)
        request.formFields.isNotEmpty() -> formFieldsToGherkin(request.formFields, types, examples)
        else -> requestBodyToGherkinClauses(request.body, types, examples)
    }
}

fun firstLineToGherkin(request: HttpRequest, types: Map<String, Pattern>, exampleDeclaration: ExampleDeclaration): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclaration> {
    val method = request.method ?: throw ContractException("Can't generate a qontract without the http method.")

    if (request.path == null)
        throw ContractException("Can't generate a qontract without the url.")

    val (query, newTypes, newExamples) = when {
        request.queryParams.isNotEmpty() -> {
            val (dictionaryType, newTypes, examples) = dictionaryToDeclarations(stringMapToValueMap(request.queryParams), types, exampleDeclaration)

            val query = dictionaryType.entries.joinToString("&") { (key, typeDeclaration) -> "$key=${typeDeclaration.pattern}" }
            Triple("?$query", newTypes, examples)
        }
        else -> Triple("", emptyMap(), exampleDeclaration)
    }

    val path = "${request.path}$query"

    val requestLineGherkin = GherkinClause("$method $path", When)

    return Triple(listOf(requestLineGherkin), newTypes, newExamples)
}

fun multiPartFormDataToGherkin(multiPartFormData: List<MultiPartFormDataValue>, types: Map<String, Pattern>, exampleDeclaration: ExampleDeclaration): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclaration> {
    return multiPartFormData.fold(Triple(emptyList(), types, exampleDeclaration)) { acc, part ->
        val (clauses, newTypes, examples) = acc

        when(part) {
            is MultiPartContentValue -> {
                val (typeDeclaration, newExamples) = part.content.typeDeclarationWithKey(part.name, newTypes, examples)

                val newGherkinClause = GherkinClause("request-part ${part.name} ${typeDeclaration.typeValue}", When)
                Triple(clauses.plus(newGherkinClause), typeDeclaration.types, examples.plus(newExamples))
            }
            is MultiPartFileValue -> {
                val contentType = part.contentType
                val contentEncoding = contentType?.let { part.contentEncoding }

                Triple(clauses.plus(GherkinClause("request-part ${part.name} ${part.filename} $contentType $contentEncoding".trim(), When)), newTypes, examples)
            }
        }
    }
}

fun formFieldsToGherkin(formFields: Map<String, String>, types: Map<String, Pattern>, exampleDeclaration: ExampleDeclaration): Triple<List<GherkinClause>, Map<String, Pattern>, ExampleDeclaration> {
    val (dictionaryTypeMap, newTypes, newExamples) = dictionaryToDeclarations(stringMapToValueMap(formFields), types, exampleDeclaration)

    val formFieldClauses = dictionaryTypeMap.entries.map { entry -> GherkinClause("form-field ${entry.key} ${entry.value.pattern}", When) }

    return Triple(formFieldClauses, newTypes, exampleDeclaration.plus(newExamples))
}
