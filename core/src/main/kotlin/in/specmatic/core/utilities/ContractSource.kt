package `in`.specmatic.core.utilities

import `in`.specmatic.core.APPLICATION_NAME_LOWER_CASE
import `in`.specmatic.core.Configuration
import `in`.specmatic.core.git.*
import `in`.specmatic.core.log.logger
import java.io.File

sealed class ContractSource {
    abstract val testContracts: List<String>
    abstract val stubContracts: List<String>
    abstract fun pathDescriptor(path: String): String
    abstract fun install(workingDirectory: File)
    abstract fun directoryRelativeTo(workingDirectory: File): File
    abstract fun getLatest(sourceGit: SystemGit)
    abstract fun pushUpdates(sourceGit: SystemGit)
    abstract fun loadContracts(selector: ContractsSelectorPredicate, workingDirectory: String, configFilePath: String): List<ContractPathData>
}

data class GitRepo(
    val gitRepositoryURL: String,
    val branchName: String?,
    override val testContracts: List<String>,
    override val stubContracts: List<String>
) : ContractSource() {
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

    override fun loadContracts(
        selector: ContractsSelectorPredicate,
        workingDirectory: String,
        configFilePath: String
    ): List<ContractPathData> {
        val userHome = File(System.getProperty("user.home"))
        val defaultQontractWorkingDir = userHome.resolve(".$APPLICATION_NAME_LOWER_CASE/repos")
        val defaultRepoDir = directoryRelativeTo(defaultQontractWorkingDir)

        val bundleDir = File(Configuration.TEST_BUNDLE_RELATIVE_PATH).resolve(repoName)

        val repoDir = when {
            bundleDir.exists() -> {
                logger.log("Using contracts from ${bundleDir.path}")
                bundleDir
            }

            defaultRepoDir.exists() -> {
                logger.log("Using contracts in home dir")
                defaultRepoDir
            }

            else -> {
                val reposBaseDir = localRepoDir(workingDirectory)
                val contractsRepoDir =  this.directoryRelativeTo(reposBaseDir)
                when {
                    !contractsRepoDir.exists() -> cloneRepo(reposBaseDir, this)
                    contractsRepoDir.exists() && isBehind(contractsRepoDir) -> cloneRepo(reposBaseDir, this)
                    contractsRepoDir.exists() && isClean(contractsRepoDir) -> {
                        logger.log("Couldn't find local contracts but ${contractsRepoDir.path} already exists and is clean and has contracts")
                        contractsRepoDir
                    }
                    else -> {
                        logger.log("Couldn't find local contracts. Although ${contractsRepoDir.path} exists, it is not clean.\nHence cloning $gitRepositoryURL into ${reposBaseDir.path}")
                        clone(reposBaseDir, this)
                    }
                }
            }
        }

        return selector.select(this).map {
            ContractPathData(repoDir.path, repoDir.resolve(it).path)
        }
    }

    private fun isClean(contractsRepoDir: File): Boolean {
        val sourceGit = getSystemGit(contractsRepoDir.path)
        return sourceGit.statusPorcelain().isEmpty()
    }

    private fun isBehind(contractsRepoDir: File): Boolean {
        val sourceGit = getSystemGit(contractsRepoDir.path)
        sourceGit.fetch()
        return sourceGit.revisionsBehindCount() > 0
    }
    private fun cloneRepo(reposBaseDir: File, gitRepo: GitRepo): File {
        logger.log("Couldn't find local contracts, cloning $gitRepositoryURL into ${reposBaseDir.path}")
        reposBaseDir.mkdirs()
        val repositoryDirectory = clone(reposBaseDir, gitRepo)

        when (branchName) {
            null -> logger.log("No branch specified, using default branch")
            else -> checkout(repositoryDirectory, branchName)
        }
        return repositoryDirectory
    }

    private fun localRepoDir(workingDirectory: String): File = File(workingDirectory).resolve("repos")

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

    override fun loadContracts(selector: ContractsSelectorPredicate, workingDirectory: String, configFilePath: String): List<ContractPathData> {
        val monoRepoBaseDir = File(SystemGit().gitRoot())
        val configFileLocation = File(configFilePath).absoluteFile.parentFile

        return selector.select(this).map {
            ContractPathData(monoRepoBaseDir.canonicalPath, configFileLocation.resolve(it).canonicalPath)
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
