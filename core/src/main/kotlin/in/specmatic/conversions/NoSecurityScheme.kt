package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.Row

class NoSecurityScheme : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Result {
        return Result.Success()
    }

    override fun equals(other: Any?): Boolean {
        return other is NoSecurityScheme
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        return requestPattern
    }

    override fun isInRow(row: Row): Boolean {
        return false
    }
}
