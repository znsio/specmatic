package `in`.specmatic.core.log

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStubResponse

class HttpLogMessage(var requestTime: String = "", var request: HttpRequest = HttpRequest(), var responseTime: String = "", var response: HttpResponse = HttpResponse.OK, var contractPath: String = "", val targetServer: String = ""):
    LogMessage {
    fun addRequest(httpRequest: HttpRequest) {
        requestTime = CurrentDate().toLogString()
        this.request = httpRequest
    }

    fun addRequest(httpRequest: HttpRequest, requestTime: String) {
        this.requestTime = requestTime
        this.request = httpRequest
    }

    fun addResponse(httpResponse: HttpResponse) {
        responseTime = CurrentDate().toLogString()
        this.response = httpResponse
    }

    fun target(): String {
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
            response.toLogString("$linePrefix$linePrefix")
        )

        val messageSuffix = listOf("")

        return (messagePrefix + contractPathLines + mainMessage + messageSuffix).joinToString(System.lineSeparator())
    }

    override fun toJSONObject(): JSONObjectValue {
        val log = mutableMapOf<String, Value>()

        log["requestTime"] = StringValue(requestTime)
        log["http-request"] = request.toJSON()
        log["http-response"] = response.toJSON()
        log["responseTime"] = StringValue(responseTime)
        log["contractMatched"] = StringValue(contractPath)

        return JSONObjectValue(log.toMap())
    }

    fun addResponse(stubResponse: HttpStubResponse) {
        addResponse(stubResponse.response)
        contractPath = stubResponse.contractPath
    }

    fun logStartRequestTime() {
        this.requestTime = CurrentDate().toLogString()
    }
}