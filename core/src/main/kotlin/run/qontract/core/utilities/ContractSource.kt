package run.qontract.core.utilities

import run.qontract.core.git.GitCommand
import java.io.File

typealias SelectorFunction = (repoDir: File, destinationDir: File) -> Unit

sealed class ContractSource {
    abstract val paths: List<String>
    abstract fun pathDescriptor(path: String): String
    abstract fun ensureExists(workingDirectory: File)
}

data class GitRepo(val gitRepositoryURL: String, override val paths: List<String>) : ContractSource() {
    private val repoName = gitRepositoryURL.split("/").last().removeSuffix(".git")

    override fun pathDescriptor(path: String): String {
        return "${repoName}:${path}"
    }

    override fun ensureExists(workingDirectory: File) {
        val sourceDir = workingDirectory.resolve(this.gitRepositoryURL.split("/").last())
        val sourceGit = GitCommand(sourceDir.path)

        try {
            if (!sourceGit.isGitRepository())
                sourceGit.clone(this.gitRepositoryURL, sourceDir.absoluteFile)
        } catch (e: Throwable) {
            println("Could not clone ${this.gitRepositoryURL}\n${e.javaClass.name}: ${exceptionCauseMessage(e)}")
        }
    }
}

data class GitMonoRepo(override val paths: List<String>) : ContractSource() {
    override fun pathDescriptor(path: String): String = path
    override fun ensureExists(workingDirectory: File) {
        println("Checking list of mono repo paths...")
        for(path in this.paths) {
            val existenceMessage = when {
                File(path).exists() -> "$path exists"
                else -> "$path NOT FOUND!"
            }

            println(existenceMessage)
        }
    }
}
