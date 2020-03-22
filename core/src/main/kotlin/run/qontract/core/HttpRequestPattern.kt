package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.test.ContractTestException.Companion.missingParam
import java.io.UnsupportedEncodingException
import java.net.URI

data class HttpRequestPattern(var headersPattern: HttpHeadersPattern = HttpHeadersPattern(), var urlMatcher: URLMatcher? = null, private var method: String? = null, private var body: Pattern? = NoContentPattern(), val formFieldsPattern: Map<String, Pattern> = emptyMap()) {
    @Throws(UnsupportedEncodingException::class)
    fun updateWith(urlMatcher: URLMatcher) {
        this.urlMatcher = urlMatcher
    }

    @Throws(Exception::class)
    fun matches(incomingHttpRequest: HttpRequest, resolver: Resolver) =
        incomingHttpRequest to resolver to
                ::matchUrl then
                ::matchMethod then
                ::matchHeaders then
                ::matchFormFields then
                ::matchBody otherwise
                ::handleError toResult
                ::returnResult

    fun matchFormFields(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters

        val keys: List<String> = formFieldsPattern.keys.filter { key -> isOptional(key) && withoutOptionality(key) !in httpRequest.formFields }
        if(keys.isNotEmpty())
            return MatchFailure(Result.Failure("Keys $keys not found in request form fields ${httpRequest.formFields}"))

        val result: Result? = formFieldsPattern
            .filterKeys { key -> withoutOptionality(key) in httpRequest.formFields }
            .map { (key, pattern) -> Triple(withoutOptionality(key), pattern, httpRequest.formFields.getValue(key)) }
            .map { (key, pattern, value) ->
                try {
                    asPattern(pattern, key).matches(pattern.parse(value, resolver), resolver)
                } catch(e: ContractParseException) {
                    Result.Failure("""Failed to parse "$value" as ${pattern.javaClass}""")
                }
            }
            .firstOrNull { it is Result.Failure }

        return when(result) {
            is Result.Failure -> MatchFailure(result.add("Form fields did not match"))
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchHeaders(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        val headers = httpRequest.headers
        when (val result = this.headersPattern.matches(headers, resolver.copy())) {
            is Result.Failure -> return MatchFailure(result.add("Request Headers did not match"))
        }
        return MatchSuccess(parameters)
    }

    private fun matchBody(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        return when (val result = body?.matches(httpRequest.body, resolver.copy().also { it.addCustomPattern("(number)", NumericStringPattern()) })) {
            is Result.Failure -> MatchFailure(result.add("Request body did not match"))
            else -> MatchSuccess(parameters)
        }
    }

    private fun matchMethod(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, _) = parameters
        method.let {
            return if (it != httpRequest.method)
                MatchFailure(Result.Failure("Method did not match. Expected: $method Actual: ${httpRequest.method}"))
            else
                MatchSuccess(parameters)
        }
    }

    private fun matchUrl(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        urlMatcher.let {
            val result = urlMatcher!!.matches(URI(httpRequest.path!!),
                    httpRequest.queryParams,
                    resolver.copy())
            return if (result is Result.Failure)
                MatchFailure(result.add("URL did not match"))
            else
                MatchSuccess(parameters)
        }
    }

    fun updateMethod(method: String) {
        this.method = method.toUpperCase()
    }

    fun bodyPattern(bodyContent: String?) = this.copy(body = parsedPattern(bodyContent!!))

    fun setBodyPattern(bodyContent: String?) {
        body = parsedPattern(bodyContent!!)
    }

    fun generate(resolver: Resolver): HttpRequest {
        val newRequest = HttpRequest()

        if (method == null) {
            throw missingParam("HTTP method")
        }
        if (urlMatcher == null) {
            throw missingParam("URL path pattern")
        }
        newRequest.updateMethod(method!!)
        newRequest.updatePath(urlMatcher!!.generatePath(resolver.copy()))
        val queryParams = urlMatcher!!.generateQuery(resolver.copy())
        for (key in queryParams.keys) {
            newRequest.updateQueryParam(key, queryParams[key] ?: "")
        }
        val headers = headersPattern.generate(resolver)

        val body = body
        if (body != null) {
            body.generate(resolver).let { value ->
                newRequest.updateBody(value)
                headers.put("Content-Type", value.httpContentType)
            }

            headers.map { (key, value) -> newRequest.updateHeader(key, value) }
        }

        val formFieldsValue = formFieldsPattern.mapValues { (key, pattern) -> asPattern(pattern, key).generate(resolver).toString() }
        return when(formFieldsValue.size) {
            0 -> newRequest
            else -> {
                newRequest.copy(
                        formFields = formFieldsValue,
                        headers = HashMap(newRequest.headers.plus("Content-Type" to "application/x-www-form-urlencoded")))            }
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpRequestPattern> {
        val newURLMatchers = urlMatcher?.newBasedOn(row, resolver.copy()) ?: listOf<URLMatcher?>(null)
        val newBodies = body?.newBasedOn(row, resolver.copy()) ?: listOf<Pattern?>(null)
        val newHeadersPattern = headersPattern.newBasedOn(row)
        val newFormFieldsPatterns = newBasedOn(formFieldsPattern, row, resolver)

        return newURLMatchers.flatMap { newURLMatcher ->
            newBodies.flatMap { newBody ->
                newHeadersPattern.flatMap { newHeadersPattern ->
                    newFormFieldsPatterns.map { newFormFieldsPattern ->
                        HttpRequestPattern(newHeadersPattern, newURLMatcher, method, newBody, newFormFieldsPattern )
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "$method ${urlMatcher.toString()}"
    }
}
