package run.qontract.core.git

import io.ktor.http.encodeOAuth
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.utilities.getTransportCallingCallback
import run.qontract.core.value.JSONObjectValue
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

        val accessToken = getAccessToken()

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

fun getAccessToken(): String? {
    return getPersonalAccessToken()
}

fun getBearerToken(): String? {
    val qontractConfigFile = File("./qontract.json")
    if(qontractConfigFile.exists()) {
        val qontractConfig = parsedJSONStructure(qontractConfigFile.readText())

        val qontractBearerTokenEnvironmentVariableName = "bearer-environment-variable"
        val qontractBearerFileName = "bearer-file"
        if (qontractConfig is JSONObjectValue) {
            if(qontractConfig.jsonObject.containsKey(qontractBearerTokenEnvironmentVariableName)) {
                val bearerName = qontractConfig.getString(qontractBearerTokenEnvironmentVariableName)
                println("Found bearer name $bearerName")

                if (System.getenv(bearerName) != null) {
                    println("$bearerName is not empty")
                    return System.getenv(bearerName)
                }
            } else if(qontractConfig.jsonObject.containsKey(qontractBearerFileName)) {
                val bearerFileName = qontractConfig.getString(qontractBearerFileName)
                println("Found bearer file name $bearerFileName")

                val bearerFile = File("./$bearerFileName")
                if(bearerFile.exists()) {
                    println("Found bearer file")
                    return bearerFile.readText().trim()
                }
            }
        }
    } else {
        println("qontract.json not found")
        println("Current working directory is ${File(".").absolutePath}")
    }

    return null
}

fun getPersonalAccessToken(): String? {
    val homeDir = File(System.getProperty("user.home"))

    if(homeDir.exists()) {
        val configFile = homeDir.resolve("qontract.json")

        if(configFile.exists()) {
            val qontractConfig = parsedJSONStructure(configFile.readText())

            val azureAccessTokenKey = "azure-access-token"
            if (qontractConfig is JSONObjectValue && qontractConfig.jsonObject.containsKey(azureAccessTokenKey)) {
                return qontractConfig.getString(azureAccessTokenKey)
            }
        }
    }

    return null
}
