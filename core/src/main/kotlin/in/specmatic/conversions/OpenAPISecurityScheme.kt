package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpRequestPattern
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.*
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

internal fun headerPatternFromRequest(
    request: HttpRequest,
    headerName: String
): Map<String, Pattern> {
    val headerValue = request.headers[headerName]

    if (headerValue != null) {
        return mapOf(headerName to ExactValuePattern(StringValue(headerValue)))
    }

    return emptyMap()
}

internal fun queryPatternFromRequest(
    request: HttpRequest,
    queryParamName: String
): Map<String, Pattern> {
    val queryParamValue = request.queryParams.getValues(queryParamName)

    if(queryParamValue.isEmpty())
        return emptyMap()

    return mapOf(
        queryParamName to ExactValuePattern(StringValue(queryParamValue.first()))
    )
}
