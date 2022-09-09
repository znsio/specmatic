package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest

interface OpenAPISecurityScheme {
    fun matches(httpRequest: HttpRequest): Boolean
    fun removeParam(httpRequest: HttpRequest): HttpRequest
    fun addTo(httpRequest: HttpRequest): HttpRequest
}
