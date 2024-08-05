package io.specmatic.core.pattern

import io.specmatic.core.HttpHeadersPattern
import io.specmatic.core.HttpResponse

class ResponseSchemaExample(override val responseExample: HttpResponse) : ResponseExample {
    override fun bodyPattern(): Pattern {
        return responseExample.body.deepPattern()
    }

    override fun headersPattern(): HttpHeadersPattern {
        return HttpHeadersPattern(responseExample.headers.mapValues { AnythingPattern })
    }
}