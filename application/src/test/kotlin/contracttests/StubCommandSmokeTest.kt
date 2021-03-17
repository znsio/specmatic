package contracttests

import application.Outcome
import application.SpecmaticApplication
import application.SpecmaticCommand
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import picocli.CommandLine
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.test.HttpClient
import java.io.File
import kotlin.concurrent.thread

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [SpecmaticApplication::class, SpecmaticCommand::class])
class StubCommandSmokeTest {
    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var picoCommand: SpecmaticCommand

    val contract = """
            Feature: Random API
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

    @Test @Disabled
    fun `simple http stub`(@TempDir tempDir: File) {
        val contractFile = tempDir.resolve("random.$CONTRACT_EXTENSION")
        contractFile.writeText(contract)

        val request = HttpRequest(method = "GET", path = "/")
        val command = listOf("stub", contractFile.absolutePath)

        val outcome = requestToStub(command, request)

        outcome.onFailure {
            fail(it)
        }

        outcome.handleSuccess { response ->
            assertThat(response.status).isEqualTo(200)
            assertThatCode {
                response.body.toStringValue().toInt()
            }.doesNotThrowAnyException()
        }
    }

    private fun requestToStub(command: List<String>, request: HttpRequest): Outcome<HttpResponse> {
        val client = HttpClient("http://localhost:9000")

        val commandThread = thread {
            CommandLine(picoCommand, factory).execute(*command.toTypedArray())
        }

        val outcome = retryRequest(client, request)

        commandThread.interrupt()

        return outcome
    }

    private fun retryRequest(client: HttpClient, request: HttpRequest): Outcome<HttpResponse> {
        val sleepInterval = 100
        val seconds = 1000
        val maxAttempts = 10 * seconds / sleepInterval

        val results = 0.until(maxAttempts).asSequence().map {
            try {
                println("Attempt $it")
                Outcome(client.execute(request))
            } catch (e: Throwable) {
                Thread.sleep(sleepInterval.toLong())
                exceptionCauseMessage(e).let { errorMessage ->
                    println(errorMessage)
                    Outcome<HttpResponse>(null, errorMessage)
                }
            }
        }

        return results.find { it.result != null } ?: results.first()
    }
}
