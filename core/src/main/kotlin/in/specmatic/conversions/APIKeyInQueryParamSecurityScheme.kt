package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.pattern.StringPattern

class APIKeyInQueryParamSecurityScheme(val name: String) : OpenAPISecurityScheme {
    override fun matches(httpRequest: HttpRequest): Boolean {
        return httpRequest.queryParams.containsKey(name)
    }

    override fun removeParam(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.minus(name))
    }

    override fun addTo(httpRequest: HttpRequest): HttpRequest {
        return httpRequest.copy(queryParams = httpRequest.queryParams.plus(name to StringPattern().generate(Resolver()).toStringLiteral()))
    }
}
