package run.qontract.core.git

import run.qontract.core.utilities.exceptionCauseMessage
import java.io.File

class SystemGit(private val workingDirectory: String = ".", private val prefix: String = "- ") : GitCommand {
    private fun execute(vararg command: String): String = executeCommandWithWorkingDirectory(prefix, workingDirectory, command.toList().toTypedArray())

    override fun add(): SystemGit = this.also { execute("git", "add", ".") }
    override fun add(relativePath: String): SystemGit = this.also { execute("git", "add", relativePath) }
    override fun commit(): SystemGit = this.also { execute("git", "commit", "-m", "Updated contract") }
    override fun push(): SystemGit = this.also { execute("git", "push") }
    override fun pull(): SystemGit = this.also { execute("git", "pull") }
    override fun resetHard(): SystemGit = this.also { execute("git", "reset", "--hard", "HEAD") }
    override fun resetMixed(): SystemGit = this.also { execute("git", "reset", "--mixed", "HEAD") }
    override fun mergeAbort(): SystemGit = this.also { execute("git", "merge", "--aborg") }
    override fun checkout(branchName: String): SystemGit = this.also { execute("git", "checkout", branchName) }
    override fun merge(branchName: String): SystemGit = this.also { execute("git", "merge", branchName) }
    override fun clone(gitRepositoryURI: String, cloneDirectory: File): SystemGit = this.also { execute("git", "clone", gitRepositoryURI, cloneDirectory.absolutePath) }
    override fun gitRoot(): String = execute("git", "rev-parse", "--show-toplevel").trim()
    override fun show(treeish: String, relativePath: String): String = execute("git", "show", "${treeish}:${relativePath}")
    override fun workingDirectoryIsGitRepo(): Boolean = try {
        execute("git", "rev-parse", "--is-inside-work-tree").trim() == "true"
    } catch (e: Throwable) {
        false.also {
            println("This must not be a git dir, got error ${e.javaClass.name}: ${exceptionCauseMessage(e)}")
        }
    }

    override fun getChangedFiles(): List<String> {
        val result = execute("git", "status", "--porcelain=1").trim()

        if (result.isEmpty())
            return emptyList()

        return result.lines().map { it.trim().split(" ", limit = 2)[1] }
    }

    override fun relativeGitPath(newerContractPath: String): Pair<SystemGit, String> {
        val gitRoot = File(SystemGit(File(newerContractPath).absoluteFile.parent).gitRoot())
        val git = SystemGit(gitRoot.absolutePath)
        val relativeContractPath = File(newerContractPath).absoluteFile.relativeTo(gitRoot.absoluteFile).path
        return Pair(git, relativeContractPath)
    }

    override fun fileIsInGitDir(newerContractPath: String): Boolean {
        val parentDir = File(newerContractPath).parentFile.absolutePath
        return SystemGit(workingDirectory = parentDir).workingDirectoryIsGitRepo()
    }

    override fun inGitRootOf(contractPath: String): GitCommand = SystemGit(File(contractPath).parentFile.absolutePath)
}

private fun executeCommandWithWorkingDirectory(prefix: String, workingDirectory: String, command: Array<String>): String {
    println("${prefix}Executing: ${command.joinToString(" ")}")
    val process = Runtime.getRuntime().exec(command, null, File(workingDirectory))
    process.waitFor()
    val out = process.inputStream.bufferedReader().readText()
    val err = process.errorStream.bufferedReader().readText()

    if (process.exitValue() != 0) throw NonZeroExitError(err.ifEmpty { out })

    return out
}

fun exitErrorMessageContains(exception: NonZeroExitError, snippets: List<String>): Boolean {
    return when (val message = exception.localizedMessage ?: exception.message) {
        null -> false
        else -> snippets.all { snippet -> snippet in message }
    }
}

class NonZeroExitError(error: String) : Throwable(error)
