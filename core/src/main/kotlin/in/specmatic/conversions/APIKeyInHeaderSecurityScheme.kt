package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import `in`.specmatic.core.value.StringValue

data class APIKeyInHeaderSecurityScheme(val name: String, private val apiKey:String?) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Result {
        return if (httpRequest.headers.containsKey(name)) Result.Success() else Result.Failure("API-key named $name was not present as a header")
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(headers = httpRequest.headers.minus(name))
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(headers = httpRequest.headers.plus(name to (apiKey ?: StringPattern().generate(Resolver()).toStringLiteral())))
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        return addToHeaderType(name, row, requestPattern)
    }

    override fun isInRow(row: Row): Boolean = row.containsField(name)
}
