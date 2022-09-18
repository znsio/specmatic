package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern

class APIKeyInHeaderSecurityScheme(val name: String) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Boolean {
        return httpRequest.headers.containsKey(name)
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(headers = httpRequest.headers.minus(name))
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(headers = httpRequest.headers.plus(name to StringPattern().generate(Resolver()).toStringLiteral()))
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        return addToHeaderType(name, row, requestPattern)
    }

    override fun isInRow(row: Row): Boolean = row.containsField(name)
}
