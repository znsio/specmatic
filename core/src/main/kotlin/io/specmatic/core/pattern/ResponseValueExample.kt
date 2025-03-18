package io.specmatic.core.pattern

import io.specmatic.core.HttpHeadersPattern
import io.specmatic.core.HttpResponse

class ResponseValueExample(override val responseExample: HttpResponse) : ResponseExample {
    override fun bodyPattern(): Pattern {
        return responseExample.body.exactMatchElseType()
    }

    override fun headersPattern(): HttpHeadersPattern {
        return HttpHeadersPattern(responseExample.headers.mapValues { stringToPattern(it.value, it.key) })
    }
}