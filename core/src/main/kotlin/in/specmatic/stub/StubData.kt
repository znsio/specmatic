package `in`.specmatic.stub

import `in`.specmatic.core.*
import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.value.KafkaMessage
import java.io.File

interface StubData

data class HttpStubData(
    val requestType: HttpRequestPattern,
    val response: HttpResponse,
    val resolver: Resolver,
    val delayInSeconds: Int? = null,
    val responsePattern: HttpResponsePattern
) : StubData {
    fun softCastResponseToXML(httpRequest: HttpRequest): HttpStubData = when {
        !response.externalisedResponseCommand.isNullOrEmpty() -> {
            invokeExternalCommand(httpRequest)
        }
        else -> this.copy(response = response.copy(body = softCastValueToXML(response.body)))
    }

    private fun invokeExternalCommand(httpRequest: HttpRequest): HttpStubData {
        val result = executeCommandWithWorkingDirectory(
            arrayOf(
                response.externalisedResponseCommand.toString(),
                //TODO: removing new lines with JSON library
                """'${httpRequest.toJSON().toString().replace("\n", "", true)}'"""
            )
        )
        val responseMap = jsonStringToValueMap(result)
        val externalCommandResponse = HttpResponse.fromJSON(responseMap)
        val externalCommandResponsePattern = HttpResponsePattern(externalCommandResponse)
        val responseMatches = responsePattern.encompasses(externalCommandResponsePattern, resolver, resolver)
        if (!responseMatches.isTrue()) {
            val errorMessage =
                """Response returned by ${response.externalisedResponseCommand} not in line with specification for ${httpRequest.method} ${httpRequest.path}:\n${responseMatches.reportString()}"""
            information.forTheUser(errorMessage)
            throw ContractException(errorMessage)
        }
        return this.copy(response = externalCommandResponse)
    }

    private fun executeCommandWithWorkingDirectory(command: Array<String>): String {
        information.forDebugging("Executing: ${command.joinToString(" ")}")
        val process = Runtime.getRuntime().exec(command, listOf("GIT_SSL_NO_VERIFY=true").toTypedArray(), File("."))
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        process.waitFor()

        if (process.exitValue() != 0) throw NonZeroExitError(err.ifEmpty { out })

        return out
    }
}

data class KafkaStubData(val kafkaMessage: KafkaMessage) : StubData

data class StubDataItems(val http: List<HttpStubData> = emptyList(), val kafka: List<KafkaStubData> = emptyList())
