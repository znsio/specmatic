package `in`.specmatic.core.pattern

import `in`.specmatic.core.HttpResponse

class ResponseValueExample(override val responseExample: HttpResponse) : ResponseExample {
    override fun bodyPattern(): Pattern {
        return responseExample.body.exactMatchElseType()
    }
}