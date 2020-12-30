package run.qontract.core.utilities

import run.qontract.core.git.NonZeroExitError
import run.qontract.core.git.SystemGit
import run.qontract.core.git.clone
import run.qontract.core.git.exitErrorMessageContains
import java.io.File

sealed class ContractSource {
    abstract val testContracts: List<String>
    abstract val stubContracts: List<String>
    abstract fun pathDescriptor(path: String): String
    abstract fun install(workingDirectory: File)
    abstract fun directoryRelativeTo(workingDirectory: File): File
    abstract fun getLatest(sourceGit: SystemGit)
    abstract fun pushUpdates(sourceGit: SystemGit)
    abstract fun loadContracts(reposBaseDir: File, selector: ContractsSelectorPredicate): List<ContractPathData>
}

data class GitRepo(val gitRepositoryURL: String, override val testContracts: List<String>, override val stubContracts: List<String>) : ContractSource() {
    val repoName = gitRepositoryURL.split("/").last().removeSuffix(".git")

    override fun pathDescriptor(path: String): String {
        return "${repoName}:${path}"
    }

    override fun directoryRelativeTo(workingDirectory: File) =
            workingDirectory.resolve(repoName)

    override fun getLatest(sourceGit: SystemGit) {
        sourceGit.pull()
    }

    override fun pushUpdates(sourceGit: SystemGit) {
        commitAndPush(sourceGit)
    }

    override fun loadContracts(reposBaseDir: File, selector: ContractsSelectorPredicate): List<ContractPathData> {
        println("Looking for contracts in local environment")
        val userHome = File(System.getProperty("user.home"))
        val defaultQontractWorkingDir = userHome.resolve(".qontract/repos")
        val defaultRepoDir = directoryRelativeTo(defaultQontractWorkingDir)

        val repoDir = when {
            (defaultRepoDir.exists() && SystemGit(defaultRepoDir.path).workingDirectoryIsGitRepo()) -> {
                println("Using local contracts")
                defaultRepoDir
            }
            else -> {
                println("Couldn't find local contracts, cloning $gitRepositoryURL into ${reposBaseDir.path}")
                clone(reposBaseDir, this)
            }
        }

        return selector.select(this).map {
            ContractPathData(repoDir.path, repoDir.resolve(it).path)
        }
    }

    override fun install(workingDirectory: File) {
        val sourceDir = workingDirectory.resolve(repoName)
        val sourceGit = SystemGit(sourceDir.path)

        try {
            println("Checking ${sourceDir.path}")
            if (!sourceDir.exists())
                sourceDir.mkdirs()

            if (!sourceGit.workingDirectoryIsGitRepo()) {
                println("Found it, not a git dir, recreating...")
                sourceDir.deleteRecursively()
                sourceDir.mkdirs()
                println("Cloning ${this.gitRepositoryURL} into ${sourceDir.absolutePath}")
                sourceGit.clone(this.gitRepositoryURL, sourceDir.absoluteFile)
            } else {
                println("Git repo already exists at ${sourceDir.path}, so ignoring it and moving on")
            }
        } catch (e: Throwable) {
            println("Could not clone ${this.gitRepositoryURL}\n${e.javaClass.name}: ${exceptionCauseMessage(e)}")
        }
    }
}

data class GitMonoRepo(override val testContracts: List<String>, override val stubContracts: List<String>) : ContractSource() {
    override fun pathDescriptor(path: String): String = path
    override fun install(workingDirectory: File) {
        println("Checking list of mono repo paths...")

        val contracts = testContracts + stubContracts

        for (path in contracts) {
            val existenceMessage = when {
                File(path).exists() -> "$path exists"
                else -> "$path NOT FOUND!"
            }

            println(existenceMessage)
        }
    }

    override fun directoryRelativeTo(workingDirectory: File) = workingDirectory.resolve(gitRootDir())

    override fun getLatest(sourceGit: SystemGit) {
        // In mono repos, we can't pull latest arbitrarily
    }

    override fun pushUpdates(sourceGit: SystemGit) {
        // In mono repos, we can't push arbitrarily
    }

    override fun loadContracts(reposBaseDir: File, selector: ContractsSelectorPredicate): List<ContractPathData> {
        val baseDir = reposBaseDir.resolve(gitRootDir())
        if (!baseDir.exists())
            baseDir.mkdirs()

        return stubContracts.map {
            val relPath = SystemGit().relativeGitPath(it).second
            File(it).parentFile.copyRecursively(baseDir.resolve(relPath).parentFile, true)
            ContractPathData(baseDir.path, baseDir.resolve(relPath).path)
        }
    }
}

fun commitAndPush(sourceGit: SystemGit) {
    val pushRequired = try {
        sourceGit.commit()
        true
    } catch (e: NonZeroExitError) {
        if (!exitErrorMessageContains(e, listOf("nothing to commit")))
            throw e

        exitErrorMessageContains(e, listOf("branch is ahead of"))
    }

    when {
        pushRequired -> {
            println("Pushing changes")
            sourceGit.push()
        }
        else -> println("No changes were made to the repo, so nothing was pushed.")
    }
}
