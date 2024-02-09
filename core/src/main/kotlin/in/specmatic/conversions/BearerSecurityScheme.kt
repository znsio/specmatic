package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import io.ktor.http.*
import org.apache.http.HttpHeaders.AUTHORIZATION

class BearerSecurityScheme(private val token: String? = null) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Result {
        val authHeaderValue: String? = httpRequest.headers["Authorization"] ?: httpRequest.headers.entries.find {
            it.key.equals("authorization", ignoreCase = true)
        }?.value

        if (authHeaderValue == null) {
            return Result.Failure("$AUTHORIZATION header is missing in request")
        }

        if (!authHeaderValue.lowercase().startsWith("bearer")) {
            return Result.Failure("$AUTHORIZATION header must be prefixed with \"Bearer\"")
        }

        return Result.Success()
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        val headersWithoutAuthorization = httpRequest.headers.filterKeys { !it.equals(AUTHORIZATION, ignoreCase = true) }
        return httpRequest.copy(headers = headersWithoutAuthorization)
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        val updatedHeaders = httpRequest.headers.toMutableMap().apply {
            // Remove any existing Authorization header (case-insensitive)
            remove(keys.firstOrNull { it.equals(AUTHORIZATION, ignoreCase = true) })

            // Add the new Authorization header
            put(AUTHORIZATION, getAuthorizationHeaderValue())
        }
        return httpRequest.copy(headers = updatedHeaders)
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        return addToHeaderType(AUTHORIZATION, row, requestPattern)
    }

    override fun isInRow(row: Row): Boolean {
        return row.columnNames.any { it.equals(AUTHORIZATION, ignoreCase = true) }
    }

    private fun getAuthorizationHeaderValue(): String {
        return "Bearer " + (token ?: StringPattern().generate(Resolver()).toStringLiteral())
    }
}
