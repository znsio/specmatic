package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.isPatternToken
import `in`.specmatic.core.pattern.parsedPattern
import `in`.specmatic.core.value.StringValue

interface OpenAPISecurityScheme {
    fun matches(httpRequest: HttpRequest): Result
    fun removeParam(httpRequest: HttpRequest): HttpRequest
    fun addTo(httpRequest: HttpRequest): HttpRequest
    fun addTo(requestPattern: HttpRequestPattern, row: Row): HttpRequestPattern
    fun isInRow(row: Row): Boolean
}

fun addToHeaderType(
    headerName: String,
    row: Row,
    requestPattern: HttpRequestPattern
): HttpRequestPattern {
    val headerValueType = row.getField(headerName).let {
        if (isPatternToken(it))
            parsedPattern(it)
        else
            ExactValuePattern(StringValue(string = it))
    }

    return requestPattern.copy(
        headersPattern = requestPattern.headersPattern.copy(
            pattern = requestPattern.headersPattern.pattern.plus(headerName to headerValueType)
        )
    )
}
