package `in`.specmatic.stub

import `in`.specmatic.core.*
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.ExternalCommand
import `in`.specmatic.core.utilities.jsonStringToValueMap

data class HttpStubData(
    val requestType: HttpRequestPattern,
    val response: HttpResponse,
    val resolver: Resolver,
    val delayInSeconds: Int? = null,
    val responsePattern: HttpResponsePattern,
    val contractPath: String = "",
    val stubToken: String? = null,
    val requestBodyRegex: Regex? = null,
    val feature:Feature? = null,
    val scenario: Scenario? = null
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
            """SPECMATIC_REQUEST='${httpRequest.toJSON().toUnformattedStringLiteral()}'"""
        )
        val responseMap = jsonStringToValueMap(result)
        val externalCommandResponse = HttpResponse.fromJSON(responseMap)
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

fun executeExternalCommand(command: String, envParam: String): String {
    logger.debug("Executing: $command with EnvParam: $envParam")
    return ExternalCommand(command.split(" ").toTypedArray(), ".", arrayOf(envParam)).executeAsSeparateProcess()
}

data class StubDataItems(val http: List<HttpStubData> = emptyList())
