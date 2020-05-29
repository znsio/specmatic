package run.qontract.core.versioning.git

import org.eclipse.jgit.api.Git
import run.qontract.core.*
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.versioning.*
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
    override fun getContractFilePath(identifier: ContractIdentifier): String = contractFileInRepo(identifier).absolutePath

    override fun testBackwardCompatibility(identifier: ContractIdentifier, contractFile: File): Results {
        val older = ContractBehaviour(contractFileInRepo(identifier).readText())
        val newer = ContractBehaviour(contractFile.readText())

        return testBackwardCompatibility2(older, newer)
    }
}

fun identifierToContractGitFile(repoName: String, identifier: ContractIdentifier): String {
    val fragment = listOf(qontractRepoDirPath, repoName, "repo")
            .plus(identifier.name.split("."))
            .plus(identifier.version.toString())
            .joinToString(File.separator)

    return "$fragment.$QONTRACT_EXTENSION"
}
