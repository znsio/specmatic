package io.specmatic.test

import io.specmatic.core.log.HttpLogMessage

object TestInteractionsLog {

    val testHttpLogMessages = mutableListOf<HttpLogMessage>()
    val stubHttpLogMessages = mutableListOf<HttpLogMessage>()

    fun addHttpLog(httpLogMessage: HttpLogMessage) {
        if(httpLogMessage.isTestLog()) {
            testHttpLogMessages.add(httpLogMessage)
            return
        }

        stubHttpLogMessages.add(httpLogMessage)
    }

    fun HttpLogMessage.duration() = (responseTime?.toEpochMillis() ?: requestTime.toEpochMillis()) - requestTime.toEpochMillis()

    fun HttpLogMessage.displayName() = scenario?.testDescription()
}