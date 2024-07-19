package io.specmatic.core

import io.ktor.util.*
import io.specmatic.conversions.NoSecurityScheme
import io.specmatic.conversions.OpenAPISecurityScheme
import io.specmatic.core.Result.Failure
import io.specmatic.core.Result.Success
import io.specmatic.core.pattern.*
import io.specmatic.core.value.StringValue

private const val MULTIPART_FORMDATA_BREADCRUMB = "MULTIPART-FORMDATA"
private const val FORM_FIELDS_BREADCRUMB = "FORM-FIELDS"
const val CONTENT_TYPE = "Content-Type"

private val invalidRequestStatuses = listOf(400, 422)

data class HeaderMatchParams(val request: HttpRequest, val headersResolver: Resolver?, val defaultResolver: Resolver, val failures: List<Failure>)

data class HttpRequestPattern(
    val headersPattern: HttpHeadersPattern = HttpHeadersPattern(),
    val httpPathPattern: HttpPathPattern? = null,
    val httpQueryParamPattern: HttpQueryParamPattern = HttpQueryParamPattern(emptyMap()),
    val method: String? = null,
    val body: Pattern = EmptyStringPattern,
    val formFieldsPattern: Map<String, Pattern> = emptyMap(),
    val multiPartFormDataPattern: List<MultiPartFormDataPattern> = emptyList(),
    val securitySchemes: List<OpenAPISecurityScheme> = listOf(NoSecurityScheme())
) {
    fun matches(incomingHttpRequest: HttpRequest, resolver: Resolver, headersResolver: Resolver? = null, requestBodyReqex: Regex? = null): Result {
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
                {
                    (request, resolver, failures) ->
                    if (requestBodyReqex?.matches(request.bodyString) == false)
                        MatchSuccess(Triple(request, resolver, failures.plus(Failure("Request did not match regex $requestBodyReqex"))))
                    else
                        MatchSuccess(Triple(request, resolver, failures))
                } then
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

        val matchFailures = mutableListOf<Failure>()
        val matchingSecurityScheme: OpenAPISecurityScheme = securitySchemes.firstOrNull {
            when (val result = it.matches(httpRequest)) {
                is Failure -> false.also { matchFailures.add(result) }
                is Success -> true
            }
        } ?: return MatchSuccess(Triple(httpRequest, resolver, failures.plus(matchFailures)))

        return MatchSuccess(Triple(matchingSecurityScheme.removeParam(httpRequest), resolver, failures))
    }

    fun matchesSignature(other: HttpRequestPattern): Boolean =
        httpPathPattern!!.path == other.httpPathPattern!!.path && method.equals(method)

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
        val (httpRequest, resolver, _: List<Failure>) = parameters

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

        val result = matchesPath(httpRequest.path!!, resolver)

        return if (result is Failure)
            MatchFailure(result)
        else
            MatchSuccess(parameters)
    }

    fun matchesPath(
        path: String,
        resolver: Resolver
    ) = httpPathPattern!!.matches(path, resolver)

    private fun matchQuery(parameters: Triple<HttpRequest, Resolver, List<Failure>>): MatchingResult<Triple<HttpRequest, Resolver, List<Failure>>> {
        val (httpRequest, resolver, failures) = parameters

        val result = httpQueryParamPattern.matches(httpRequest, resolver)

        return if (result is Failure)
            MatchSuccess(Triple(httpRequest, resolver, failures.plus(result)))
        else
            MatchSuccess(parameters)
    }

    fun generate(request: HttpRequest, resolver: Resolver): HttpRequestPattern {
        var requestPattern = HttpRequestPattern()

        return attempt(breadCrumb = "REQUEST") {
            if (method == null) {
                throw missingParam("HTTP method")
            }
            if (httpPathPattern == null) {
                throw missingParam("URL path")
            }

            requestPattern = requestPattern.copy(method = request.method)

            requestPattern = attempt(breadCrumb = "URL") {
                val path = request.path ?: ""
                val pathTypes = pathToPattern(path)
                val queryParamTypes = toTypeMapForQueryParameters(request.queryParams, httpQueryParamPattern, resolver)
                requestPattern.copy(httpPathPattern = HttpPathPattern(pathTypes, path), httpQueryParamPattern = HttpQueryParamPattern(queryParamTypes))
            }

            requestPattern = attempt(breadCrumb = "HEADERS") {
                val headersFromRequest = toTypeMap(
                    toLowerCaseKeys(request.headers),
                    toLowerCaseKeys(headersPattern.pattern),
                    resolver
                )

                requestPattern.copy(headersPattern = HttpHeadersPattern(headersFromRequest))
            }

            requestPattern = attempt(breadCrumb = "BODY") {
                requestPattern.copy(
                    body = when (request.body) {
                        is StringValue -> encompassedType(request.bodyString, null, body, resolver)
                        else -> request.body.exactMatchElseType()
                    }
                )
            }

            requestPattern = attempt(breadCrumb = "FORM FIELDS") {
                requestPattern.copy(formFieldsPattern = toTypeMap(request.formFields, formFieldsPattern, resolver))
            }

            val multiPartFormDataRequestMap =
                request.multiPartFormData.fold(emptyMap<String, MultiPartFormDataValue>()) { acc, part ->
                    acc.plus(part.name to part)
                }

            attempt(breadCrumb = "MULTIPART DATA") {
                requestPattern.copy(multiPartFormDataPattern = multiPartFormDataPattern.filter {
                    withoutOptionality(it.name) in multiPartFormDataRequestMap
                }.map {
                    val key = withoutOptionality(it.name)
                    multiPartFormDataRequestMap.getValue(key).inferType()
                })
            }
        }
    }

    private fun <ValueType> toLowerCaseKeys(map: Map<String, ValueType>) =
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

    private fun toTypeMapForQueryParameters(
        queryParams: QueryParameters,
        httpQueryParamPattern: HttpQueryParamPattern,
        resolver: Resolver
    ): Map<String, Pattern> {
        val patterns: Map<String, Pattern> = httpQueryParamPattern.queryPatterns

        val paramsWithinPattern = patterns.filterKeys { withoutOptionality(it) in queryParams.paramPairs.map { it.first } }.map {
            val key = withoutOptionality(it.key)
            val pattern = it.value

            attempt(breadCrumb = key) {
                val values: List<String> = queryParams.getValues(key)
                when (pattern) {
                    is QueryParameterArrayPattern -> {
                        val queryParameterValuePatterns = values.map { value ->
                            encompassedType(value, key, pattern.pattern.first(), resolver)
                        }
                        key to QueryParameterArrayPattern(queryParameterValuePatterns, key)
                    }

                    is QueryParameterScalarPattern -> {
                        key to QueryParameterScalarPattern(
                            encompassedType(
                                values.single(),
                                key,
                                pattern.pattern,
                                resolver
                            )
                        )
                    }

                    else -> {
                        throw ContractException("Non query type: $pattern found")
                    }
                }
            }
        }.toMap()

        val paramsUnaccountedFor = queryParams.paramPairs.filter { (name, _) ->
            name !in paramsWithinPattern
        }.groupBy { (name, _) ->
            name
        }

        val paramsOutsidePattern = if(httpQueryParamPattern.additionalProperties != null) {
            val results = paramsUnaccountedFor.map { (name, values) ->
                values.map { (_, rawValue) ->
                    val value = httpQueryParamPattern.additionalProperties.parse(rawValue, resolver)
                    httpQueryParamPattern.additionalProperties.matches(value, resolver)
                }
            }.flatten()

            val matchResult = Result.fromResults(results)

            if(matchResult is Failure)
                throw ContractException(matchResult.toFailureReport())

            paramsUnaccountedFor.map { (name, values) ->
                val pattern = if (values.size > 1) {
                    QueryParameterArrayPattern(values.map { ExactValuePattern(StringValue(it.second)) }, name)
                } else {
                    QueryParameterScalarPattern(ExactValuePattern(StringValue(values.single().second)))
                }

                name to pattern
            }.toMap()
        } else {
            emptyMap()
        }

        return paramsWithinPattern + paramsOutsidePattern
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
            if (httpPathPattern == null) {
                throw missingParam("URL path")
            }
            newRequest = newRequest.updateMethod(method)
            attempt(breadCrumb = "URL") {
                newRequest = newRequest.updatePath(httpPathPattern.generate(resolver))
                val queryParams = httpQueryParamPattern.generate(resolver)
                for (queryParam in queryParams) {
                    newRequest = newRequest.updateQueryParam(queryParam.first, queryParam.second)
                }
            }
            val headers = headersPattern.generate(resolver)

            val body = body
            attempt(breadCrumb = "BODY") {
                resolver.withCyclePrevention(body) {cyclePreventedResolver ->
                    body.generate(cyclePreventedResolver).let { value ->
                        newRequest = newRequest.updateBody(value)
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

    fun newBasedOn(row: Row, initialResolver: Resolver, status: Int = 0): Sequence<ReturnValue<HttpRequestPattern>> {
        val resolver = when (status) {
            in invalidRequestStatuses -> initialResolver.invalidRequestResolver()
            else -> initialResolver
        }

        return returnValue(breadCrumb = "REQUEST") {
            val newHttpPathPatterns: Sequence<ReturnValue<HttpPathPattern?>> = httpPathPattern?.let { httpPathPattern ->
                val newURLPathSegmentPatternsList = httpPathPattern.newBasedOn(row, resolver)
                newURLPathSegmentPatternsList.map { HttpPathPattern(it, httpPathPattern.path) }.map { HasValue(it) }
            } ?: sequenceOf(HasValue(null))

            val newQueryParamsPatterns: Sequence<ReturnValue<HttpQueryParamPattern>> =
                if (status.toString().startsWith("2")) {
                    val new = httpQueryParamPattern.newBasedOn(row, resolver)
                    httpQueryParamPattern.addComplimentaryPatterns(new, row, resolver)
                } else {
                    httpQueryParamPattern.readFrom(row, resolver)
                }.map {
                    HasValue(HttpQueryParamPattern(it))
                }

            val newHeadersPattern: Sequence<ReturnValue<HttpHeadersPattern>> = if (status.toString().startsWith("2")) {
                val new = headersPattern.newBasedOn(row, resolver)
                headersPattern.addComplimentaryPatterns(new, row, resolver)
            } else {
                headersPattern.readFrom(row, resolver)
            }.map { HasValue(it) }

            val newBodies: Sequence<ReturnValue<Pattern>> = attempt(breadCrumb = "BODY") {
                body.let {
                    if (it is DeferredPattern && row.containsField(it.pattern)) {
                        val example = row.getField(it.pattern)
                        sequenceOf(HasValue(ExactValuePattern(it.parse(example, resolver))))
                    } else if (it.typeAlias?.let { p -> isPatternToken(p) } == true && row.containsField(it.typeAlias!!)) {
                        val example = row.getField(it.typeAlias!!)
                        sequenceOf(HasValue(ExactValuePattern(it.parse(example, resolver))))
                    } else if (it is XMLPattern && it.referredType?.let { referredType -> row.containsField("($referredType)") } == true) {
                        val referredType = "(${it.referredType})"
                        val example = row.getField(referredType)
                        sequenceOf(HasValue(ExactValuePattern(it.parse(example, resolver))))
                    } else if (row.containsField("(REQUEST-BODY)")) {
                        val example = row.getField("(REQUEST-BODY)")
                        val value = it.parse(example, resolver)

                        val requestBodyAsIs = if (!isInvalidRequestResponse(status)) {
                            val result = body.matches(value, resolver)

                            if (result is Failure)
                                throw ContractException(result.toFailureReport())
                            else
                                ExactValuePattern(value)
                        } else
                            ExactValuePattern(value)

                        if (status.toString().startsWith("2"))
                            resolver.generateHttpRequestbodies(body, row, requestBodyAsIs, value)
                        else
                            sequenceOf(HasValue(requestBodyAsIs))
                    } else {
                        resolver.generateHttpRequestbodies(body, row)
                    }
                }
            }

            val newFormFieldsPatterns: Sequence<ReturnValue<Map<String, Pattern>>> =
                newMapBasedOn(formFieldsPattern, row, resolver).map { it.value }.map { HasValue(it) }
            val newFormDataPartLists: Sequence<ReturnValue<List<MultiPartFormDataPattern>>> =
                newMultiPartBasedOn(multiPartFormDataPattern, row, resolver).map { HasValue(it) }

            newHttpPathPatterns.flatMap("PATH") { newPathParamPattern ->
                newQueryParamsPatterns.flatMap("QUERY") { newQueryParamPattern ->
                    newBodies.flatMap("BODY") { newBody ->
                        newHeadersPattern.flatMap("HEADERS") { newHeadersPattern ->
                            newFormFieldsPatterns.flatMap("FORM-FIELDS") { newFormFieldsPattern ->
                                newFormDataPartLists.flatMap("FORM-DATA") { newFormDataPartList ->
                                    val newRequestPattern = HttpRequestPattern(
                                        headersPattern = newHeadersPattern.value,
                                        httpPathPattern = newPathParamPattern.value,
                                        httpQueryParamPattern = newQueryParamPattern.value,
                                        method = method,
                                        body = newBody.value,
                                        formFieldsPattern = newFormFieldsPattern.value,
                                        multiPartFormDataPattern = newFormDataPartList.value
                                    )

                                    val schemeInRow = securitySchemes.find { it.isInRow(row) }

                                    if (schemeInRow != null) {
                                        listOf(schemeInRow.addTo(newRequestPattern, row)).asSequence()
                                    } else {
                                        securitySchemes.asSequence().map {
                                            newRequestPattern.copy(securitySchemes = listOf(it))
                                        }
                                    }.map { HasValue(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun <T, U> Sequence<T>.flatMap(breadCrumb: String = "", fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
        val iterator = this.iterator()

        return sequence {
            try {
                while(iterator.hasNext()) {
                    val next = iterator.next()

                    val results: Sequence<ReturnValue<U>> = fn(next)

                    yieldAll(results)
                }
            } catch(t: Throwable) {
                yield(HasException(t, breadCrumb = breadCrumb))
            }
        }
    }

    private fun isInvalidRequestResponse(status: Int): Boolean {
        return status in invalidRequestStatuses
    }

    fun newBasedOn(resolver: Resolver): Sequence<HttpRequestPattern> {
        return attempt(breadCrumb = "REQUEST") {
            val newHttpPathPatterns = httpPathPattern?.let { httpPathPattern ->
                val newURLPathSegmentPatternsList = httpPathPattern.newBasedOn(resolver)
                newURLPathSegmentPatternsList.map { HttpPathPattern(it, httpPathPattern.path) }
            } ?: sequenceOf<HttpPathPattern?>(null)

            val newQueryParamsPatterns = httpQueryParamPattern.newBasedOn(resolver).map { HttpQueryParamPattern(it) }
            val newBodies = attempt(breadCrumb = "BODY") {
                resolver.withCyclePrevention(body) { cyclePreventedResolver ->
                    body.newBasedOn(cyclePreventedResolver)
                }
            }
            val newHeadersPattern = headersPattern.newBasedOn(resolver)
            val newFormFieldsPatterns = newBasedOn(formFieldsPattern, resolver)
            //TODO: Backward Compatibility
            val newFormDataPartLists = newMultiPartBasedOn(multiPartFormDataPattern, Row(), resolver)

            newHttpPathPatterns.flatMap { newPathParamPattern ->
                newQueryParamsPatterns.flatMap { newQueryParamPattern ->
                    newBodies.flatMap { newBody ->
                        newHeadersPattern.flatMap { newHeadersPattern ->
                            newFormFieldsPatterns.flatMap { newFormFieldsPattern ->
                                newFormDataPartLists.flatMap { newFormDataPartList ->
                                    val newRequestPattern = HttpRequestPattern(
                                        headersPattern = newHeadersPattern,
                                        httpPathPattern = newPathParamPattern,
                                        httpQueryParamPattern = newQueryParamPattern,
                                        method = method,
                                        body = newBody,
                                        formFieldsPattern = newFormFieldsPattern,
                                        multiPartFormDataPattern = newFormDataPartList
                                    )

                                    securitySchemes.map {
                                        newRequestPattern.copy(securitySchemes = listOf(it))
                                    }.asSequence().map { HasValue(it) }
                                }
                            }
                        }
                    }
                }
            }.map { it.value }
        }
    }

    fun testDescription(): String {
        return "$method ${httpPathPattern.toString()}"
    }

    fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<HttpRequestPattern>> {
        return returnValue(breadCrumb = "REQUEST") {
            val newHttpPathPatterns: Sequence<ReturnValue<HttpPathPattern>?> =
                httpPathPattern?.let { httpPathPattern ->
                    httpPathPattern.negativeBasedOn(row, resolver)
                        .map { it.ifValue { HttpPathPattern(it, httpPathPattern.path) } }
                } ?: sequenceOf(null)

            val newQueryParamsPatterns =
                httpQueryParamPattern.negativeBasedOn(row, resolver).map { it.ifValue { HttpQueryParamPattern(it) } }

            val newBodies: Sequence<ReturnValue<out Pattern>> = returnValue(breadCrumb = "BODY") {
                body.let {
                    if (it is DeferredPattern && row.containsField(it.pattern)) {
                        val example = row.getField(it.pattern)
                        sequenceOf(HasValue(ExactValuePattern(it.parse(example, resolver))))
                    } else if (it.typeAlias?.let { p -> isPatternToken(p) } == true && row.containsField(it.typeAlias!!)) {
                        val example = row.getField(it.typeAlias!!)
                        sequenceOf(HasValue(ExactValuePattern(it.parse(example, resolver))))
                    } else if (it is XMLPattern && it.referredType?.let { referredType -> row.containsField("($referredType)") } == true) {
                        val referredType = "(${it.referredType})"
                        val example = row.getField(referredType)
                        sequenceOf(HasValue(ExactValuePattern(it.parse(example, resolver))))
                    } else if (row.containsField("(REQUEST-BODY)")) {
                        val example = row.getField("(REQUEST-BODY)")
                        val value = it.parse(example, resolver)
                        val result = body.matches(value, resolver)
                        if (result is Failure)
                            throw ContractException(result.toFailureReport())

                        body.negativeBasedOn(row.noteRequestBody(), resolver)
                    } else {
                        body.negativeBasedOn(row, resolver)
                    }
                }
            }

            val newHeadersPattern = headersPattern.negativeBasedOn(row, resolver)
            val newFormFieldsPatterns = newMapBasedOn(formFieldsPattern, row, resolver).map { it.value }
            val newFormDataPartLists = newMultiPartBasedOn(multiPartFormDataPattern, row, resolver)

            sequence {
                try {
                    // If security schemes are present, for now we'll just take the first scheme and assign it to each negative request pattern.
                    // Ideally we should generate negative patterns from the security schemes and use them.
                    val positivePattern: HttpRequestPattern =
                        newBasedOn(
                            row,
                            resolver,
                            400
                        ).first().value.copy(securitySchemes = listOf(securitySchemes.first()))

                    newHttpPathPatterns.forEach { pathParamPatternR ->
                        if (pathParamPatternR != null) {
                            yield(pathParamPatternR.ifValue { pathParamPattern ->
                                positivePattern.copy(httpPathPattern = pathParamPattern)
                            })
                        }
                    }
                    newQueryParamsPatterns.forEach { queryParamPatternR ->
                        yield(
                            queryParamPatternR.ifValue { queryParamPattern ->
                                positivePattern.copy(httpQueryParamPattern = queryParamPattern)
                            }
                        )
                    }
                    newBodies.forEach { newBodyPatternR ->
                        yield(
                            newBodyPatternR.ifValue { newBodyPattern -> positivePattern.copy(body = newBodyPattern) }
                        )
                    }
                    newHeadersPattern.forEach { newHeaderPatternR ->
                        yield(
                            newHeaderPatternR.ifValue { newHeaderPattern ->
                                positivePattern.copy(headersPattern = newHeaderPattern)
                            }
                        )
                    }
                    newFormFieldsPatterns.forEach { newFormFieldPattern ->
                        yield(
                            HasValue(positivePattern.copy(formFieldsPattern = newFormFieldPattern))
                        )
                    }
                    newFormDataPartLists.forEach { newFormDataPartListPattern ->
                        yield(
                            HasValue(positivePattern.copy(multiPartFormDataPattern = newFormDataPartListPattern))
                        )
                    }
                } catch (t: Throwable) {
                    yield(HasException(t))
                }
            }
        }
    }

    fun addPathParamsToRows(requestPath: String, row: Row, resolver: Resolver): Row {
        return httpPathPattern?.let { httpPathPattern ->
            val pathParams = httpPathPattern.extractPathParams(requestPath, resolver)
            return row.addFields(pathParams)
        } ?: row
    }

}

fun missingParam(missingValue: String): ContractException {
    return ContractException("$missingValue is missing. Can't generate the contract test.")
}

fun newMultiPartBasedOn(
    partList: List<MultiPartFormDataPattern>,
    row: Row,
    resolver: Resolver
): Sequence<List<MultiPartFormDataPattern>> {
    val values = partList.map { part ->
        attempt(breadCrumb = part.name) {
            part.newBasedOn(row, resolver)
        }
    }

    return multiPartListCombinations(values)
}

fun multiPartListCombinations(values: List<Sequence<MultiPartFormDataPattern?>>): Sequence<List<MultiPartFormDataPattern>> {
    if (values.isEmpty())
        return sequenceOf(emptyList())

    val value: Sequence<MultiPartFormDataPattern?> = values.last()
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
