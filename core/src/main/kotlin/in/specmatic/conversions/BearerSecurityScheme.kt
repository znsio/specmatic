package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import org.apache.http.HttpHeaders.AUTHORIZATION

class BearerSecurityScheme : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Boolean {
        return httpRequest.headers.containsKey(AUTHORIZATION)
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(headers = httpRequest.headers.minus(AUTHORIZATION))
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(
            headers = httpRequest.headers.plus(
                AUTHORIZATION to "Bearer " + StringPattern().generate(Resolver()).toStringLiteral(),
            ),
        )
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        return addToHeaderType(AUTHORIZATION, row, requestPattern)
    }

    override fun isInRow(row: Row): Boolean = row.containsField(AUTHORIZATION)
}
