package io.specmatic.stub.listener

import io.specmatic.core.*
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.utilities.exceptionCauseMessage
import java.io.File

interface MockEventListener {
    fun onRespond(data: MockEvent)
}

data class MockEvent (
    val name: String,
    val details: String,
    val request: HttpRequest,
    val requestTime: Long,
    val response: HttpResponse?,
    val responseTime: Long?,
    val scenario: Scenario?,
    val result: TestResult,
) {
    constructor(logMessage: HttpLogMessage) : this(
        name = logMessage.toName(),
        details = logMessage.toDetails(),
        request = logMessage.request,
        requestTime = logMessage.requestTime.toEpochMillis(),
        response = logMessage.response,
        responseTime = logMessage.responseTime?.toEpochMillis(),
        scenario = logMessage.scenario,
        result = logMessage.toResult()
    )
}

private fun HttpLogMessage.toResult(): TestResult {
    return when {
        this.examplePath != null || this.scenario != null && response?.status !in invalidRequestStatuses -> TestResult.Success
        scenario == null -> TestResult.MissingInSpec
        else -> TestResult.Failed
    }
}

private fun HttpLogMessage.toDetails(): String {
    return when {
        this.examplePath != null -> "Request Matched Example: ${this.examplePath}"
        this.scenario != null && response?.status !in invalidRequestStatuses -> "Request Matched Contract ${scenario?.apiDescription}"
        this.exception != null -> "Invalid Request\n${exception?.let(::exceptionCauseMessage)}"
        else -> response?.body?.toStringLiteral() ?: "Request Didn't Match Contract"
    }
}

private fun HttpLogMessage.toName(): String {
    val scenario = this.scenario ?: return "Unknown Request"
    return scenario.copy(exampleName = this.examplePath?.let(::File)?.name).testDescription()
}
