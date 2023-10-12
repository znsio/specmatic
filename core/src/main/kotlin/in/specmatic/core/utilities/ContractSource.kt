package `in`.specmatic.core.utilities

import `in`.specmatic.core.APPLICATION_NAME_LOWER_CASE
import `in`.specmatic.core.Configuration
import `in`.specmatic.core.DEFAULT_WORKING_DIRECTORY
import `in`.specmatic.core.git.*
import `in`.specmatic.core.log.logger
import java.io.File

sealed class ContractSource {
    abstract val type:String?
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
    override val stubContracts: List<String>,
    override val type: String?
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
                logger.log("Looking for a contract repo checkout at: ${contractsRepoDir.canonicalPath}")
                when {
                    !contractsRepoDir.exists() -> {
                        logger.log("Contract repo does not exist.")
                        cloneRepoAndCheckoutBranch(reposBaseDir, this)
                    }
                    contractsRepoDir.exists() && isBehind(contractsRepoDir) -> {
                        logger.log("Contract repo exists but is behind the remote.")
                        cloneRepoAndCheckoutBranch(reposBaseDir, this)
                    }
                    contractsRepoDir.exists() && isClean(contractsRepoDir) -> {
                        logger.log("Contract repo exists, is clean, and is up to date with remote.")
                        ensureThatSpecmaticFolderIsIgnored()
                        contractsRepoDir
                    }
                    else -> {
                        logger.log("Contract repo exists, but it is not clean.")
                        cloneRepoAndCheckoutBranch(reposBaseDir, this)
                    }
                }
            }
        }

        return selector.select(this).map {
            ContractPathData(repoDir.path, repoDir.resolve(it).path, type, gitRepositoryURL, branchName, it)
        }
    }

    private fun isClean(contractsRepoDir: File): Boolean {
        val sourceGit = getSystemGitWithAuth(contractsRepoDir.path)
        return sourceGit.statusPorcelain().isEmpty()
    }

    private fun isBehind(contractsRepoDir: File): Boolean {
        val sourceGit = getSystemGitWithAuth(contractsRepoDir.path)
        sourceGit.fetch()
        return sourceGit.revisionsBehindCount() > 0
    }

    private fun isSpecmaticFolderIgnored(): Boolean {
        val currentWorkingDirectory = File(".").absolutePath
        val sourceGit = getSystemGitWithAuth(currentWorkingDirectory)
        return sourceGit.checkIgnore(DEFAULT_WORKING_DIRECTORY).isNotEmpty()
    }

    private fun cloneRepoAndCheckoutBranch(reposBaseDir: File, gitRepo: GitRepo): File {
        logger.log("Cloning $gitRepositoryURL into ${reposBaseDir.path}")
        reposBaseDir.mkdirs()
        val repositoryDirectory = clone(reposBaseDir, gitRepo)
        when (branchName) {
            null -> logger.log("No branch specified, using default branch")
            else -> checkout(repositoryDirectory, branchName)
        }
        ensureThatSpecmaticFolderIsIgnored()
        return repositoryDirectory
    }

    private fun ensureThatSpecmaticFolderIsIgnored() {
        val gitIgnoreFile = File(".gitignore")
        when (gitIgnoreFile.exists()) {
            true -> {
                if(!isSpecmaticFolderIgnored()){
                    logger.log("A .gitignore file exists for this git repo, but it does not contain the $DEFAULT_WORKING_DIRECTORY folder.")
                    addSpecmaticFolderToGitIgnoreFile(gitIgnoreFile)
                }
            }
            else -> {
                createGitIgnoreFile(gitIgnoreFile)
                addSpecmaticFolderToGitIgnoreFile(gitIgnoreFile, false)
            }
        }
    }

    private fun addSpecmaticFolderToGitIgnoreFile(gitIgnoreFile: File, onNewLine:Boolean = true){
        logger.log("Adding $DEFAULT_WORKING_DIRECTORY folder to the existing .gitignore file.")
        gitIgnoreFile.appendText("${if (onNewLine) "\n" else ""}.specmatic")
    }

    private fun createGitIgnoreFile(gitIgnoreFile: File) {
        logger.log("Creating a gitignore file file as it is missing for the current project.")
        gitIgnoreFile.createNewFile()
    }

    private fun localRepoDir(workingDirectory: String): File = File(workingDirectory).resolve("repos")

    override fun install(workingDirectory: File) {
        val sourceDir = workingDirectory.resolve(repoName)
        val sourceGit = SystemGit(sourceDir.path)

        try {
            println("Checking ${sourceDir.path}")
            if (!sourceDir.exists())
                sourceDir.mkdirs()

            if (!sourceGit.workingDirectoryIsGitRepo() || isEmptyNestedGitDirectory(sourceGit, sourceDir)) {
                println("Found it, not a git dir, recreating...")
                sourceDir.deleteRecursively()
                sourceDir.mkdirs()
                println("Cloning ${this.gitRepositoryURL} into ${sourceDir.canonicalPath}")
                this.cloneRepoAndCheckoutBranch(sourceDir.canonicalFile.parentFile, this)
            } else {
                println("Git repo already exists at ${sourceDir.path}, so ignoring it and moving on")
            }
        } catch (e: Throwable) {
            println("Could not clone ${this.gitRepositoryURL}\n${e.javaClass.name}: ${exceptionCauseMessage(e)}")
        }
    }

    private fun isEmptyNestedGitDirectory(sourceGit: SystemGit, sourceDir: File) =
        (sourceGit.workingDirectoryIsGitRepo() && sourceGit.getRemoteUrl() != this.gitRepositoryURL && sourceDir.listFiles()?.isEmpty() == true)
}

data class GitMonoRepo(override val testContracts: List<String>, override val stubContracts: List<String>,
                       override val type: String?) : ContractSource() {
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
            ContractPathData(monoRepoBaseDir.canonicalPath, configFileLocation.resolve(it).canonicalPath, provider = type, specificationPath = it)
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
