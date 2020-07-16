package application

import java.io.File

class GitWrapper(private val workingDirectory: String) {
    fun add(): GitWrapper = this.also { executeCommand("git", "add", ".") }
    fun commit(): GitWrapper = this.also { executeCommand("git", "commit", "-m", "Updated contract") }
    fun push(): GitWrapper = this.also { executeCommand("git", "push") }
    fun pull(): GitWrapper = this.also { executeCommand("git", "pull") }
    fun resetHard(): GitWrapper = this.also { executeCommand("git",  "reset", "--hard", "HEAD") }
    fun checkout(branchName: String): GitWrapper = this.also { executeCommand("git", "checkout", branchName) }

    private fun executeCommand(vararg command: String) = executeCommandWithWorkingDirectory(workingDirectory, command.toList().toTypedArray())
}

private fun executeCommandWithWorkingDirectory(workingDirectory: String, command: Array<String>) {
    println("Executing: ${command.joinToString(" ")}")
    val process = Runtime.getRuntime().exec(command, null, File(workingDirectory))
    val out = process.inputStream.bufferedReader().readText()
    val err = process.errorStream.bufferedReader().readText()

    if(process.exitValue() != 0) throw UpdateError(err.ifEmpty { out })
}

fun exceptionMessageContains(exception: UpdateError, snippets: List<String>): Boolean {
    return when(val message = exception.localizedMessage ?: exception.message) {
        null -> false
        else -> snippets.all { snippet -> snippet in message }
    }
}
