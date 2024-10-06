package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse

interface ResponseInterceptor {
    fun interceptResponse(httpRequest: HttpRequest, httpResponse: HttpResponse): HttpResponse?
}