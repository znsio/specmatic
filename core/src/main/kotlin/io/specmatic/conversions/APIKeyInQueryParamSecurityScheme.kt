package io.specmatic.conversions

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.*
import io.specmatic.core.value.StringValue

data class APIKeyInQueryParamSecurityScheme(val name: String, private val apiKey:String?) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest, resolver: Resolver): Result {
        return if (httpRequest.queryParams.containsKey(name) || resolver.mockMode) Result.Success()
        else Result.Failure(
            breadCrumb = "QUERY-PARAMS.$name",
            message = resolver.mismatchMessages.expectedKeyWasMissing("API-Key", name)
        )
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.minus(name))
    }

    override fun addTo(httpRequest: HttpRequest, resolver: Resolver): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.plus(name to (apiKey ?: resolver.generate("QUERY-PARAMS", name, StringPattern()).toStringLiteral())))
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        val queryParamValueType = row.getField(name).let {
            if (isPatternToken(it))
                parsedPattern(it)
            else
                ExactValuePattern(StringValue(string = it))
        }

        return requestPattern.copy(
            httpQueryParamPattern = requestPattern.httpQueryParamPattern.copy(
                 queryPatterns = requestPattern.httpQueryParamPattern.queryPatterns.plus(name to queryParamValueType)
            )
        )
    }

    override fun isInRow(row: Row): Boolean = row.containsField(name)
}
