package io.specmatic.stub.listener

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Scenario

interface MockEventListener {
    fun call(
        request: HttpRequest,
        response: HttpResponse,
        scenario: Scenario
    )
}