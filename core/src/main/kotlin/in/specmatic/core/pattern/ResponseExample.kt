package `in`.specmatic.core.pattern

import `in`.specmatic.core.HttpResponse

interface ResponseExample {
    fun bodyPattern(): Pattern
    val responseExample: HttpResponse
}