package io.specmatic.test

import io.specmatic.core.log.HttpLogMessage

object DataRecorder {

    val testHttpLogMessages = mutableListOf<HttpLogMessage>()
    val stubHttpLongMessages = mutableListOf<HttpLogMessage>()

    fun addHttpLog(httpLogMessage: HttpLogMessage) {
        if(httpLogMessage.scenario != null) {
            testHttpLogMessages.add(httpLogMessage)
        } else {
            stubHttpLongMessages.add(httpLogMessage)
        }
    }

    fun HttpLogMessage.duration() = (responseTime?.toEpochMillis() ?: requestTime.toEpochMillis()) - requestTime.toEpochMillis()

    fun HttpLogMessage.displayName() = scenario?.testDescription() ?: "Stub Response"
}