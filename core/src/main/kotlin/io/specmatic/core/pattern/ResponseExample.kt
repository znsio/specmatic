package io.specmatic.core.pattern

import io.specmatic.core.HttpHeadersPattern
import io.specmatic.core.HttpResponse

sealed interface ResponseExample {
    fun bodyPattern(): Pattern
    fun headersPattern(): HttpHeadersPattern

    val responseExample: HttpResponse
}