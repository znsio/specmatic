package `in`.specmatic.core

import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStubResponse
import `in`.specmatic.stub.getDateStringValue

class HttpLogMessage(var requestTime: String = "", var request: HttpRequest = HttpRequest(), var responseTime: String = "", var response: HttpResponse = HttpResponse.OK, var contractPath: String = ""): LogMessage {
    fun addRequest(httpRequest: HttpRequest) {
        requestTime = getDateStringValue()
        this.request = httpRequest
    }

    fun addRequest(httpRequest: HttpRequest, requestTime: String) {
        this.requestTime = requestTime
        this.request = httpRequest
    }

    fun addResponse(httpResponse: HttpResponse, responseTime: String) {
        this.responseTime = responseTime
        this.response = httpResponse
    }

    fun addResponse(httpResponse: HttpResponse) {
        responseTime = getDateStringValue()
        this.response = httpResponse
    }

    override fun toLogString(): String {
        val prefix = "  "
        val separator = "$prefix--------------------"

        val mainMessage = listOf(
            "",
            "====================",
            "Request time: $requestTime",
            "Response time: $responseTime",
            separator,
            request.toLogString("  "),
            separator,
            response.toLogString("  "))

        val suffix = listOf(
            "",
            ""
        )

        return mainMessage.plus(suffix).joinToString(System.lineSeparator())
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
}