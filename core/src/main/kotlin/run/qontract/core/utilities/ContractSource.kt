package run.qontract.core.utilities

import run.qontract.core.git.GitCommand
import java.io.File

typealias SelectorFunction = (repoDir: File, destinationDir: File) -> Unit

sealed class ContractSource {
    abstract val contracts: List<String>
    abstract fun pathDescriptor(path: String): String
    abstract fun install(workingDirectory: File)
    abstract fun directoryRelativeTo(workingDirectory: File): File
}

data class GitRepo(val gitRepositoryURL: String, override val contracts: List<String>) : ContractSource() {
    val repoName = gitRepositoryURL.split("/").last().removeSuffix(".git")

    override fun pathDescriptor(path: String): String {
        return "${repoName}:${path}"
    }

    override fun directoryRelativeTo(workingDirectory: File) =
            workingDirectory.resolve(this.gitRepositoryURL.split("/").last())

    override fun install(workingDirectory: File) {
        val sourceDir = workingDirectory.resolve(this.gitRepositoryURL.split("/").last())
        val sourceGit = GitCommand(sourceDir.path)

        try {
            println("Checking ${sourceDir.path}")
            if(!sourceDir.exists())
                sourceDir.mkdirs()

            if(!sourceGit.workingDirectoryIsGitRepo()) {
                println("Found it, not a git dir, recreating...")
                sourceDir.deleteRecursively()
                sourceDir.mkdirs()
                println("Cloning ${this.gitRepositoryURL} into ${sourceDir.absolutePath}")
                sourceGit.clone(this.gitRepositoryURL, sourceDir.absoluteFile)
            }
            else {
                println("Git repo already exists at ${sourceDir.path}, so ignoring it and moving on")
            }
        } catch (e: Throwable) {
            println("Could not clone ${this.gitRepositoryURL}\n${e.javaClass.name}: ${exceptionCauseMessage(e)}")
        }
    }
}

data class GitMonoRepo(override val contracts: List<String>) : ContractSource() {
    override fun pathDescriptor(path: String): String = path
    override fun install(workingDirectory: File) {
        println("Checking list of mono repo paths...")
        for(path in this.contracts) {
            val existenceMessage = when {
                File(path).exists() -> "$path exists"
                else -> "$path NOT FOUND!"
            }

            println(existenceMessage)
        }
    }

    override fun directoryRelativeTo(workingDirectory: File): File = File(".")
}
