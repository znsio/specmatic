package `in`.specmatic.core

import `in`.specmatic.conversions.OpenAPISecurityScheme
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import io.ktor.util.*

private const val MULTIPART_FORMDATA_BREADCRUMB = "MULTIPART-FORMDATA"
private const val FORM_FIELDS_BREADCRUMB = "FORM-FIELDS"
const val CONTENT_TYPE = "Content-Type"

private val invalidRequestStatuses = listOf(400, 422)

data class HeaderMatchParams(val request: HttpRequest, val headersResolver: Resolver?, val defaultResolver: Resolver, val failures: List<Failure>)

data class HttpRequestPattern(
    val headersPattern: HttpHeadersPattern = HttpHeadersPattern(),
    val urlMatcher: URLMatcher? = null,
    val method: String? = null,
    val body: Pattern = EmptyStringPattern,
    val formFieldsPattern: Map<String, Pattern> = emptyMap(),
    val multiPartFormDataPattern: List<MultiPartFormDataPattern> = emptyList(),
    val securitySchemes: List<OpenAPISecurityScheme> = emptyList()
) {
    fun matches(incomingHttpRequest: HttpRequest, resolver: Resolver, headersResolver: Resolver? = null): Result {
        val result = incomingHttpRequest to resolver to
                ::matchPath then
                ::matchMethod then
                ::matchSecurityScheme then
                ::matchQuery then
                { (request, defaultResolver, failures) ->
                    matchHeaders(HeaderMatchParams(request, headersResolver, defaultResolver, failures))
                } then
                ::matchFormFields then
                ::matchMultiPartFormData then
                ::matchBody then
                ::summarize otherwise
                ::handleError toResult
                ::returnResult

        return when (result) {
            is Failure -> result.breadCrumb("REQUEST")
            else -> result
        }
    }

    private fun matchSecurityScheme(parameters: Triple<HttpRequest, Resolver, List<Failure>>): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, resolver, failures) = parameters

        if(securitySchemes.isEmpty())
            return MatchSuccess(Triple(httpRequest, resolver, failures))

        val matchingSecurityScheme = securitySchemes.find { it.matches(httpRequest) }
            ?: return MatchSuccess(Triple(httpRequest, resolver, failures.plus(Failure("No auth params were found in the request"))))

        return MatchSuccess(Triple(matchingSecurityScheme.removeParam(httpRequest), resolver, failures))
    }

    fun matchesSignature(other: HttpRequestPattern): Boolean =
        urlMatcher!!.path == other.urlMatcher!!.path && method.equals(method)

    private fun matchMultiPartFormData(parameters: Triple<HttpRequest, Resolver, List<Failure>>): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, resolver, failures) = parameters

        if (multiPartFormDataPattern.isEmpty() && httpRequest.multiPartFormData.isEmpty()) {
            return MatchSuccess(parameters)
        }

        val results: List<Result?> = multiPartFormDataPattern.map { type ->
            val results = httpRequest.multiPartFormData.map { value ->
                type.matches(value, resolver)
            }

            val result = results.find { it is Success } ?: results.find { it is Failure && it.failureReason != FailureReason.PartNameMisMatch }?.breadCrumb(type.name)?.breadCrumb(MULTIPART_FORMDATA_BREADCRUMB)
            result ?: when {
                isOptional(type.name) -> Success()
                else -> null
            }
        }

        val payloadFailures: List<Failure> = results.filterIsInstance<Failure>()

        val typeKeys = multiPartFormDataPattern.map { withoutOptionality(it.name) }.sorted()
        val valueKeys = httpRequest.multiPartFormData.map { it.name }.sorted()

        val missingInType: List<Failure> = valueKeys.filter { it !in typeKeys }.map {
            Failure(resolver.mismatchMessages.unexpectedKey("part", it)).breadCrumb(it).breadCrumb(MULTIPART_FORMDATA_BREADCRUMB)
        }

        val originalTypeKeys = multiPartFormDataPattern.map { it.name }.sorted()
        val missingInValue = originalTypeKeys.filter { !isOptional(it) }.filter { withoutOptionality(it) !in valueKeys }.map { partName ->
            Failure(resolver.mismatchMessages.expectedKeyWasMissing("part", withoutOptionality(partName))).breadCrumb(withoutOptionality(partName)).breadCrumb(MULTIPART_FORMDATA_BREADCRUMB)
        }

        val allFailures: List<Failure> = missingInValue.plus(missingInType).plus(payloadFailures)

        return if(allFailures.isEmpty())
            MatchSuccess(parameters)
        else
            MatchSuccess(Triple(httpRequest, resolver, failures.plus(allFailures)))
    }

    fun matchFormFields(parameters: Triple<HttpRequest, Resolver, List<Failure>>): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, resolver, failures) = parameters

        val keyErrorResults: List<Failure> = resolver.findKeyErrorList(formFieldsPattern, httpRequest.formFields).map {
            it.missingKeyToResult("form field", resolver.mismatchMessages).breadCrumb(it.name).breadCrumb(FORM_FIELDS_BREADCRUMB)
        }

        val payloadResults: List<Result> = formFieldsPattern
            .filterKeys { key -> withoutOptionality(key) in httpRequest.formFields }
            .map { (key, pattern) -> Triple(withoutOptionality(key), pattern, httpRequest.formFields.getValue(key)) }
            .map { (key, pattern, value) ->
                try {
                    when (val result = resolver.matchesPattern(
                        key, pattern, try {
                            pattern.parse(value, resolver)
                        } catch (e: Throwable) {
                            StringValue(value)
                        }
                    )) {
                        is Failure -> result.breadCrumb(key).breadCrumb(FORM_FIELDS_BREADCRUMB)
                        else -> result
                    }
                } catch (e: ContractException) {
                    e.failure().breadCrumb(key).breadCrumb(FORM_FIELDS_BREADCRUMB)
                } catch (e: Throwable) {
                    mismatchResult(pattern, value).breadCrumb(key).breadCrumb(FORM_FIELDS_BREADCRUMB)
                }
            }

        val allFailures = keyErrorResults.plus(payloadResults.filterIsInstance<Failure>())

        return if(allFailures.isEmpty())
            MatchSuccess(parameters)
        else
            MatchSuccess(Triple(httpRequest, resolver, allFailures))
    }

    private fun matchHeaders(parameters: HeaderMatchParams): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, headersResolver, defaultResolver, failures) = parameters
        val headers = httpRequest.headers
        return when (val result = this.headersPattern.matches(headers, headersResolver ?: defaultResolver)) {
            is Failure -> MatchSuccess(Triple(httpRequest, defaultResolver, failures.plus(result)))
            else -> MatchSuccess(Triple(httpRequest, defaultResolver, failures))
        }
    }

    private fun matchBody(parameters: Triple<HttpRequest, Resolver, List<Failure>>): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, resolver, failures) = parameters

        val result = try {
            val bodyValue =
                if (isPatternToken(httpRequest.bodyString))
                    StringValue(httpRequest.bodyString)
                else
                    body.parse(httpRequest.bodyString, resolver)

            resolver.matchesPattern(null, body, bodyValue).breadCrumb("BODY")
        } catch (e: ContractException) {
            e.failure().breadCrumb("BODY")
        }

        return when (result) {
            is Failure -> MatchSuccess(Triple(httpRequest, resolver, failures.plus(result)))
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchMethod(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, resolver) = parameters
        method.let {
            return if (it != httpRequest.method)
                MatchFailure(mismatchResult(method ?: "", httpRequest.method ?: "").copy(failureReason = FailureReason.MethodMismatch).breadCrumb("METHOD"))
            else
                MatchSuccess(Triple(httpRequest, resolver, emptyList()))
        }
    }

    private fun matchPath(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val result = urlMatcher!!.matchesPath(httpRequest.path!!, resolver)

        return if (result is Failure)
            MatchFailure(result)
        else
            MatchSuccess(parameters)
    }

    private fun matchQuery(parameters: Triple<HttpRequest, Resolver, List<Failure>>): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, resolver, failures) = parameters

        val result = urlMatcher!!.matchesQuery(httpRequest, resolver)

        return if (result is Failure)
            MatchSuccess(Triple(httpRequest, resolver, failures.plus(result.breadCrumb("QUERY-PARAMS"))))
        else
            MatchSuccess(parameters)
    }

    fun generate(request: HttpRequest, resolver: Resolver): HttpRequestPattern {
        var requestType = HttpRequestPattern()

        return attempt(breadCrumb = "REQUEST") {
            if (method == null) {
                throw missingParam("HTTP method")
            }
            if (urlMatcher == null) {
                throw missingParam("URL path")
            }

            requestType = requestType.copy(method = request.method)

            requestType = attempt(breadCrumb = "URL") {
                val path = request.path ?: ""
                val pathTypes = pathToPattern(path)
                val queryParamTypes = toTypeMap(
                    request.queryParams,
                    urlMatcher.queryPattern,
                    resolver
                ).mapKeys { it.key.removeSuffix("?") }

                requestType.copy(urlMatcher = URLMatcher(queryParamTypes, pathTypes, path))
            }

            requestType = attempt(breadCrumb = "HEADERS") {
                requestType.copy(
                    headersPattern = HttpHeadersPattern(
                        toTypeMap(
                            toLowerCaseKeys(request.headers) as Map<String, String>,
                            toLowerCaseKeys(headersPattern.pattern) as Map<String, Pattern>,
                            resolver
                        )
                    )
                )
            }

            requestType = attempt(breadCrumb = "BODY") {
                requestType.copy(
                    body = when (request.body) {
                        is StringValue -> encompassedType(request.bodyString, null, body, resolver)
                        else -> request.body.exactMatchElseType()
                    }
                )
            }

            requestType = attempt(breadCrumb = "FORM FIELDS") {
                requestType.copy(formFieldsPattern = toTypeMap(request.formFields, formFieldsPattern, resolver))
            }

            val multiPartFormDataRequestMap =
                request.multiPartFormData.fold(emptyMap<String, MultiPartFormDataValue>()) { acc, part ->
                    acc.plus(part.name to part)
                }

            attempt(breadCrumb = "MULTIPART DATA") {
                requestType.copy(multiPartFormDataPattern = multiPartFormDataPattern.filter {
                    withoutOptionality(it.name) in multiPartFormDataRequestMap
                }.map {
                    val key = withoutOptionality(it.name)
                    multiPartFormDataRequestMap.getValue(key).inferType()
                })
            }
        }
    }

    private fun toLowerCaseKeys(map: Map<String, Any?>) =
        map.map { (key, value) -> key.toLowerCasePreservingASCIIRules() to value }.toMap()

    private fun toTypeMap(
        values: Map<String, String>,
        types: Map<String, Pattern>,
        resolver: Resolver
    ): Map<String, Pattern> {
        return types.filterKeys { withoutOptionality(it) in values }.mapValues {
            val key = withoutOptionality(it.key)
            val type = it.value

            attempt(breadCrumb = key) {
                val valueString = values.getValue(key)
                encompassedType(valueString, key, type, resolver)
            }
        }
    }

    private fun encompassedType(valueString: String, key: String?, type: Pattern, resolver: Resolver): Pattern {
        return when {
            isPatternToken(valueString) -> resolvedHop(parsedPattern(valueString, key), resolver).let { parsedType ->
                when (val result = type.encompasses(parsedType, resolver, resolver)) {
                    is Success -> parsedType
                    is Failure -> throw ContractException(result.toFailureReport())
                }
            }
            else -> type.parseToType(valueString, resolver)
        }
    }

    fun generate(resolver: Resolver): HttpRequest {
        var newRequest = HttpRequest()

        return attempt(breadCrumb = "REQUEST") {
            if (method == null) {
                throw missingParam("HTTP method")
            }
            if (urlMatcher == null) {
                throw missingParam("URL path")
            }
            newRequest = newRequest.updateMethod(method)
            attempt(breadCrumb = "URL") {
                newRequest = newRequest.updatePath(urlMatcher.generatePath(resolver))
                val queryParams = urlMatcher.generateQuery(resolver)
                for (key in queryParams.keys) {
                    newRequest = newRequest.updateQueryParam(key, queryParams[key] ?: "")
                }
            }
            val headers = headersPattern.generate(resolver)

            val body = body
            attempt(breadCrumb = "BODY") {
                resolver.withCyclePrevention(body) {cyclePreventedResolver ->
                    body.generate(cyclePreventedResolver).let { value ->
                        newRequest = newRequest.updateBody(value)
                        newRequest = newRequest.updateHeader(CONTENT_TYPE, value.httpContentType)
                    }
                }
            }

            newRequest = newRequest.copy(headers = headers)

            val formFieldsValue = attempt(breadCrumb = "FORM FIELDS") {
                formFieldsPattern.mapValues { (key, pattern) ->
                    attempt(breadCrumb = key) {
                        resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
                            cyclePreventedResolver.generate(key, pattern)
                        }.toString()
                    }
                }
            }
            newRequest = when (formFieldsValue.size) {
                0 -> newRequest
                else -> newRequest.copy(
                    formFields = formFieldsValue,
                    headers = newRequest.headers.plus(CONTENT_TYPE to "application/x-www-form-urlencoded")
                )
            }

            newRequest = securitySchemes.fold(newRequest) { request, securityScheme ->
                securityScheme.addTo(request)
            }

            val multipartData = attempt(breadCrumb = "MULTIPART DATA") {
                multiPartFormDataPattern.mapIndexed { index, multiPartFormDataPattern ->
                    attempt(breadCrumb = "[$index]") { multiPartFormDataPattern.generate(resolver) }
                }
            }
            when (multipartData.size) {
                0 -> newRequest
                else -> newRequest.copy(
                    multiPartFormData = multipartData,
                    headers = newRequest.headers.plus(CONTENT_TYPE to "multipart/form-data")
                )
            }
        }
    }

    fun newBasedOn(row: Row, initialResolver: Resolver, status: Int = 0): List<HttpRequestPattern> {
        val resolver = if(status in invalidRequestStatuses)
            initialResolver.invalidRequestResolver()
        else
            initialResolver

        return attempt(breadCrumb = "REQUEST") {
            val newURLMatchers = urlMatcher?.newBasedOn(row, resolver) ?: listOf<URLMatcher?>(null)
                val newBodies: List<Pattern> = attempt(breadCrumb = "BODY") {
                body.let {
                    if(it is DeferredPattern && row.containsField(it.pattern)) {
                        val example = row.getField(it.pattern)
                        listOf(ExactValuePattern(it.parse(example, resolver)))
                    }
                    else if(it.typeAlias?.let { isPatternToken(it) } == true && row.containsField(it.typeAlias!!)) {
                        val example = row.getField(it.typeAlias!!)
                        listOf(ExactValuePattern(it.parse(example, resolver)))
                    }
                    else if(it is XMLPattern && it.referredType?.let { referredType -> row.containsField("($referredType)") } == true) {
                        val referredType = "(${it.referredType})"
                        val example = row.getField(referredType)
                        listOf(ExactValuePattern(it.parse(example, resolver)))
                    } else if(row.containsField("(REQUEST-BODY)")) {
                        val example = row.getField("(REQUEST-BODY)")
                        val value = it.parse(example, resolver)

                        if(! isInvalidRequestResponse(status)) {
                            val result = body.matches(value, resolver)
                            if (result is Failure)
                                throw ContractException(result.toFailureReport())
                        }

                        if(Flags.generativeTestingEnabled()) {
                            val rowWithRequestBodyAsIs = listOf(ExactValuePattern(value))

                            val requestsFromFlattenedRow: List<Pattern> =
                                resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                                    body.newBasedOn(row.flattenRequestBodyIntoRow(), cyclePreventedResolver)
                                }

                            requestsFromFlattenedRow.plus(rowWithRequestBodyAsIs)
                        } else {
                            listOf(ExactValuePattern(value))
                        }
                    } else {

                        if(Flags.generativeTestingEnabled()) {
                            val vanilla = resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                                body.newBasedOn(Row(), cyclePreventedResolver)
                            }
                            val fromExamples = resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                                body.newBasedOn(row, cyclePreventedResolver)
                            }
                            val remainingVanilla = vanilla.filterNot { vanillaType ->
                                fromExamples.any { typeFromExamples ->
                                    vanillaType.encompasses(
                                        typeFromExamples,
                                        resolver,
                                        resolver
                                    ).isSuccess()
                                }
                            }

                            fromExamples.plus(remainingVanilla)
                        } else {
                            resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                                body.newBasedOn(row, cyclePreventedResolver)
                            }
                        }
                    }
                }
            }

            val newHeadersPattern = headersPattern.newBasedOn(row, resolver)
            val newFormFieldsPatterns = newBasedOn(formFieldsPattern, row, resolver)
            val newFormDataPartLists = newMultiPartBasedOn(multiPartFormDataPattern, row, resolver)

            newURLMatchers.flatMap { newURLMatcher ->
                newBodies.flatMap { newBody ->
                    newHeadersPattern.flatMap { newHeadersPattern ->
                        newFormFieldsPatterns.flatMap { newFormFieldsPattern ->
                            newFormDataPartLists.flatMap { newFormDataPartList ->
                                val newRequestPattern = HttpRequestPattern(
                                    headersPattern = newHeadersPattern,
                                    urlMatcher = newURLMatcher,
                                    method = method,
                                    body = newBody,
                                    formFieldsPattern = newFormFieldsPattern,
                                    multiPartFormDataPattern = newFormDataPartList
                                )

                                if(securitySchemes.isEmpty()) {
                                    listOf(newRequestPattern)
                                } else {
                                    val schemeInRow = securitySchemes.find { it.isInRow(row) }

                                    if(schemeInRow != null) {
                                        listOf(schemeInRow.addTo(newRequestPattern, row))
                                    } else {
                                        securitySchemes.map {
                                            newRequestPattern.copy(securitySchemes = listOf(it))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isInvalidRequestResponse(status: Int): Boolean {
        return status in invalidRequestStatuses
    }

    fun newBasedOn(resolver: Resolver): List<HttpRequestPattern> {
        return attempt(breadCrumb = "REQUEST") {
            val newURLMatchers = urlMatcher?.newBasedOn(resolver) ?: listOf<URLMatcher?>(null)
            val newBodies = attempt(breadCrumb = "BODY") {
                resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                    body.newBasedOn(cyclePreventedResolver)
                }
            }
            val newHeadersPattern = headersPattern.newBasedOn(resolver)
            val newFormFieldsPatterns = newBasedOn(formFieldsPattern, resolver)
            //TODO: Backward Compatibility
            val newFormDataPartLists = newMultiPartBasedOn(multiPartFormDataPattern, Row(), resolver)

            newURLMatchers.flatMap { newURLMatcher ->
                newBodies.flatMap { newBody ->
                    newHeadersPattern.flatMap { newHeadersPattern ->
                        newFormFieldsPatterns.flatMap { newFormFieldsPattern ->
                            newFormDataPartLists.flatMap { newFormDataPartList ->
                                val newRequestPattern = HttpRequestPattern(
                                    headersPattern = newHeadersPattern,
                                    urlMatcher = newURLMatcher,
                                    method = method,
                                    body = newBody,
                                    formFieldsPattern = newFormFieldsPattern,
                                    multiPartFormDataPattern = newFormDataPartList
                                )

                                when(securitySchemes) {
                                    emptyList<OpenAPISecurityScheme>() -> listOf(newRequestPattern)
                                    else -> securitySchemes.map {
                                        newRequestPattern.copy(securitySchemes = listOf(it))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun testDescription(): String {
        return "$method ${urlMatcher.toString()}"
    }

    fun negativeBasedOn(row: Row, resolver: Resolver): List<HttpRequestPattern> {
        return attempt(breadCrumb = "REQUEST") {
            val newURLMatchers = urlMatcher?.newBasedOn(row, resolver) ?: listOf<URLMatcher?>(null)
            val newBodies: List<Pattern> = attempt(breadCrumb = "BODY") {
                body.let {
                    if(it is DeferredPattern && row.containsField(it.pattern)) {
                        val example = row.getField(it.pattern)
                        listOf(ExactValuePattern(it.parse(example, resolver)))
                    }
                    else if(it.typeAlias?.let { isPatternToken(it) } == true && row.containsField(it.typeAlias!!)) {
                        val example = row.getField(it.typeAlias!!)
                        listOf(ExactValuePattern(it.parse(example, resolver)))
                    }
                    else if(it is XMLPattern && it.referredType?.let { referredType -> row.containsField("($referredType)") } == true) {
                        val referredType = "(${it.referredType})"
                        val example = row.getField(referredType)
                        listOf(ExactValuePattern(it.parse(example, resolver)))
                    } else if(row.containsField("(REQUEST-BODY)")) {
                        val example = row.getField("(REQUEST-BODY)")
                        val value = it.parse(example, resolver)
                        val result = body.matches(value, resolver)
                        if(result is Failure)
                            throw ContractException(result.toFailureReport())

                        val originalRequest = if(value is JSONObjectValue) {
                            val jsonValues = jsonObjectToValues(value)
                            val jsonValeuRow = Row(
                                columnNames = jsonValues.map { it.first }.toList(),
                                values = jsonValues.map { it.second }.toList())

                            body.negativeBasedOn(jsonValeuRow, resolver)
                        } else {
                            listOf(ExactValuePattern(value))
                        }

                        val flattenedRequests: List<Pattern> = resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                            body.newBasedOn(row.flattenRequestBodyIntoRow(), cyclePreventedResolver)
                        }

                        flattenedRequests.plus(originalRequest)

                        body.negativeBasedOn(row.flattenRequestBodyIntoRow(), resolver)
                    } else {
                        body.negativeBasedOn(row, resolver)
                    }
                }
            }

            val newHeadersPattern = headersPattern.newBasedOn(row, resolver)
            val newFormFieldsPatterns = newBasedOn(formFieldsPattern, row, resolver)
            val newFormDataPartLists = newMultiPartBasedOn(multiPartFormDataPattern, row, resolver)

            newURLMatchers.flatMap { newURLMatcher ->
                newBodies.flatMap { newBody ->
                    newHeadersPattern.flatMap { newHeadersPattern ->
                        newFormFieldsPatterns.flatMap { newFormFieldsPattern ->
                            newFormDataPartLists.flatMap { newFormDataPartList ->
                                val newRequestPattern = HttpRequestPattern(
                                    headersPattern = newHeadersPattern,
                                    urlMatcher = newURLMatcher,
                                    method = method,
                                    body = newBody,
                                    formFieldsPattern = newFormFieldsPattern,
                                    multiPartFormDataPattern = newFormDataPartList
                                )

                                when(securitySchemes) {
                                    emptyList<OpenAPISecurityScheme>() -> listOf(newRequestPattern)
                                    else -> securitySchemes.map {
                                        newRequestPattern.copy(securitySchemes = listOf(it))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun jsonObjectToValues(value: JSONObjectValue): List<Pair<String, String>> {
    val valueMap = value.jsonObject

    return valueMap.entries.map { (key, value) ->
        when(value) {
            is JSONObjectValue -> {
                jsonObjectToValues(value)
            }
            else -> {
                listOf(Pair(key, value.toStringLiteral()))
            }
        }
    }.flatten()
}

fun missingParam(missingValue: String): ContractException {
    return ContractException("$missingValue is missing. Can't generate the contract test.")
}

fun newMultiPartBasedOn(
    partList: List<MultiPartFormDataPattern>,
    row: Row,
    resolver: Resolver
): List<List<MultiPartFormDataPattern>> {
    val values = partList.map { part ->
        attempt(breadCrumb = part.name) {
            part.newBasedOn(row, resolver)
        }
    }

    return multiPartListCombinations(values)
}

fun multiPartListCombinations(values: List<List<MultiPartFormDataPattern?>>): List<List<MultiPartFormDataPattern>> {
    if (values.isEmpty())
        return listOf(emptyList())

    val value: List<MultiPartFormDataPattern?> = values.last()
    val subLists = multiPartListCombinations(values.dropLast(1))

    return subLists.flatMap { list ->
        value.map { type ->
            when (type) {
                null -> list
                else -> list.plus(type)
            }
        }
    }
}
