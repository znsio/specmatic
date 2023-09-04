package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import org.apache.http.HttpHeaders.AUTHORIZATION

class BearerSecurityScheme : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Result {
        httpRequest.headers.let {
            if (!it.containsKey(AUTHORIZATION))
                return Result.Failure("$AUTHORIZATION header is missing in request")
            if (!it[AUTHORIZATION]!!.lowercase().startsWith("bearer"))
                return Result.Failure("$AUTHORIZATION header must be prefixed with \"Bearer\"")
        }
        return Result.Success()
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
