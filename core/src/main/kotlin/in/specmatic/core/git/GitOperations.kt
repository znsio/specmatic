@file:JvmName("GitOperations")

package `in`.specmatic.core.git

import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.utilities.GitRepo
import `in`.specmatic.core.utilities.getTransportCallingCallback
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import io.ktor.http.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

fun clone(workingDirectory: File, gitRepo: GitRepo): File {
    val cloneDirectory = gitRepo.directoryRelativeTo(workingDirectory)

    resetCloneDirectory(cloneDirectory)
    clone(gitRepo.gitRepositoryURL, cloneDirectory)

    return cloneDirectory
}

private fun clone(gitRepositoryURI: String, cloneDirectory: File) {
    jgitClone(gitRepositoryURI, cloneDirectory) { exception ->
        information.forDebugging("""Falling back to git command after getting error from jgit (${exception.javaClass.name}: ${exception.message})""")
        SystemGit(cloneDirectory.parent, "-").clone(gitRepositoryURI, cloneDirectory)
    }
}

private fun resetCloneDirectory(cloneDirectory: File) {
    information.forTheUser("Resetting ${cloneDirectory.absolutePath}")
    if (cloneDirectory.exists())
        cloneDirectory.deleteRecursively()
    cloneDirectory.mkdirs()
}

private fun jgitClone(gitRepositoryURI: String, cloneDirectory: File, onFailure: (exception: Throwable) -> Unit) {
    try {
        val cloneCommand = Git.cloneRepository().apply {
            setTransportConfigCallback(getTransportCallingCallback())
            setURI(gitRepositoryURI)
            setDirectory(cloneDirectory)
        }

        val accessToken = getPersonalAccessToken()

        if (accessToken != null) {
            val credentialsProvider: CredentialsProvider = UsernamePasswordCredentialsProvider(accessToken, "")
            cloneCommand.setCredentialsProvider(credentialsProvider)
        } else {
            val ciBearerToken = getBearerToken()

            if (ciBearerToken != null) {
                cloneCommand.setTransportConfigCallback(getTransportCallingCallback(ciBearerToken.encodeOAuth()))
            }
        }

        cloneCommand.call()
    } catch (e: Throwable) {
        onFailure(e)
    }
}

fun loadFromPath(json: Value?, path: List<String>): Value? {
    if (json !is JSONObjectValue)
        return null

    return when(path.size) {
        0 -> null
        1 -> json.jsonObject[path.first()]
        else -> loadFromPath(json.jsonObject[path.first()], path.drop(1))
    }
}

fun getBearerToken(): String? {
    val qontractConfigFile = File(globalConfigFileName)

    return when {
        qontractConfigFile.exists() ->
            readQontractConfig(qontractConfigFile).let { qontractConfig ->
                readBearerFromEnvVariable(qontractConfig) ?: readBearerFromFile(qontractConfig)
            }
        else -> null.also {
            information.forTheUser("$globalConfigFileName not found")
            information.forTheUser("Current working directory is ${File(".").absolutePath}")
        }
    }
}

private fun readBearerFromEnvVariable(qontractConfig: Value): String? {
    return loadFromPath(qontractConfig, listOf("auth", "bearer-environment-variable"))?.toStringValue()?.let { bearerName ->
        information.forTheUser("Found bearer name $bearerName")

        System.getenv(bearerName).also {
            if(it != null) information.forTheUser("$bearerName is not empty")
        }
    }
}

private fun readBearerFromFile(qontractConfig: Value): String? {
    return loadFromPath(qontractConfig, listOf("auth", "bearer-file"))?.toStringValue()?.let { bearerFileName ->
        information.forTheUser("Found bearer file name $bearerFileName")

        val bearerFile = File(bearerFileName).absoluteFile

        when {
            bearerFile.exists() -> {
                information.forTheUser("Found bearer file ${bearerFile.absolutePath}")
                bearerFile.readText().trim()
            }
            else -> {
                information.forTheUser("Could not find bearer file ${bearerFile.absolutePath}")
                null
            }
        }
    }
}

fun getPersonalAccessToken(): String? {
    val homeDir = File(System.getProperty("user.home"))

    if(homeDir.exists()) {
        val configFile = homeDir.resolve(globalConfigFileName)

        if(configFile.exists()) {
            val qontractConfig = readQontractConfig(configFile)

            val azureAccessTokenKey = "azure-access-token"
            if (qontractConfig is JSONObjectValue && qontractConfig.jsonObject.containsKey(azureAccessTokenKey)) {
                return qontractConfig.getString(azureAccessTokenKey)
            }
        }
    }

    return null
}

private fun readQontractConfig(qontractConfigFile: File) = parsedJSON(qontractConfigFile.readText())
