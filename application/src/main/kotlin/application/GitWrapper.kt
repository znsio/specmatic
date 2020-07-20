package application

import java.io.File

class GitWrapper(private val workingDirectory: String, private val prefix: String = "- ") {
    fun add(): GitWrapper = this.also { executeCommand("git", "add", ".") }
    fun commit(): GitWrapper = this.also { executeCommand("git", "commit", "-m", "Updated contract") }
    fun push(): GitWrapper = this.also { executeCommand("git", "push") }
    fun pull(): GitWrapper = this.also { executeCommand("git", "pull") }
    fun resetHard(): GitWrapper = this.also { executeCommand("git",  "reset", "--hard", "HEAD") }
    fun checkout(branchName: String): GitWrapper = this.also { executeCommand("git", "checkout", branchName) }
    fun merge(branchName: String): GitWrapper = this.also { executeCommand("git", "merge", branchName) }

    private fun executeCommand(vararg command: String) = executeCommandWithWorkingDirectory(prefix, workingDirectory, command.toList().toTypedArray())
}

private fun executeCommandWithWorkingDirectory(prefix: String, workingDirectory: String, command: Array<String>) {
    println("${prefix}Executing: ${command.joinToString(" ")}")
    val process = Runtime.getRuntime().exec(command, null, File(workingDirectory))
    val out = process.inputStream.bufferedReader().readText()
    val err = process.errorStream.bufferedReader().readText()
    process.waitFor()

    if(process.exitValue() != 0) throw UpdateError(err.ifEmpty { out })
}

fun exceptionMessageContains(exception: UpdateError, snippets: List<String>): Boolean {
    return when(val message = exception.localizedMessage ?: exception.message) {
        null -> false
        else -> snippets.all { snippet -> snippet in message }
    }
}
