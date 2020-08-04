package run.qontract.core.git

import run.qontract.core.utilities.exceptionCauseMessage
import java.io.File

class GitCommand(private val workingDirectory: String = ".", private val prefix: String = "- ") {
    private fun execute(vararg command: String): String = executeCommandWithWorkingDirectory(prefix, workingDirectory, command.toList().toTypedArray())

    fun add(): GitCommand = this.also { execute("git", "add", ".") }
    fun add(relativePath: String): GitCommand = this.also { execute("git", "add", relativePath) }
    fun commit(): GitCommand = this.also { execute("git", "commit", "-m", "Updated contract") }
    fun push(): GitCommand = this.also { execute("git", "push") }
    fun pull(): GitCommand = this.also { execute("git", "pull") }
    fun resetHard(): GitCommand = this.also { execute("git",  "reset", "--hard", "HEAD") }
    fun resetMixed(): GitCommand = this.also { execute("git",  "reset", "--mixed", "HEAD") }
    fun mergeAbort(): GitCommand = this.also { execute("git",  "merge", "--aborg") }
    fun checkout(branchName: String): GitCommand = this.also { execute("git", "checkout", branchName) }
    fun merge(branchName: String): GitCommand = this.also { execute("git", "merge", branchName) }
    fun clone(gitRepositoryURI: String, cloneDirectory: File): GitCommand = this.also { execute("git", "clone", gitRepositoryURI, cloneDirectory.absolutePath) }
    fun gitRoot(): String = execute("git", "rev-parse", "--show-toplevel").trim()
    fun show(treeish: String, relativePath: String): String = execute("git", "show", "${treeish}:${relativePath}")
    fun workingDirectoryIsGitRepo(): Boolean = try {
        execute("git", "rev-parse", "--is-inside-work-tree").trim() == "true"
    } catch(e: Throwable) {
        false.also {
            println("This must not be a git dir, got error ${e.javaClass.name}: ${exceptionCauseMessage(e)}")
        }
    }

    fun getChangedFiles(): List<String> {
        val result = execute("git", "status", "--porcelain=1").trim()

        if(result.isEmpty())
            return emptyList()

        return result.lines().map { it.trim().split(" ", limit = 2)[1] }
    }

    fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
        val gitRoot = File(GitCommand(File(newerContractPath).absoluteFile.parent).gitRoot())
        val git = GitCommand(gitRoot.absolutePath)
        val relativeContractPath = File(newerContractPath).absoluteFile.relativeTo(gitRoot.absoluteFile).path
        return Pair(git, relativeContractPath)
    }

    fun fileIsInGitDir(newerContractPath: String): Boolean {
        val parentDir = File(newerContractPath).parentFile.absolutePath
        return GitCommand(workingDirectory = parentDir).workingDirectoryIsGitRepo()
    }
}

private fun executeCommandWithWorkingDirectory(prefix: String, workingDirectory: String, command: Array<String>): String {
    println("${prefix}Executing: ${command.joinToString(" ")}")
    val process = Runtime.getRuntime().exec(command, null, File(workingDirectory))
    process.waitFor()
    val out = process.inputStream.bufferedReader().readText()
    val err = process.errorStream.bufferedReader().readText()

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
