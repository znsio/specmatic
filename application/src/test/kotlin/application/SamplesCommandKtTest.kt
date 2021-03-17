package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import run.qontract.core.CONTRACT_EXTENSION
import run.qontract.core.SPECMATIC_RESULT_HEADER
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
        val (data, _) = captureStandardOutput {
            samples(qontractFile, "localhost", 9000)
        }

        validateOutput(data.trimIndent())
    }

    @Test
    fun `command should generate sample given the input`() {
        val (data, _) = captureStandardOutput {
            val command = SamplesCommand()
            command.qontractFile = qontractFile

            samples(qontractFile, "localhost", 9000)
        }

        validateOutput(data)
    }

    private fun validateOutput(data: String) {
        assertThat(data).startsWith(""">> Request Start At """)

        assertThat(data).contains("""-> POST /square
-> Accept-Charset: UTF-8
-> Accept: */*
->""".trimIndent())

        assertThat(data).contains("<- 200 OK")
        assertThat(data).contains("<- $SPECMATIC_RESULT_HEADER: success")
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
