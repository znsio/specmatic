package application.versioning.git

import application.*
import application.versioning.ContractIdentifier
import application.versioning.PointerInfo
import application.versioning.pathToFile
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

    private fun contractFileInRepo(identifier: ContractIdentifier): File {
        val pointerInfo =
            when {
                identifier.getCacheDescriptorFile().exists() ->
                    PointerInfo(jsonStringToValueMap(identifier.getCacheDescriptorFile().readText().trim()))
                else -> PointerInfo(repoName, identifierToContractGitFile(repoName, identifier))
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

    private val gitPath = pathToFile(qontractRepoDirPath, repoName, "repo")

    override fun addContract(identifier: ContractIdentifier, contractFileWithUpdate: File): PointerInfo {
        updateContract(identifier, contractFileWithUpdate)
        val contractFileInRepo = contractFileInRepo(identifier)

        return PointerInfo(
                repoName = repoName,
                contractPath = contractFileInRepo.absolutePath)
    }

    override fun getContractData(identifier: ContractIdentifier): String = contractFileInRepo(identifier).readText()
    override fun getFilePath(identifier: ContractIdentifier) = contractFileInRepo(identifier)

    override fun testBackwardCompatibility(identifier: ContractIdentifier, contractFile: File): Results {
        val older = ContractBehaviour(contractFileInRepo(identifier).readText())
        val newer = ContractBehaviour(contractFile.readText())

        return testBackwardCompatibility(older, newer)
    }
}

fun identifierToContractGitFile(repoName: String, identifier: ContractIdentifier): String {
    val fragment = listOf(qontractRepoDirPath, repoName, "repo")
            .plus(identifier.name.split("."))
            .plus(identifier.version.toString())
            .joinToString(File.separator)

    return "$fragment.contract"
}
