package `in`.specmatic.core.log

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStubResponse

class HttpLogMessage(var requestTime: String = "", var request: HttpRequest = HttpRequest(), var responseTime: String = "", var response: HttpResponse = HttpResponse.OK, var contractPath: String = ""):
    LogMessage {
    fun addRequest(httpRequest: HttpRequest) {
        requestTime = CurrentDate().getDateStringValue()
        this.request = httpRequest
    }

    fun addRequest(httpRequest: HttpRequest, requestTime: String) {
        this.requestTime = requestTime
        this.request = httpRequest
    }

    fun addResponse(httpResponse: HttpResponse) {
        responseTime = CurrentDate().getDateStringValue()
        this.response = httpResponse
    }

    override fun toLogString(): String {
        val prefix = "  "

        val mainMessage = listOf(
            "",
            "--------------------",
            "${prefix}Request at $requestTime",
            request.toLogString("$prefix$prefix"),
            "${prefix}Response at $responseTime",
            response.toLogString("$prefix$prefix")
        )

        val suffix = listOf("")

        return (mainMessage + suffix).joinToString(System.lineSeparator())
    }

    override fun toJSONObject(): JSONObjectValue {
        val log = mutableMapOf<String, Value>()

        log["requestTime"] = StringValue(requestTime)
        log["http-request"] = request.toJSON()
        log["http-response"] = response.toJSON()
        log["responseTime"] = StringValue(responseTime)
        log["contractPath"] = StringValue(contractPath)

        return JSONObjectValue(log.toMap())
    }

    fun addResponse(stubResponse: HttpStubResponse) {
        addResponse(stubResponse.response)
        contractPath = stubResponse.contractPath
    }

    fun logStartRequestTime() {
        this.requestTime = CurrentDate().getDateStringValue()
    }
}