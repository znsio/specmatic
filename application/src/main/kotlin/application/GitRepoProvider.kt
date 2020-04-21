package application

import org.eclipse.jgit.api.Git
import run.qontract.core.ContractBehaviour
import run.qontract.core.Results
import run.qontract.core.testBackwardCompatibility
import run.qontract.core.utilities.jsonStringToValueMap
import java.io.File

data class GitRepoProvider(val repoName: String) : RepoProvider {
    override fun readContract(identifier: ContractIdentifier): String {
        val contractFile = contractFileInRepo(identifier)
        return contractFile.readText()
    }

    private fun pointerInfo(identifier: ContractIdentifier): GitPointerInfo =
            GitPointerInfo(jsonStringToValueMap(identifier.cacheDescriptorFile.readText().trim()))

    private fun contractFileInRepo(identifier: ContractIdentifier): File {
        val pointerInfo =
            when {
                identifier.cacheDescriptorFile.exists() ->
                    GitPointerInfo(jsonStringToValueMap(identifier.cacheDescriptorFile.readText().trim()))
                else -> GitPointerInfo(repoName, toContractPathInRepo(repoName, identifier))
            }

        return File(pointerInfo.contractPath)
    }

    override fun updateContract(identifier: ContractIdentifier, contractFile: File) {
        val contractFileInRepo = contractFileInRepo(identifier)
        contractFileInRepo.writeText(contractFile.readText())

        commit(identifier)
    }

    private fun commit(identifier: ContractIdentifier) {
        val git = Git.open(gitPath)
        git.add().addFilepattern(".").call()

        val commit = git.commit()
        commit.message = "Updated ${identifier.displayableString}"
        commit.call()
        git.push().setTransportConfigCallback(getTransportCallingCallback()).call()
    }

    private val gitPath get() = pathToFile(qontractRepoDirPath, repoName, "repo")

    override fun addContract(identifier: ContractIdentifier, contractFileWithUpdate: File): PointerInfo {
        updateContract(identifier, contractFileWithUpdate)
        val contractFileInRepo = contractFileInRepo(identifier)

        return GitPointerInfo(
                repoName = repoName,
                contractPath = contractFileInRepo.absolutePath)
    }

    override fun getContractData(identifier: ContractIdentifier): String = contractFileInRepo(identifier).readText()

    override fun testBackwardCompatibility(identifier: ContractIdentifier, contractFile: File): Results {
        val older = ContractBehaviour(contractFileInRepo(identifier).readText())
        val newer = ContractBehaviour(contractFile.readText())

        return testBackwardCompatibility(older, newer)
    }
}

fun toContractPathInRepo(repoName: String, identifier: ContractIdentifier): String {
    val fragment = listOf(qontractRepoDirPath, repoName, "repo")
            .plus(identifier.contractName.split("."))
            .plus(identifier.version.toString())
            .joinToString(File.separator)

    return "$fragment.contract"
}
