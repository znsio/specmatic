package `in`.specmatic.core.utilities

import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.pattern.ContractException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.util.function.Consumer

internal class ExternalCommandTest {
    @Test
    fun `should throw contract exception when external command does not exist`() {
        val exception = assertThrows(ContractException::class.java) {
            ExternalCommand(arrayOf("missing_command"), ".").executeAsSeparateProcess()
        }
        assertThat(exception.report()).isEqualTo(
            """Error running missing_command: Cannot run program "missing_command" (in directory "."): error=2, No such file or directory"""
        )
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `should throw contract exception when external command returns non zero exit code`() {
        val exception = assertThrows(NonZeroExitError::class.java) {
            ExternalCommand(arrayOf("cat", "missing_file"), ".").executeAsSeparateProcess()
        }
        assertThat(exception.message?.trim() ?: "").satisfies(
            Consumer {
                assertThat(it).startsWith("Error executing cat missing_file: ")
            },
            Consumer {
                assertThat(it).endsWith("missing_file: No such file or directory")
            }
        )
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `should return command output`() {
        val commandOutput =
            ExternalCommand(arrayOf("echo", "hello"), ".").executeAsSeparateProcess()
        assertThat(commandOutput).isEqualTo("hello\n")
    }
    
    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `should inherit parent process env`() {
        val commandOutput =
            ExternalCommand("env", ".", mapOf("myVar1" to "myVal")).executeAsSeparateProcess()
        assertThat(commandOutput).contains("myVar1=myVal")

        val parentProcessEnv = System.getenv()
        parentProcessEnv.forEach { (key, value) ->
            if(valueMayChangeInChildProcess(key)) {
                assertThat(commandOutput).contains("$key=")
            } else {
                assertThat(commandOutput).contains("$key=$value")
            }
        }
    }

    private fun valueMayChangeInChildProcess(key: String): Boolean {
        return key.startsWith("CODEQL_")
    }
}