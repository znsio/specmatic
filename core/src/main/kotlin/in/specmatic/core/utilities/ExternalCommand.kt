package `in`.specmatic.core.utilities

import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.pattern.ContractException
import java.io.File

class ExternalCommand(
    private val command: Array<String>,
    private val workingDirect: String,
    private val environmentParameters: Map<String, String>
) {
    constructor (
        command: String,
        workingDirect: String,
        environmentParameters: Map<String, String>
    ) : this(command.split(" ").toTypedArray(), workingDirect, environmentParameters)

    constructor (
        command: Array<String>,
        workingDirect: String,
    ) : this(command, workingDirect, emptyMap())

    fun executeAsSeparateProcess(): String {
        val commandWithParameters = command.joinToString(" ")
        return try {
            val procBuilder = ProcessBuilder(command.asList()).directory(File(workingDirect))
            val env = procBuilder.environment();
            env.putAll(environmentParameters)
            val proc = procBuilder.start()
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            val exitCode = proc.waitFor()

            if (exitCode != 0)
                throw NonZeroExitError("""Error executing $commandWithParameters: ${err.ifEmpty { out }}""", exitCode)

            out
        } catch (otherExceptions: Exception) {
            throw ContractException("""Error running $commandWithParameters: ${otherExceptions.message}""",
                exceptionCause = otherExceptions)
        }
    }
}