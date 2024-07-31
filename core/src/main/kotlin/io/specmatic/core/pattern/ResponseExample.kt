package io.specmatic.core.pattern

import io.specmatic.core.HttpHeadersPattern
import io.specmatic.core.HttpResponse

interface ResponseExample {
    fun bodyPattern(): Pattern
    fun headersPattern(): HttpHeadersPattern

    val responseExample: HttpResponse
}