package `in`.specmatic.stub

import `in`.specmatic.core.*
import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.value.KafkaMessage
import java.io.File

interface StubData

data class HttpStubData(
    val requestType: HttpRequestPattern,
    val response: HttpResponse,
    val resolver: Resolver,
    val delayInSeconds: Int? = null
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
                """'${httpRequest.toJSON().toString().replace("\n", "", true)}'"""
            )
        )
        val responseMap = jsonStringToValueMap(result)
        return this.copy(response = HttpResponse.fromJSON(responseMap))
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
