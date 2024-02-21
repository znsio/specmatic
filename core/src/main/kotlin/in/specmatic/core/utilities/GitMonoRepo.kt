package `in`.specmatic.core.utilities

import `in`.specmatic.core.git.SystemGit
import java.io.File

data class GitMonoRepo(override val testContracts: List<String>, override val stubContracts: List<String>,
                       override val type: String?) : ContractSource, GitSource {
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
            ContractPathData(
                monoRepoBaseDir.canonicalPath,
                configFileLocation.resolve(it).canonicalPath,
                provider = type,
                specificationPath = it
            )
        }
    }
}