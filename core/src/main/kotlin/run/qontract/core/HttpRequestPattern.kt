package run.qontract.core

import run.qontract.core.Result.Failure
import run.qontract.core.Result.Success
import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import java.net.URI

data class HttpRequestPattern(val headersPattern: HttpHeadersPattern = HttpHeadersPattern(), val urlMatcher: URLMatcher? = null, val method: String? = null, val body: Pattern = NoContentPattern, val formFieldsPattern: Map<String, Pattern> = emptyMap(), val multiPartFormDataPattern: List<MultiPartFormDataPattern> = emptyList()) {
    @Throws(Exception::class)
    fun matches(incomingHttpRequest: HttpRequest, resolver: Resolver): Result {
        val result = incomingHttpRequest to resolver to
                ::matchUrl then
                ::matchMethod then
                ::matchHeaders then
                ::matchFormFields then
                ::matchMultiPartFormData then
                ::matchBody otherwise
                ::handleError toResult
                ::returnResult

        return when(result) {
            is Failure -> result.breadCrumb("REQUEST")
            else -> result
        }
    }

    private fun matchMultiPartFormData(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        if(multiPartFormDataPattern.isEmpty() && httpRequest.multiPartFormData.isEmpty())
            return MatchSuccess(parameters)

        if (multiPartFormDataPattern.isEmpty() && httpRequest.multiPartFormData.isNotEmpty()) {
            return MatchFailure(Failure("The contract expected no multipart data, but the request contained ${httpRequest.multiPartFormData.size} parts.", breadCrumb = "MULTIPART-FORMDATA"))
        }

        if (multiPartFormDataPattern.size != httpRequest.multiPartFormData.size) {
            return MatchFailure(Failure("The contract expected ${multiPartFormDataPattern.size} parts, but the request contained ${httpRequest.multiPartFormData.size} parts.", breadCrumb = "MULTIPART-FORMDATA"))
        }

        val results = multiPartFormDataPattern.mapIndexed { index, type ->
            val results = httpRequest.multiPartFormData.map { value ->
                when (val result = type.matches(value, resolver)) {
                    is Success -> Pair(value, result)
                    is Failure -> Pair(value, result)
                }
            }

            results.find { (_, result) -> result is Success }?.let { listOf(it) } ?: results
        }

        if (results.any { it.first().second !is Success }) {
            val reason = results.flatten().joinToString("\n\n") { (value, result) ->
                "${value.toDisplayableValue()}\n${resultReport(result)}".prependIndent("  ")
            }

            return MatchFailure(Failure("The multipart data in the request did not match the contract:\n$reason", null, "MULTIPART-FORMDATA"))
        }

        return MatchSuccess(parameters)
    }

    fun matchFormFields(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val keys: List<String> = formFieldsPattern.keys.filter { key -> isOptional(key) && withoutOptionality(key) !in httpRequest.formFields }
        if(keys.isNotEmpty())
            return MatchFailure(Failure(message = "Fields $keys not found", breadCrumb = "FORM FIELDS"))

        val result: Result? = formFieldsPattern
            .filterKeys { key -> withoutOptionality(key) in httpRequest.formFields }
            .map { (key, pattern) -> Triple(withoutOptionality(key), pattern, httpRequest.formFields.getValue(key)) }
            .map { (key, pattern, value) ->
                try {
                    when (val result = resolver.matchesPattern(key, pattern, pattern.parse(value, resolver))) {
                        is Failure -> result.breadCrumb("FORM FIELDS").breadCrumb(key)
                        else -> result
                    }
                } catch(e: ContractException) {
                    mismatchResult(pattern, value).breadCrumb("FORM FIELDS").breadCrumb(key)
                } catch(e: Throwable) {
                    mismatchResult(pattern, value).breadCrumb("FORM FIELDS").breadCrumb(key)
                }
            }
            .firstOrNull { it is Failure }

        return when(result) {
            is Failure -> MatchFailure(result)
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchHeaders(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        val headers = httpRequest.headers
        when (val result = this.headersPattern.matches(headers, resolver)) {
            is Failure -> return MatchFailure(result)
        }
        return MatchSuccess(parameters)
    }

    private fun matchBody(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val bodyValue = try {
            if (isPatternToken(httpRequest.bodyString)) StringValue(httpRequest.bodyString) else body.parse(httpRequest.bodyString, resolver)
        } catch(e: ContractException) {
            return MatchFailure(e.failure().breadCrumb("BODY"))
        }

        return when (val result = resolver.matchesPattern(null, body, bodyValue)) {
            is Failure -> MatchFailure(result.breadCrumb("BODY"))
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchMethod(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, _) = parameters
        method.let {
            return if (it != httpRequest.method)
                MatchFailure(mismatchResult(method ?: "", httpRequest.method ?: "").breadCrumb("METHOD"))
            else
                MatchSuccess(parameters)
        }
    }

    private fun matchUrl(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        urlMatcher.let {
            val result = urlMatcher!!.matches(URI(httpRequest.path!!),
                    httpRequest.queryParams,
                    resolver)
            return if (result is Failure)
                MatchFailure(result.breadCrumb("URL"))
            else
                MatchSuccess(parameters)
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
                body.generate(resolver).let { value ->
                    newRequest = newRequest.updateBody(value)
                    newRequest = newRequest.updateHeader("Content-Type", value.httpContentType)
                }
            }

            newRequest = newRequest.copy(headers = headers)

            val formFieldsValue = attempt(breadCrumb = "FORM FIELDS") { formFieldsPattern.mapValues { (key, pattern) -> attempt(breadCrumb = key) { resolver.generate(key, pattern).toString() } } }
            newRequest = when (formFieldsValue.size) {
                0 -> newRequest
                else -> newRequest.copy(
                        formFields = formFieldsValue,
                        headers = newRequest.headers.plus("Content-Type" to "application/x-www-form-urlencoded"))
            }

            val multipartData = attempt(breadCrumb = "MULTIPART DATA") { multiPartFormDataPattern.mapIndexed { index, multiPartFormDataPattern -> attempt(breadCrumb = "[$index]") { multiPartFormDataPattern.generate(resolver) } } }
            when(multipartData.size) {
                0 -> newRequest
                else -> newRequest.copy(
                        multiPartFormData = multipartData,
                        headers = newRequest.headers.plus("Content-Type" to "multipart/form-data")
                )
            }
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpRequestPattern> {
        return attempt(breadCrumb = "REQUEST") {
            val newURLMatchers = urlMatcher?.newBasedOn(row, resolver) ?: listOf<URLMatcher?>(null)
            val newBodies = attempt(breadCrumb = "BODY") { body.newBasedOn(row, resolver) }
            val newHeadersPattern = headersPattern.newBasedOn(row, resolver)
            val newFormFieldsPatterns = newBasedOn(formFieldsPattern, row, resolver)
            val newFormDataPartLists = multiPartFormDataPattern.map { it.newBasedOn(row, resolver) }.let {
                if(it.isEmpty()) {
                    listOf(multiPartFormDataPattern)
                }
                else it
            }

            newURLMatchers.flatMap { newURLMatcher ->
                newBodies.flatMap { newBody ->
                    newHeadersPattern.flatMap { newHeadersPattern ->
                        newFormFieldsPatterns.flatMap { newFormFieldsPattern ->
                            newFormDataPartLists.map { newFormDataPartList ->
                                HttpRequestPattern(
                                        headersPattern = newHeadersPattern,
                                        urlMatcher = newURLMatcher,
                                        method = method,
                                        body = newBody,
                                        formFieldsPattern = newFormFieldsPattern,
                                        multiPartFormDataPattern = newFormDataPartList)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "$method ${urlMatcher.toString()}"
    }
}

fun missingParam(missingValue: String): ContractException {
    return ContractException("$missingValue is missing. Can't generate the contract test.")
}
