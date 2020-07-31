package run.qontract.core.git

import io.ktor.http.encodeOAuth
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.utilities.getTransportCallingCallback
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
import java.io.File

fun clone(workingDirectory: File, gitRepositoryURI: String): File {
    val cloneDirectory = cloneDirectory(workingDirectory, gitRepositoryURI)

    resetCloneDirectory(cloneDirectory)
    clone(gitRepositoryURI, cloneDirectory)

    return cloneDirectory
}

private fun cloneDirectory(workingDirectory: File, gitRepositoryURI: String): File {
    val repoName = lastSegment(gitRepositoryURI)
    return workingDirectory.resolve(repoName)
}

private fun lastSegment(gitRepositoryURI: String) = gitRepositoryURI.split("/").last()

private fun clone(gitRepositoryURI: String, cloneDirectory: File) {
    jgitClone(gitRepositoryURI, cloneDirectory) { exception ->
        println("""Falling back to git command after getting error from jgit (${exception.javaClass.name}: ${exception.message})""")
        GitCommand(cloneDirectory.parent, "-").clone(gitRepositoryURI, cloneDirectory)
    }
}

private fun resetCloneDirectory(cloneDirectory: File) {
    println("Resetting ${cloneDirectory.absolutePath}")
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
    val qontractConfigFile = File("./qontract.json")

    return when {
        qontractConfigFile.exists() ->
            readQontractConfig(qontractConfigFile).let { qontractConfig ->
                readBearerFromEnvVariable(qontractConfig) ?: readBearerFromFile(qontractConfig)
            }
        else -> null.also {
            println("qontract.json not found")
            println("Current working directory is ${File(".").absolutePath}")
        }
    }
}

private fun readBearerFromEnvVariable(qontractConfig: Value): String? {
    return loadFromPath(qontractConfig, listOf("auth", "bearer-environment-variable"))?.toStringValue()?.let { bearerName ->
        println("Found bearer name $bearerName")

        System.getenv(bearerName).also {
            if(it != null) println("$bearerName is not empty")
        }
    }
}

private fun readBearerFromFile(qontractConfig: Value): String? {
    return loadFromPath(qontractConfig, listOf("auth", "bearer-file"))?.toStringValue()?.let { bearerFileName ->
        println("Found bearer file name $bearerFileName")

        File("./$bearerFileName").takeIf { it.exists() }?.let {
            println("Found bearer file")
            it.readText().trim()
        }
    }
}

fun getPersonalAccessToken(): String? {
    val homeDir = File(System.getProperty("user.home"))

    if(homeDir.exists()) {
        val configFile = homeDir.resolve("qontract.json")

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

private fun readQontractConfig(qontractConfigFile: File) = parsedJSONStructure(qontractConfigFile.readText())
