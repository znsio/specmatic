package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.test.ContractTestException.Companion.missingParam
import java.io.UnsupportedEncodingException
import java.net.URI

data class HttpRequestPattern(var headersPattern: HttpHeadersPattern = HttpHeadersPattern(), var urlMatcher: URLMatcher? = null, private var method: String? = null, private var body: Pattern? = NoContentPattern()) {
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
                ::matchBody otherwise
                ::handleError toResult
                ::returnResult

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
            val result = urlMatcher!!.matches(URI(httpRequest.path),
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

    @Throws(Exception::class)
    fun generate(resolver: Resolver): HttpRequest {
        val newRequest = HttpRequest()

        if (method == null) {
            throw missingParam("HTTP method")
        }
        if (urlMatcher == null) {
            throw missingParam("URL path pattern")
        }
        newRequest.setMethod(method!!)
        newRequest.updatePath(urlMatcher!!.generatePath(resolver.copy()))
        val queryParams = urlMatcher!!.generateQuery(resolver.copy())
        for (key in queryParams.keys) {
            newRequest.setQueryParam(key, queryParams[key] ?: "")
        }
        val headers = headersPattern.generate(resolver)

        val body = body
        if (body != null) {
            body.generate(resolver).let { value ->
                newRequest.setBody(value)
                headers.put("Content-Type", value.httpContentType)
            }

            headers.map { (key, value) -> newRequest.setHeader(key, value) }
        }

        return newRequest
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpRequestPattern> {
        val newURLMatchers = urlMatcher?.newPatternsBasedOn(row, resolver.copy()) ?: listOf<URLMatcher?>(null)
        val newBodies = body?.newBasedOn(row, resolver.copy()) ?: listOf<Pattern?>(null)
        val newHeadersPattern = headersPattern.newBasedOn(row)

        return newURLMatchers.flatMap { newURLMatcher ->
            newBodies.flatMap { newBody ->
                newHeadersPattern.map { newHeadersPattern ->
                    HttpRequestPattern(newHeadersPattern, newURLMatcher, method, newBody)
                }
            }
        }
    }

    override fun toString(): String {
        return "$method ${urlMatcher.toString()}"
    }
}

