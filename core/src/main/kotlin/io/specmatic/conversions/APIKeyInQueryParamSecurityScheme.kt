package io.specmatic.conversions

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.*
import io.specmatic.core.value.StringValue

data class APIKeyInQueryParamSecurityScheme(val name: String, private val apiKey:String?) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Result {
        return if (httpRequest.queryParams.containsKey(name)) Result.Success() else Result.Failure("API-key named $name was not present in query string")
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.minus(name))
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.plus(name to (apiKey ?: StringPattern().generate(Resolver()).toStringLiteral())))
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
