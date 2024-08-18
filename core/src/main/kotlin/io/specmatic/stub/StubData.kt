package io.specmatic.stub

import io.specmatic.core.*
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.attempt
import io.specmatic.core.utilities.ExternalCommand
import io.specmatic.core.utilities.jsonStringToValueMap
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import io.specmatic.mock.ScenarioStub

data class HttpStubData(
    val requestType: HttpRequestPattern,
    val response: HttpResponse,
    val resolver: Resolver,
    val delayInMilliseconds: Long? = null,
    val responsePattern: HttpResponsePattern,
    val contractPath: String = "",
    val stubToken: String? = null,
    val requestBodyRegex: Regex? = null,
    val feature:Feature? = null,
    val scenario: Scenario? = null,
    val template: ScenarioStub? = null
) {
    val matchFailure: Boolean
        get() = response.headers[SPECMATIC_RESULT_HEADER] == "failure"

    fun softCastResponseToXML(httpRequest: HttpRequest): HttpStubData = when {
        response.externalisedResponseCommand.isNotEmpty() -> invokeExternalCommand(httpRequest).copy(contractPath = contractPath)
        else -> this.copy(response = response.copy(body = softCastValueToXML(response.body)))
    }

    private fun invokeExternalCommand(httpRequest: HttpRequest): HttpStubData {
        val result = executeExternalCommand(
            response.externalisedResponseCommand,
            mapOf("SPECMATIC_REQUEST" to """'${httpRequest.toJSON().toUnformattedStringLiteral()}'"""),
        )

        val externalCommandResponse = attempt {
            val responseMap = jsonStringToValueMap(result)
            HttpResponse.fromJSON(responseMap)
        }

        val responseMatches = responsePattern.matches(externalCommandResponse, resolver.copy(mismatchMessages = ContractExternalResponseMismatch))
        return when {
            !responseMatches.isSuccess() -> {
                val errorMessage =
                    """Response returned by ${response.externalisedResponseCommand} not in line with specification for ${httpRequest.method} ${httpRequest.path}:\n${responseMatches.reportString()}"""
                logger.log(errorMessage)
                throw ContractException(errorMessage)
            }
            else -> {
                this.copy(response = externalCommandResponse)
            }
        }
    }
}

fun executeExternalCommand(command: String, envParams: Map<String, String>): String {
    logger.debug("Executing: $command with EnvParams: $envParams")
    return ExternalCommand(command, ".", envParams).executeAsSeparateProcess()
}

data class StubDataItems(val http: List<HttpStubData> = emptyList())
