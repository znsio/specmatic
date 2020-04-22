package application.versioning

import application.RepoProvider
import application.qontractRepoDirPath
import application.versioning.git.GitRepoProvider
import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.jsonStringToValueMap

fun getRepoProvider(identifier: ContractIdentifier): RepoProvider {
    val pointerInfo = jsonStringToValueMap(identifier.getCacheDescriptorFile().readText())
    val repoName = pointerInfo.getValue("repoName").toStringValue()

    return when(getRepoType(repoName)) {
        "git" -> GitRepoProvider(repoName)
        else -> throw ContractException("Unidentified repo type")
    }
}

fun getRepoType(repoName: String): String {
    val conf = jsonStringToValueMap(pathToFile(qontractRepoDirPath, repoName, "conf.json").readText())
    return conf.getValue("type").toStringValue()
}
