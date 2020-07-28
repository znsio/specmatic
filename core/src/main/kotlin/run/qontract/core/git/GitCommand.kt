package run.qontract.core.git

import java.io.File

class GitCommand(private val workingDirectory: String = ".", private val prefix: String = "- ") {
    private fun execute(vararg command: String): String = executeCommandWithWorkingDirectory(prefix, workingDirectory, command.toList().toTypedArray())

    fun add(): GitCommand = this.also { execute("git", "add", ".") }
    fun add(relativePath: String): GitCommand = this.also { execute("git", "add", "relativePath") }
    fun commit(): GitCommand = this.also { execute("git", "commit", "-m", "Updated contract") }
    fun push(): GitCommand = this.also { execute("git", "push") }
    fun pull(): GitCommand = this.also { execute("git", "pull") }
    fun resetHard(): GitCommand = this.also { execute("git",  "reset", "--hard", "HEAD") }
    fun resetMixed(): GitCommand = this.also { execute("git",  "reset", "--mixed", "HEAD") }
    fun mergeAbort(): GitCommand = this.also { execute("git",  "merge", "--aborg") }
    fun checkout(branchName: String): GitCommand = this.also { execute("git", "checkout", branchName) }
    fun merge(branchName: String): GitCommand = this.also { execute("git", "merge", branchName) }
    fun clone(gitRepositoryURI: String, cloneDirectory: File): GitCommand = this.also { execute("git", "clone", gitRepositoryURI, cloneDirectory.absolutePath) }
    fun gitRoot(): String = execute("git", "rev-parse", "--show-toplevel")
    fun show(treeish: String, relativePath: String): String = execute("git", "show", "${treeish}:${relativePath}")
    fun isGitRepository(): Boolean = execute("git", "rev-parse", "--is-inside-work-tree") == "true"
}

private fun executeCommandWithWorkingDirectory(prefix: String, workingDirectory: String, command: Array<String>): String {
    println("${prefix}Executing: ${command.joinToString(" ")}")
    val process = Runtime.getRuntime().exec(command, null, File(workingDirectory))
    val out = process.inputStream.bufferedReader().readText()
    val err = process.errorStream.bufferedReader().readText()
    process.waitFor()

    if(process.exitValue() != 0) throw NonZeroExitError(process.exitValue(), err.ifEmpty { out })

    return out
}

fun exitErrorMessageContains(exception: NonZeroExitError, snippets: List<String>): Boolean {
    return when(val message = exception.localizedMessage ?: exception.message) {
        null -> false
        else -> snippets.all { snippet -> snippet in message }
    }
}

class NonZeroExitError(exitValue: Int, error: String) : Throwable(error)
