@file:JvmName("GitOperations")

package `in`.specmatic.core.git

import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.log.logger
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
import java.util.*

fun clone(workingDirectory: File, gitRepo: GitRepo): File {
    val cloneDirectory = gitRepo.directoryRelativeTo(workingDirectory)

    resetCloneDirectory(cloneDirectory)
    clone(gitRepo.gitRepositoryURL, cloneDirectory)

    return cloneDirectory
}

private fun clone(gitRepositoryURI: String, cloneDirectory: File) {
    jgitClone(gitRepositoryURI, cloneDirectory) { exception ->
        logger.debug("""Falling back to git command after getting error from jgit (${exception.javaClass.name}: ${exception.message})""")
        resetCloneDirectory(cloneDirectory)
        SystemGit(cloneDirectory.parent, "-").clone(gitRepositoryURI, cloneDirectory)
    }
}

private fun resetCloneDirectory(cloneDirectory: File) {
    logger.log("Resetting ${cloneDirectory.absolutePath}")
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

        val accessTokenText = getPersonalAccessToken()

        if (accessTokenText != null) {
//            val accessToken = String(Base64.getEncoder().encode("$token:".encodeToByteArray()))

            val credentialsProvider: CredentialsProvider = UsernamePasswordCredentialsProvider(accessTokenText, "")
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
            logger.log("$globalConfigFileName not found")
            logger.log("Current working directory is ${File(".").absolutePath}")
        }
    }
}

private fun readBearerFromEnvVariable(qontractConfig: Value): String? {
    return loadFromPath(qontractConfig, listOf("auth", "bearer-environment-variable"))?.toStringLiteral()?.let { bearerName ->
        logger.log("Found bearer environment variable name \"$bearerName\"")

        System.getenv(bearerName).also {
            if(it == null)
                logger.log("$bearerName environment variable has not been set")
        }
    }
}

private fun readBearerFromFile(qontractConfig: Value): String? {
    return loadFromPath(qontractConfig, listOf("auth", "bearer-file"))?.toStringLiteral()?.let { bearerFileName ->
        logger.log("Found bearer file name $bearerFileName")

        val bearerFile = File(bearerFileName).absoluteFile

        when {
            bearerFile.exists() -> {
                logger.log("Found bearer file ${bearerFile.absolutePath}")
                bearerFile.readText().trim()
            }
            else -> {
                logger.log("Could not find bearer file ${bearerFile.absolutePath}")
                null
            }
        }
    }
}

fun getPersonalAccessToken(): String? {
    return getPersonalAccessTokenProperty() ?: getPersonalAccessTokenEnvVariable() ?: getPersonalAccessTokenConfig()
}

private fun getPersonalAccessTokenConfig(): String? {
    val homeDir = File(System.getProperty("user.home"))
    val configFile = homeDir.resolve("specmatic-azure.json")

    if (configFile.exists()) {
        val qontractConfig = readQontractConfig(configFile)

        "azure-access-token".let { azureAccessTokenKey ->
            if (qontractConfig is JSONObjectValue && qontractConfig.jsonObject.containsKey(azureAccessTokenKey)) {
                return qontractConfig.getString(azureAccessTokenKey).also {
                    println("Using personal access token from home directory config")
                }
            }
        }

        "personal-access-token".let { azureAccessTokenKey ->
            if (qontractConfig is JSONObjectValue && qontractConfig.jsonObject.containsKey(azureAccessTokenKey)) {
                return qontractConfig.getString(azureAccessTokenKey).also {
                    println("Using personal access token from home directory config")
                }
            }
        }
    }

    return null
}

private fun getPersonalAccessTokenEnvVariable(): String? {
    val environmentVariableName = "PERSONAL_ACCESS_TOKEN"

    return System.getenv(environmentVariableName)?.also {
        println("Using personal access token from environment variable")
    }
}

private fun getPersonalAccessTokenProperty(): String? {
    val accessTokenVariableName = "personalAccessToken"

    return System.getProperty(accessTokenVariableName)?.also {
        println("Using personal access token from property")
    }
}

private fun readQontractConfig(qontractConfigFile: File) = parsedJSON(qontractConfigFile.readText())
