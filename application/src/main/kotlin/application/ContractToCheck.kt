package application

import `in`.specmatic.core.Feature
import `in`.specmatic.core.git.GitCommand
import java.io.File

class ContractToCheck(private val contractFile: CanonicalFile, private val git: GitCommand) {
    constructor(contractFilePath: String, git: GitCommand): this(CanonicalFile(contractFilePath), git)

    fun fetchAllOtherContracts(): List<Pair<Feature, String>> =
        listOfAllContractFiles(File(git.gitRoot())).filterNot {
            it.path == contractFile.path
        }.mapNotNull {
            loadContractData(it)
        }

    fun getPathsInContract(): List<String>? = urlPaths(contractFile.readText())
}