package io.specmatic.core.log

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Scenario
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import io.specmatic.stub.HttpStubResponse

data class HttpLogMessage(
    var requestTime: CurrentDate = CurrentDate(),
    var request: HttpRequest = HttpRequest(),
    var responseTime: CurrentDate? = null,
    var response: HttpResponse? = null,
    var contractPath: String = "",
    val targetServer: String = "",
    val comment: String? = null,
    var scenario: Scenario? = null,
    var exception: Exception? = null
) :
    LogMessage {
    fun addRequest(httpRequest: HttpRequest) {
        requestTime = CurrentDate()
        this.request = httpRequest
    }

    fun addResponse(httpResponse: HttpResponse) {
        responseTime = CurrentDate()
        this.response = httpResponse
    }

    fun addException(exception: Exception) {
        this.exception = exception
    }

    private fun target(): String {
        return if(targetServer.isNotBlank()) {
            "to $targetServer "
        } else ""
    }

    override fun toLogString(): String {
        val linePrefix = "  "

        val messagePrefix = listOf(
            "",
            "--------------------",
        )

        val commentLines = if(comment != null) {
            listOf(
                "${linePrefix}",
                "${comment.prependIndent(linePrefix)}",
                "${linePrefix}",
                "${linePrefix}-----",
                "${linePrefix}")
        } else {
            emptyList()
        }

        val contractPathLines = if(contractPath.isNotBlank()) {
            listOf(
                "${linePrefix}Contract matched: $contractPath",
                ""
            )
        } else {
            emptyList()
        }

        val mainMessage = listOf(
            "${linePrefix}Request ${target()}at $requestTime",
            request.toLogString("$linePrefix$linePrefix"),
            "",
            "${linePrefix}Response at $responseTime",
            response?.toLogString("$linePrefix$linePrefix")
        )

        val messageSuffix = listOf("")
        return (messagePrefix + commentLines + contractPathLines + mainMessage + messageSuffix).joinToString(System.lineSeparator())
    }

    override fun toJSONObject(): JSONObjectValue {
        val log = mutableMapOf<String, Value>()

        log["requestTime"] = StringValue(requestTime.toLogString())
        log["http-request"] = request.toJSON()
        log["http-response"] = response?.toJSON() ?: JSONObjectValue()
        log["responseTime"] = StringValue(responseTime?.toLogString() ?: "")
        log["contractMatched"] = StringValue(contractPath)

        return JSONObjectValue(log.toMap())
    }

    fun addResponse(stubResponse: HttpStubResponse) {
        addResponse(stubResponse.response)
        contractPath = stubResponse.contractPath
    }

    fun logStartRequestTime() {
        this.requestTime = CurrentDate()
    }
}