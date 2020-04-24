package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.EmptyString
import run.qontract.core.value.StringValue
import java.io.UnsupportedEncodingException
import java.net.URI

data class HttpRequestPattern(var headersPattern: HttpHeadersPattern = HttpHeadersPattern(), var urlMatcher: URLMatcher? = null, private var method: String? = null, private var body: Pattern = NoContentPattern, val formFieldsPattern: Map<String, Pattern> = emptyMap()) {
    @Throws(UnsupportedEncodingException::class)
    fun updateWith(urlMatcher: URLMatcher) {
        this.urlMatcher = urlMatcher
    }

    @Throws(Exception::class)
    fun matches(incomingHttpRequest: HttpRequest, resolver: Resolver): Result {
        val result = incomingHttpRequest to resolver to
                ::matchUrl then
                ::matchMethod then
                ::matchHeaders then
                ::matchFormFields then
                ::matchBody otherwise
                ::handleError toResult
                ::returnResult

        return when(result) {
            is Result.Failure -> result.breadCrumb("REQUEST")
            else -> result
        }
    }

    fun matchFormFields(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val keys: List<String> = formFieldsPattern.keys.filter { key -> isOptional(key) && withoutOptionality(key) !in httpRequest.formFields }
        if(keys.isNotEmpty())
            return MatchFailure(Result.Failure(message = "Fields $keys not found", breadCrumb = "FORM FIELDS"))

        val result: Result? = formFieldsPattern
            .filterKeys { key -> withoutOptionality(key) in httpRequest.formFields }
            .map { (key, pattern) -> Triple(withoutOptionality(key), pattern, httpRequest.formFields.getValue(key)) }
            .map { (key, pattern, value) ->
                try {
                    when (val result = resolver.matchesPattern(key, pattern, pattern.parse(value, resolver))) {
                        is Result.Failure -> result.breadCrumb("FORM FIELDS").breadCrumb(key)
                        else -> result
                    }
                } catch(e: ContractException) {
                    mismatchResult(pattern, value).breadCrumb("FORM FIELDS").breadCrumb(key)
                }
            }
            .firstOrNull { it is Result.Failure }

        return when(result) {
            is Result.Failure -> MatchFailure(result)
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchHeaders(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        val headers = httpRequest.headers
        when (val result = this.headersPattern.matches(headers, resolver)) {
            is Result.Failure -> return MatchFailure(result)
        }
        return MatchSuccess(parameters)
    }

    private fun matchBody(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val resolverWithNumericString = withNumericStringPattern(resolver)

        val bodyValue = try {
            if (isPatternToken(httpRequest.bodyString)) StringValue(httpRequest.bodyString) else body.parse(httpRequest.bodyString, resolverWithNumericString)
        } catch(e: ContractException) {
            return MatchFailure(e.result().breadCrumb("BODY"))
        }

        return when (val result = resolverWithNumericString.matchesPattern(null, body, bodyValue)) {
            is Result.Failure -> MatchFailure(result.breadCrumb("BODY"))
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
            return if (result is Result.Failure)
                MatchFailure(result.breadCrumb("URL"))
            else
                MatchSuccess(parameters)
        }
    }

    fun updateMethod(method: String) {
        this.method = method.toUpperCase()
    }

    fun bodyPattern(bodyContent: String?) = this.copy(body = parsedPattern(bodyContent!!))
    fun bodyPattern(newBody: Pattern) = this.copy(body = newBody)

    fun setBodyPattern(bodyContent: String?) {
        body = parsedPattern(bodyContent!!)
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
            newRequest = newRequest.updateMethod(method!!)
            attempt(breadCrumb = "URL") {
                newRequest = newRequest.updatePath(urlMatcher!!.generatePath(resolver))
                val queryParams = urlMatcher!!.generateQuery(resolver)
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
            when (formFieldsValue.size) {
                0 -> newRequest
                else -> {
                    newRequest.copy(
                            formFields = formFieldsValue,
                            headers = newRequest.headers.plus("Content-Type" to "application/x-www-form-urlencoded"))
                }
            }
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpRequestPattern> {
        return attempt(breadCrumb = "REQUEST") {
            val newURLMatchers = urlMatcher?.newBasedOn(row, resolver) ?: listOf<URLMatcher?>(null)
            val newBodies = attempt(breadCrumb = "BODY") { body.newBasedOn(row, resolver) }
            val newHeadersPattern = headersPattern.newBasedOn(row, resolver)
            val newFormFieldsPatterns = newBasedOn(formFieldsPattern, row, resolver)

            newURLMatchers.flatMap { newURLMatcher ->
                newBodies.flatMap { newBody ->
                    newHeadersPattern.flatMap { newHeadersPattern ->
                        newFormFieldsPatterns.map { newFormFieldsPattern ->
                            HttpRequestPattern(
                                    headersPattern = newHeadersPattern,
                                    urlMatcher = newURLMatcher,
                                    method = method,
                                    body = newBody,
                                    formFieldsPattern = newFormFieldsPattern)
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
