package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.Contract
import `in`.specmatic.core.log.*
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.stub.HttpStub
import org.junit.jupiter.api.AfterAll
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

internal class SamplesCommandKtTest {
    @TempDir
    lateinit var tempDir: File

    private val simpleGherkin = """Feature: Math API
  Scenario: Square
    When POST /square
    And request-body (number)
    Then status 200
    And response-body (number)"""

    private lateinit var specFile: File

    companion object {
        @JvmStatic
        @AfterAll
        fun tearDown() {
            resetLogger()
        }
    }

    @Test
    fun `samples function should generate sample given the input`() {
        logger = NonVerbose(CompositePrinter(listOf(JSONConsoleLogPrinter)))

        val (data, _) = captureStandardOutput {
            val gherkin = specFile.readText().trim()
            HttpStub(gherkin, emptyList(), "localhost", 9000).use { fake ->
                Contract.fromGherkin(gherkin).samples(fake)
            }
        }

        println(data)
        validateOutput(data.trimIndent())
    }

    @Test
    fun `command should generate sample given the input`() {
        logger = NonVerbose(CompositePrinter(listOf(JSONConsoleLogPrinter)))

        val (data, _) = captureStandardOutput {
            val command = SamplesCommand()
            command.contractFile = specFile

            val gherkin = specFile.readText().trim()
            HttpStub(gherkin, emptyList(), "localhost", 9000).use { fake ->
                Contract.fromGherkin(gherkin).samples(fake)
            }
        }

        validateOutput(data)
    }

    private fun validateOutput(data: String) {
        val json = parsedJSON(data.removeSuffix(",")) as JSONObjectValue

        assertThat(json.findFirstChildByPath("http-request.path")?.toStringLiteral()).isEqualTo("/square")
        assertThat(json.findFirstChildByPath("http-request.method")?.toStringLiteral()).isEqualTo("POST")
        assertThat(json.findFirstChildByPath("http-request.body")).isInstanceOf(NumberValue::class.java)
        assertThat(json.findFirstChildByPath("requestTime")).isInstanceOf(StringValue::class.java)

        assertThat(json.findFirstChildByPath("http-response.status")?.toStringLiteral()).isEqualTo("200")
        assertThat(json.findFirstChildByPath("http-request.body")).isInstanceOf(NumberValue::class.java)
        assertThat(json.findFirstChildByPath("responseTime")).isInstanceOf(StringValue::class.java)
    }

    @BeforeEach
    fun setup() {
        specFile = File(tempDir, "math.$CONTRACT_EXTENSION").also {
            it.writeText(simpleGherkin)
        }
    }
}

fun <ReturnType> captureStandardOutput(fn: () -> ReturnType): Pair<String, ReturnType> {
    val originalOut = System.out

    val byteArrayOutputStream = ByteArrayOutputStream()
    val newOut = PrintStream(byteArrayOutputStream)
    System.setOut(newOut)

    val result = fn()

    System.out.flush()
    System.setOut(originalOut) // So you can print again
    return Pair(String(byteArrayOutputStream.toByteArray()).trim(), result)
}
