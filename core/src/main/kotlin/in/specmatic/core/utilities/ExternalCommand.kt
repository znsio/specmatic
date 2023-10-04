package `in`.specmatic.core.utilities

import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.pattern.ContractException
import java.io.File

class ExternalCommand(
    private val command: Array<String>,
    private val workingDirect: String,
    private val environmentParameters: Array<String>
) {
    constructor (command: String, workingDirect: String, environmentParameters: List<String>) : this(command.split(" ").toTypedArray(), workingDirect, environmentParameters.toTypedArray())

    fun executeAsSeparateProcess(): String {
        val commandWithParameters = command.joinToString(" ")
        return try {
            val process =
                Runtime.getRuntime().exec(command, environmentParameters, File(workingDirect))
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() != 0) throw NonZeroExitError("""Error executing $commandWithParameters: ${err.ifEmpty { out }}""", process.exitValue())

            out
        } catch (otherExceptions: Exception) {
            throw ContractException("""Error running $commandWithParameters: ${otherExceptions.message}""",
                exceptionCause = otherExceptions)
        }
    }
}