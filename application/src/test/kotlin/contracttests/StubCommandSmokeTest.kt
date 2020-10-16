package contracttests

import application.Outcome
import application.QontractApplication
import application.QontractCommand
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import picocli.CommandLine
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.test.HttpClient
import java.io.File
import kotlin.concurrent.thread

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [QontractApplication::class, QontractCommand::class])
class StubCommandSmokeTest {
    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var picoCommand: QontractCommand

    val contract = """
            Feature: Random API
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

    @Test
    fun `simple http stub`(@TempDir tempDir: File) {
        val contractFile = tempDir.resolve("random.qontract")
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
