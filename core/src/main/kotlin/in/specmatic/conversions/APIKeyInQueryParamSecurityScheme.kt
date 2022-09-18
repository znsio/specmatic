package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.StringValue

class APIKeyInQueryParamSecurityScheme(val name: String) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Boolean {
        return httpRequest.queryParams.containsKey(name)
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.minus(name))
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.plus(name to StringPattern().generate(Resolver()).toStringLiteral()))
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        val queryParamValueType = row.getField(name).let {
            if (isPatternToken(it))
                parsedPattern(it)
            else
                ExactValuePattern(StringValue(string = it))
        }

        return requestPattern.copy(
            urlMatcher = requestPattern.urlMatcher?.copy(
                queryPattern = requestPattern.urlMatcher.queryPattern.plus(name to queryParamValueType)
            )
        )

    }

    override fun isInRow(row: Row): Boolean = row.containsField(name)
}
