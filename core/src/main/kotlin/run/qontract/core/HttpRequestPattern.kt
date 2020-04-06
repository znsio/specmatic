package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.test.ContractTestException.Companion.missingParam
import java.io.UnsupportedEncodingException
import java.net.URI

data class HttpRequestPattern(var headersPattern: HttpHeadersPattern = HttpHeadersPattern(), var urlPattern: URLPattern? = null, private var method: String? = null, private var body: Pattern = NoContentPattern(), val formFieldsPattern: Map<String, Pattern> = emptyMap()) {
    @Throws(UnsupportedEncodingException::class)
    fun updateWith(urlPattern: URLPattern) {
        this.urlPattern = urlPattern
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
                    when (val result = asPattern(pattern, key).matches(pattern.parse(value, resolver), resolver)) {
                        is Result.Failure -> result.breadCrumb("FORM FIELDS").breadCrumb(key)
                        else -> result
                    }
                } catch(e: ContractParseException) {
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
        when (val result = this.headersPattern.matches(headers, resolver.makeCopy())) {
            is Result.Failure -> return MatchFailure(result.breadCrumb("HEADERS"))
        }
        return MatchSuccess(parameters)
    }

    private fun matchBody(parameters: Pair<HttpRequest, Resolver>): MatchingResult<Pair<HttpRequest, Resolver>> {
        val (httpRequest, resolver) = parameters
        return when (val result = body?.matches(httpRequest.body, withNumericStringPattern(resolver))) {
            is Result.Failure -> MatchFailure(result.breadCrumb("PAYLOAD"))
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
        urlPattern.let {
            val result = urlPattern!!.matches(URI(httpRequest.path!!),
                    httpRequest.queryParams,
                    resolver.makeCopy())
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
        val newRequest = HttpRequest()

        if (method == null) {
            throw missingParam("HTTP method")
        }
        if (urlPattern == null) {
            throw missingParam("URL path pattern")
        }
        newRequest.updateMethod(method!!)
        newRequest.updatePath(urlPattern!!.generatePath(resolver.makeCopy()))
        val queryParams = urlPattern!!.generateQuery(resolver.makeCopy())
        for (key in queryParams.keys) {
            newRequest.updateQueryParam(key, queryParams[key] ?: "")
        }
        val headers = headersPattern.generate(resolver)

        val body = body
        body.generate(resolver).let { value ->
            newRequest.updateBody(value)
            headers.put("Content-Type", value.httpContentType)
        }

        headers.map { (key, value) -> newRequest.updateHeader(key, value) }

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
        val newURLMatchers = urlPattern?.newBasedOn(row, resolver.makeCopy()) ?: listOf<URLPattern?>(null)
        val newBodies = body.newBasedOn(row, resolver.makeCopy())
        val newHeadersPattern = headersPattern.newBasedOn(row)
        val newFormFieldsPatterns = newBasedOn(formFieldsPattern, row, resolver)

        return newURLMatchers.flatMap { newURLMatcher ->
            newBodies.flatMap { newBody ->
                newHeadersPattern.flatMap { newHeadersPattern ->
                    newFormFieldsPatterns.map { newFormFieldsPattern ->
                        HttpRequestPattern(
                                headersPattern = newHeadersPattern,
                                urlPattern = newURLMatcher,
                                method = method,
                                body = newBody,
                                formFieldsPattern = newFormFieldsPattern)
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "$method ${urlPattern.toString()}"
    }
}
