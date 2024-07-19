package io.specmatic.core.pattern

import io.specmatic.core.HttpResponse

interface ResponseExample {
    fun bodyPattern(): Pattern
    val responseExample: HttpResponse
}