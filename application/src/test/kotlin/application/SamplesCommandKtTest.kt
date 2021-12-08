package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.Contract
import `in`.specmatic.core.log.JSONConsoleLogPrinter
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.stub.HttpStub
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

internal class SamplesCommandKtTest {
    @TempDir
    lateinit var tempDir: File

    val simpleGherkin = """Feature: Math API
  Scenario: Square
    When POST /square
    And request-body (number)
    Then status 200
    And response-body (number)"""

    lateinit var qontractFile: File

    @Test
    fun `samples function should generate sample given the input`() {
        logger.printer.printers.clear()
        logger.printer.printers.add(JSONConsoleLogPrinter)

        val (data, _) = captureStandardOutput {
            val gherkin = qontractFile.readText().trim()
            HttpStub(gherkin, emptyList(), "localhost", 9000).use { fake ->
                Contract.fromGherkin(gherkin).samples(fake)
            }
        }

        println(data)
        validateOutput(data.trimIndent())
    }

    @Test
    fun `command should generate sample given the input`() {
        logger.printer.printers.clear()
        logger.printer.printers.add(JSONConsoleLogPrinter)

        val (data, _) = captureStandardOutput {
            val command = SamplesCommand()
            command.contractFile = qontractFile

            val gherkin = qontractFile.readText().trim()
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
        qontractFile = File(tempDir, "math.$CONTRACT_EXTENSION").also {
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
