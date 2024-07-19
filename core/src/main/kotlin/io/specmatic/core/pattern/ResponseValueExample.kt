package io.specmatic.core.pattern

import io.specmatic.core.HttpResponse

class ResponseValueExample(override val responseExample: HttpResponse) : ResponseExample {
    override fun bodyPattern(): Pattern {
        return responseExample.body.exactMatchElseType()
    }
}