package io.specmatic.test

import io.specmatic.core.*
import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.StringValue
import kotlin.math.pow

class ResponseMonitor(
    private val feature: Feature, val originalScenario: Scenario, private val response: HttpResponse,
    private val maxRetry: Int = 3, private val backOffDelay: Long = 1000
) {
    private val headerKey: String = "Link"
    private val requestPath: String = "request"
    private val responsePath: String = "response"

    fun waitForResponse(executor: TestExecutor): ReturnValue<HttpResponse> {
        val (monitorScenario, monitorLink) = when(val result = getScenarioAndLink()) {
            is HasValue -> result.value
            is HasException -> return result.cast()
            is HasFailure -> return result.cast()
        }
        val baseURL = (executor as? HttpClient)?.baseURL.orEmpty()

        repeat(maxRetry) { count ->
            try {
                val delay = getBackOffDelay(count)
                Thread.sleep(delay)

                val response = checkStatus(monitorScenario, baseURL, monitorLink)
                monitorScenario.matches(response).throwOnFailure()

                val monitorComplete = response.checkCompletion()
                if (monitorComplete is HasValue) {
                    val (requestFromMonitor, responseFromMonitor) = monitorComplete.value
                    val result = originalScenario.matches(requestFromMonitor, responseFromMonitor, DefaultMismatchMessages, feature.flagsBased)
                    if (result is Result.Failure) {
                        return HasFailure(result, message = "Monitor request / response doesn't match scenario")
                    }
                    return HasValue(responseFromMonitor)
                }
            } catch (e: Exception) { return HasException(e) }
        }

        return HasFailure("Max retries exceeded, monitor link: $monitorLink")
    }

    private fun getScenarioAndLink(): ReturnValue<Pair<Scenario, Link>> {
        val processingScenario = response.getProcessingScenario() ?: return HasFailure("No processing scenario found in response")
        val processingScenarioResult = processingScenario.matches(response)
        if (processingScenarioResult is Result.Failure) {
            return HasFailure(processingScenarioResult, message = "Response doesn't match processing scenario")
        }

        val monitorLink = response.extractMonitorLinkFromHeader(headerKey)
        val monitorScenario = monitorLink?.monitorScenario() ?: return HasFailure("No monitor scenario found for link: $monitorLink")

        return HasValue(Pair(monitorScenario, monitorLink))
    }

    private fun checkStatus(monitorScenario: Scenario, baseURL: String, monitorLink: Link): HttpResponse {
        val request = monitorScenario.generateHttpRequest().updatePath(monitorLink.toPath())
        return HttpClient(baseURL).execute(request)
    }

    private fun extractLinksFromHeader(headerValue: String): List<Link> {
        return headerValue.split(",").map { it.trim() }
            .map { link ->
                val parts = link.split(";").map { it.trim() }
                val url = parts[0].removePrefix("<").removeSuffix(">")
                val rel = parts[1].removePrefix("rel=").removeSurrounding("\"")
                val title = parts.getOrNull(2)?.removePrefix("title=")?.removeSurrounding("\"")
                Link(url, rel, title)
            }
    }

    private fun getBackOffDelay(retryCount: Int): Long {
        return backOffDelay * 2.0.pow(retryCount).toLong()
    }

    private fun HttpResponse.convertToOriginalResponse(): HttpResponse {
        if (this.body !is JSONObjectValue) throw ContractException("Monitor response body is not an object")
        return this.body.toResponse()
    }

    private fun HttpResponse.convertToOriginalRequest(): HttpRequest {
        if (this.body !is JSONObjectValue) throw ContractException("Monitor response body is not an object")
        return this.body.toRequest()
    }

    private fun HttpResponse.checkCompletion(): ReturnValue<Pair<HttpRequest, HttpResponse>> {
        val (request, response) = runCatching {
            this.convertToOriginalRequest() to this.convertToOriginalResponse()
        }.getOrElse { return HasException(it) }
        return HasValue(request to response)
    }

    private fun HttpResponse.extractMonitorLinkFromHeader(key: String): Link? {
        val headerValue = this.headers[key] ?: return null
        return extractLinksFromHeader(headerValue).firstOrNull {
            it.title == "monitor"
        }
    }

    private fun HttpResponse.getProcessingScenario(): Scenario? {
        if (originalScenario.status == 202 && this.status == 202) return null
        return feature.scenarioAssociatedTo(
            path = originalScenario.path, method = originalScenario.method,
            responseStatusCode = 202, contentType = originalScenario.requestContentType
        )
    }

    private fun JSONObjectValue.toRequest(): HttpRequest {
        val requestObject = this.jsonObject[requestPath] as? JSONObjectValue
            ?: throw ContractException(breadCrumb = requestPath, errorMessage = "Expected a json object")
        return HttpRequest(
            path = originalScenario.path,
            method = requestObject.jsonObject["method"]?.toStringLiteral(),
            headers = requestObject.getMapOrEmpty("header"),
            body = requestObject.jsonObject["body"] ?: NullValue
        )
    }

    private fun JSONObjectValue.toResponse(): HttpResponse {
        val responseObject = this.jsonObject[responsePath] as? JSONObjectValue
            ?: throw ContractException(breadCrumb = responsePath, errorMessage = "Expected a json object")
        return HttpResponse(
            status = responseObject.jsonObject["statusCode"]?.toStringLiteral()?.toInt() ?: DEFAULT_RESPONSE_CODE,
            headers = responseObject.getMapOrEmpty("header"),
            body = responseObject.jsonObject["body"] ?: NullValue
        )
    }

    private fun JSONObjectValue.getMapOrEmpty(path: String): Map<String, String> {
        val value = this.jsonObject[path] as? JSONArrayValue ?: return emptyMap()
        return value.list.mapNotNull {
            val header = it as? JSONObjectValue ?: return@mapNotNull null
            val headerName = header.jsonObject["name"] as? StringValue ?: return@mapNotNull null
            val headerValue = header.jsonObject["value"] as? StringValue ?: return@mapNotNull null
            headerName.string to headerValue.string
        }.toMap()
    }

    private fun Link.monitorScenario(): Scenario? {
        return feature.scenarios.firstOrNull {
            if(it.httpRequestPattern.httpPathPattern == null) return@firstOrNull false
            it.httpRequestPattern.matchesPath(this.toPath(), it.resolver) is Result.Success
        }
    }

    data class Link(val url: String, val rel: String, val title: String? = null) {
        fun toPath(): String {
            return url
        }
    }
}