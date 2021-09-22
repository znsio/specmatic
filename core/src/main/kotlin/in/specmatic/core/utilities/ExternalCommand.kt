package `in`.specmatic.core.utilities

import `in`.specmatic.core.pattern.ContractException
import java.io.File

class ExternalCommand(
    private val command: Array<String>,
    private val workingDirect: String,
    private val environmentParameters: Array<String>
) {
    fun executeAsSeparateProcess(): String {
        val commandWithParameters = command.joinToString(" ")
        return try {
            val process =
                Runtime.getRuntime().exec(command, environmentParameters, File(workingDirect))
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() != 0) throw ContractException("""Error executing $commandWithParameters: ${err.ifEmpty { out }}""")

            out
        } catch (contractException: ContractException) {
            throw contractException
        } catch (otherExceptions: Exception) {
            throw ContractException("""Error running $commandWithParameters: ${otherExceptions.message}""")
        }
    }
}