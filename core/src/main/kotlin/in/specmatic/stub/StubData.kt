package `in`.specmatic.stub

import `in`.specmatic.core.*
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.ExternalCommand
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.value.KafkaMessage

interface StubData

data class HttpStubData(
    val requestType: HttpRequestPattern,
    val response: HttpResponse,
    val resolver: Resolver,
    val delayInSeconds: Int? = null,
    val responsePattern: HttpResponsePattern,
    val contractPath: String = ""
) : StubData {
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
            !responseMatches.isTrue() -> {
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

data class KafkaStubData(val kafkaMessage: KafkaMessage) : StubData

data class StubDataItems(val http: List<HttpStubData> = emptyList(), val kafka: List<KafkaStubData> = emptyList())
