package io.specmatic.stub

import io.specmatic.core.HttpRequest

interface RequestInterceptor {
    fun interceptRequest(httpRequest: HttpRequest): HttpRequest?
}