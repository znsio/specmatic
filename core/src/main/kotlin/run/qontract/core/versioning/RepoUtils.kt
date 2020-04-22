@file:JvmName(name = "RepoUtils")

package run.qontract.core.versioning

import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import run.qontract.core.versioning.git.GitRepoProvider
import run.qontract.core.pattern.ContractException
import run.qontract.core.qontractRepoDirPath
import run.qontract.core.utilities.jsonStringToValueMap
import java.io.File

fun getContractFilePath(name: String, version: Int): String {
    val identifier = ContractIdentifier(name, version)
    return getRepoProvider(identifier).getContractFilePath(identifier)
}

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

fun getTransportCallingCallback(): TransportConfigCallback {
    return object : TransportConfigCallback {
        override fun configure(transport: Transport?) {
            if (transport is SshTransport) {
                transport.sshSessionFactory = SshdSessionFactory()
            }
        }
    }
}

