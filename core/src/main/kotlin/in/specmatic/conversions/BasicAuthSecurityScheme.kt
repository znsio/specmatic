package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import org.apache.http.HttpHeaders.AUTHORIZATION
import java.util.Base64

data class BasicAuthSecurityScheme(private val token: String? = null) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Result {
        val authHeaderValue: String = httpRequest.headers[AUTHORIZATION]
            ?: return Result.Failure("$AUTHORIZATION header is missing in request")

        if (!authHeaderValue.lowercase().startsWith("basic"))
            return Result.Failure("$AUTHORIZATION header must be prefixed with \"Basic\"")

        val base64Credentials = authHeaderValue.substringAfter(" ").trim()

        return validateBase64EncodedCredentials(base64Credentials)
    }

    private fun validateBase64EncodedCredentials(base64Credentials: String): Result {
        try {
            val decodedBytes = Base64.getDecoder().decode(base64Credentials)
            val credentials = String(decodedBytes)

            if (!credentials.contains(":"))
                return Result.Failure("Base64-encoded credentials in $AUTHORIZATION header is not in the form username:password")
        } catch (e: IllegalArgumentException) {
            return Result.Failure("Invalid base64 encoding in $AUTHORIZATION header")
        }

        return Result.Success()
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(headers = httpRequest.headers.minus(AUTHORIZATION))
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(
            headers = httpRequest.headers.plus(
                AUTHORIZATION to getAuthorizationHeaderValue()
            )
        )
    }

    override fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern {
        return addToHeaderType(AUTHORIZATION, row, requestPattern)
    }

    override fun isInRow(row: Row): Boolean = row.containsField(AUTHORIZATION)
    private fun getAuthorizationHeaderValue(): String {
        val validToken = if(token != null)
            validatedToken(token)
        else
            randomBasicAuthCredentials()

        return "Basic $validToken"
    }

    private fun validatedToken(token: String): String {
        val result = validateBase64EncodedCredentials(token)

        if(result is Result.Failure)
            throw ContractException(result.reportString())

        return token
    }

    private fun randomBasicAuthCredentials(): String =
        StringPattern()
            .generate(Resolver())
            .toStringLiteral().let {
                String(Base64.getEncoder().encode("$it:$it".toByteArray()))
            }
}
