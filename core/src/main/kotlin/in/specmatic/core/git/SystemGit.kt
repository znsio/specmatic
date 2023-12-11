package `in`.specmatic.core.git

import `in`.specmatic.core.Configuration
import `in`.specmatic.core.azure.AuthCredentials
import `in`.specmatic.core.azure.NoGitAuthCredentials
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.ExternalCommand
import `in`.specmatic.core.utilities.exceptionCauseMessage
import java.io.File

class SystemGit(override val workingDirectory: String = ".", private val prefix: String = "- ", val authCredentials: AuthCredentials = NoGitAuthCredentials) : GitCommand {
    fun executeWithAuth(vararg command: String): String {
        val gitExecutable = listOf(Configuration.gitCommand)
        val auth = authCredentials.gitCommandAuthHeaders()

        return execute(gitExecutable + auth + command.toList())
    }

    private fun execute(command: List<String>): String =
        executeCommandWithWorkingDirectory(prefix, workingDirectory, command.toList().toTypedArray())

    private fun execute(vararg command: String): String =
        executeCommandWithWorkingDirectory(prefix, workingDirectory, command.toList().toTypedArray())

    private fun executeCommandWithWorkingDirectory(
        prefix: String,
        workingDirectory: String,
        command: Array<String>
    ): String {
        logger.debug("${prefix}Executing: ${command.joinToString(" ")}")
        return ExternalCommand(
            command,
            workingDirectory,
            mapOf("GIT_SSL_NO_VERIFY" to "true")
        ).executeAsSeparateProcess()
    }

    fun init(): SystemGit = this.also { execute(Configuration.gitCommand, "init") }
    override fun add(): SystemGit = this.also { execute(Configuration.gitCommand, "add", ".") }
    override fun add(relativePath: String): SystemGit = this.also { execute(Configuration.gitCommand, "add", relativePath) }
    override fun commit(): SystemGit = this.also { execute(Configuration.gitCommand, "commit", "-m", "Updated contract") }
    override fun push(): SystemGit = this.also { executeWithAuth(Configuration.gitCommand, "push") }
    override fun pull(): SystemGit = this.also { executeWithAuth(Configuration.gitCommand, "pull") }
    override fun resetHard(): SystemGit = this.also { execute(Configuration.gitCommand, "reset", "--hard", "HEAD") }
    override fun resetMixed(): SystemGit = this.also { execute(Configuration.gitCommand, "reset", "--mixed", "HEAD") }
    override fun mergeAbort(): SystemGit = this.also { execute(Configuration.gitCommand, "merge", "--aborg") }
    override fun checkout(branchName: String): SystemGit = this.also { execute(Configuration.gitCommand, "checkout", branchName) }
    override fun merge(branchName: String): SystemGit = this.also { execute(Configuration.gitCommand, "merge", branchName) }
    override fun clone(gitRepositoryURI: String, cloneDirectory: File): SystemGit =
        this.also { executeWithAuth("clone", gitRepositoryURI, cloneDirectory.absolutePath) }
    override fun exists(treeish: String, relativePath: String): Boolean {
        return try {
            show(treeish, relativePath)
            true
        } catch(e: NonZeroExitError) {
            false
        }
    }

    override fun getCurrentBranch(): String {
        return execute(Configuration.gitCommand, "git", "diff", "--name-only", "master")
    }

    override fun statusPorcelain(): String {
        return execute(Configuration.gitCommand, "status", "--porcelain")
    }

    override fun fetch(): String {
        return executeWithAuth("fetch")
    }

    override fun revisionsBehindCount(): Int {
        return execute(Configuration.gitCommand, "rev-list", "--count", "HEAD..@{u}").trim().toInt()
    }

    override fun checkIgnore(path: String): String {
        try {
            return execute(Configuration.gitCommand, "check-ignore", path)
        }
        catch (nonZeroExitError:NonZeroExitError) {
            return ""
        }
    }

    override fun shallowClone(gitRepositoryURI: String, cloneDirectory: File): SystemGit =
        this.also {
            executeWithAuth("clone", "--depth", "1", gitRepositoryURI, cloneDirectory.absolutePath)
        }

    override fun gitRoot(): String = execute(Configuration.gitCommand, "rev-parse", "--show-toplevel").trim()
    override fun show(treeish: String, relativePath: String): String =
        execute(Configuration.gitCommand, "show", "${treeish}:${relativePath}")

    override fun workingDirectoryIsGitRepo(): Boolean = try {
        execute(Configuration.gitCommand, "rev-parse", "--is-inside-work-tree").trim() == "true"
    } catch (e: Throwable) {
        false.also {
            logger.debug(
                "This must not be a git dir, got error ${e.javaClass.name}: ${
                    exceptionCauseMessage(
                        e
                    )
                }"
            )
        }
    }

    override fun getChangedFiles(): List<String> {
        val result = execute(Configuration.gitCommand, "status", "--porcelain=1").trim()

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
        val parentDir = File(newerContractPath).absoluteFile.parentFile.absolutePath
        return SystemGit(workingDirectory = parentDir).workingDirectoryIsGitRepo()
    }

    override fun inGitRootOf(contractPath: String): GitCommand = SystemGit(File(contractPath).parentFile.absolutePath)
    fun getChangesFromMainBranch(mainBranch: String): List<String> {
        return execute(Configuration.gitCommand, "diff", "--name-only", mainBranch).split(System.lineSeparator())
    }

    override fun getRemoteUrl(name: String): String = execute(Configuration.gitCommand, "remote", "get-url", name)
}

fun exitErrorMessageContains(exception: NonZeroExitError, snippets: List<String>): Boolean {
    return when (val message = exception.localizedMessage ?: exception.message) {
        null -> false
        else -> snippets.all { snippet -> snippet in message }
    }
}

class NonZeroExitError(error: String, val exitCode:Int) : Throwable(error)
