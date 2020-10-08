package contracttests

import application.Outcome
import application.QontractApplication
import application.QontractCommand
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.*
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [QontractApplication::class, QontractCommand::class])
class StubCommandSmokeTest {
    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var command: QontractCommand

    @Test
    fun `simple http stub test`(@TempDir tempDir: File) {
        val contract = """
            Feature: Random API
              Scenario: Random number
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()

        val contractFile = tempDir.resolve("random.qontract")
        contractFile.writeText(contract)

        val job = GlobalScope.launch {
            try {
                CommandLine(command, factory).execute("stub", contractFile.absolutePath)
            } finally {
                println("GOT HERE")
            }
        }

        val client = HttpClient("http://localhost:9000")

        val request = HttpRequest(method = "GET", path = "/")

        val outcome = retryRequest(client, request)

        job.cancel("temp")

        outcome.onFailure {
            fail(it)
        }

        outcome.handleSuccess { response ->
            println(response.toLogString())
            assertThat(response.status).isEqualTo(200)
            assertThatCode {
                response.body.toStringValue().toInt()
            }.doesNotThrowAnyException()
        }
    }

    private fun retryRequest(client: HttpClient, request: HttpRequest): Outcome<HttpResponse> {
        val sleepInterval = 100
        val seconds = 1000
        val maxAttempts = 10 * seconds / sleepInterval

        val results = 0.until(maxAttempts).asSequence().map {
            try {
                println("Attempt $it")
                Outcome(client.execute(request))
            } catch(e: Throwable) {
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
