package `in`.specmatic.core

import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStubResponse
import `in`.specmatic.stub.getDateStringValue

data class JSONHTTPLog(var requestTime: String = "", var httpRequest: HttpRequest = HttpRequest(), var responseTime: String = "", var response: HttpResponse = HttpResponse.OK, var contractPath: String = "") {
    fun addRequest(httpRequest: HttpRequest) {
        requestTime = getDateStringValue()
        this.httpRequest = httpRequest
    }

    fun addRequest(httpRequest: HttpRequest, requestTime: String) {
        this.requestTime = requestTime
        this.httpRequest = httpRequest
    }

    fun addResponse(httpResponse: HttpResponse, requestTime: String) {
        this.requestTime = requestTime
        this.response = httpResponse
    }

    fun addResponse(httpResponse: HttpResponse) {
        requestTime = getDateStringValue()
        this.response = httpResponse
    }

    fun toLogString(): String {
        val log = mutableMapOf<String, Value>()

        log["requestTime"] = StringValue(getDateStringValue())
        log["http-request"] = httpRequest.toJSON()
        log["http-response"] = response.toJSON()
        log["responseTime"] = StringValue(getDateStringValue())
        log["contractPath"] = StringValue(contractPath)

        return JSONObjectValue(log.toMap()).displayableValue() + ","
    }

    fun addResponse(stubResponse: HttpStubResponse) {
        addResponse(stubResponse.response)
        contractPath = stubResponse.contractPath
    }
}