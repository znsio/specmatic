@file:JvmName("Utilities")

package run.qontract.core.utilities

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import run.qontract.core.nativeString
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.io.*
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.system.exitProcess

fun exitWithMessage(message: String): Nothing {
    println(message)
    exitProcess(1)
}

fun exceptionCauseMessage(e: Throwable): String {
    return when(val messageStack = exceptionMessageStack(e, emptyList())) {
        emptyList<String>() -> "Exception class=${e.javaClass.name}, no message found"
        else -> {
            val messageString = messageStack.joinToString("; ") { it.trim().removeSuffix(".") }
            "Error: $messageString"
        }
    }
}

fun exceptionMessageStack(e: Throwable, messages: List<String>): List<String> {
    val message = e.localizedMessage ?: e.message
    val newMessages = if(message != null) messages.plus(message) else messages

    return when(val cause = e.cause) {
        null -> newMessages
        else -> exceptionMessageStack(cause, newMessages)
    }
}

fun readFile(filePath: String): String {
    return File(filePath).readText().trim()
}

fun parseXML(xmlData: String): Document {
    val builderFactory = DocumentBuilderFactory.newInstance()
    val builder = builderFactory.newDocumentBuilder()
    builder.setErrorHandler(null)
    return builder.parse(InputSource(StringReader(xmlData)))
}

fun xmlToString(node: Node): String = xmlToString(DOMSource(node))

private fun xmlToString(domSource: DOMSource): String {
    val writer = StringWriter()
    val result = StreamResult(writer)
    val tf = TransformerFactory.newInstance()
    val transformer = tf.newTransformer()
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    transformer.transform(domSource, result)
    return writer.toString()
}

val contractFilePath: String
    get() = currentDirectory + defaultContractFilePath

private const val currentDirectory = "./"
private const val contractDirectory = "contract"
private const val defaultContractFilePath = "$contractDirectory/service.contract"

fun clone(workingDirectory: File, gitRepositoryURI: String): File {
    val repoName = gitRepositoryURI.split("/").last()
    val cloneDirectory = workingDirectory.resolve(repoName)
    if(!cloneDirectory.exists()) cloneDirectory.mkdirs()
    val command = Git.cloneRepository().setURI(gitRepositoryURI).setDirectory(cloneDirectory).setTransportConfigCallback(getTransportCallingCallback())
    command.call()

    return cloneDirectory
}

private fun getTransportCallingCallback(): TransportConfigCallback {
    return TransportConfigCallback { transport ->
        if (transport is SshTransport) {
            transport.sshSessionFactory = SshdSessionFactory()
        }
    }
}


fun pathSelector(jsonObject: Map<String, Value>): SelectorFunction {
    return when(val sourcePaths = getStringArray(jsonObject, "paths")) {
        null -> { sourceDir: File, destinationDir: File ->
            val sourceFiles = contractFiles(sourceDir)

            for(sourceFile in sourceFiles) {
                val relative = sourceFile.relativeTo(sourceDir)
                val destinationFile = destinationDir.resolve(relative)
                sourceFile.copyTo(destinationFile)
            }
        }
        else -> { sourceDir: File, destinationDir: File ->
            for(sourcePath in sourcePaths) {
                val sourceFile = sourceDir.resolve(sourcePath)
                val destinationFile = destinationDir.resolve(sourcePath)
                sourceFile.copyTo(destinationFile)
            }
        }
    }
}

fun contractFiles(repoDir: File): List<File> {
    return repoDir.listFiles()?.flatMap { file ->
        when {
            file.isDirectory -> contractFiles(file)
            file.isFile -> {
                when(file.extension) {
                    "qontract" -> listOf(file)
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    } ?: emptyList()
}

fun getStringArray(jsonObject: Map<String, Value>, key: String): List<String>? {
    val data = jsonObject[key]

    return when {
        data == null -> null
        data !is JSONArrayValue -> exitWithMessage("paths must be a json array, but in one of the objects it is ${data.toStringValue()}")
        data.list.isEmpty() -> null
        else -> strings(data.list)
    }
}

fun strings(list: List<Value>): List<String> {
    return list.map {
        when(it) {
            is StringValue -> it.string
            else -> exitWithMessage("All members of the paths array must be strings, but found one (${it.toStringValue()}) which was not")
        }
    }
}

fun loadSourceDataFromManifest(manifestFile: String): List<ContractSource> {
    val manifestJson = try {
        parsedJSONStructure(File(manifestFile).readText())
    } catch (e: Throwable) {
        exitWithMessage("Error loading manifest file ${manifestFile}: ${exceptionCauseMessage(e)}")
    }

    if (manifestJson !is JSONArrayValue) exitWithMessage("The contents of the manifest must be a json array")

    return manifestJson.list.map { repo ->
        if (repo !is JSONObjectValue) exitWithMessage("Every element of the json array in the manifest must be a json object, but got this: ${repo.toStringValue()}")

        val gitRepo = nativeString(repo.jsonObject, "git")
                ?: exitWithMessage("Each config object must contain a key named git with the value being a git repo containing contracts")
        val selector = pathSelector(repo.jsonObject)

        ContractSource(gitRepo, selector)
    }
}

fun ensureEmptyOrNotExists(workingDirectory: File) {
    if(workingDirectory.exists() && workingDirectory.listFiles()?.isNotEmpty() == true) {
        exitWithMessage("The provided working directory ${workingDirectory.path} must be empty or must not exist")
    }
}

fun ensureThatManifestAndWorkingDirectoryExist(manifestFile: File, workingDirectory: File) {
    if(!manifestFile.exists())
        exitWithMessage("Manifest file ${manifestFile.path} does not exist")

    if(!workingDirectory.exists()) {
        try {
            workingDirectory.mkdirs()
        } catch (e: Throwable) {
            exitWithMessage(exceptionCauseMessage(e))
        }
    }
}

fun contractFilePathsFrom(manifestFile: String, workingDirectory: String): List<String> {
    println("Loading manifest file $manifestFile")
    val sources = loadSourceDataFromManifest(manifestFile)

    val contractsDir = File(workingDirectory).resolve("contracts")
    if(!contractsDir.exists()) contractsDir.mkdirs()

    val reposBaseDir = File(workingDirectory).resolve("repos")
    if(!reposBaseDir.exists()) reposBaseDir.mkdirs()

    for (source in sources) {
        println("Cloning ${source.gitRepositoryURL} into ${reposBaseDir.path}")
        val repoDir = clone(reposBaseDir, source.gitRepositoryURL)
        val contractDir = contractsDir.resolve(repoDir.nameWithoutExtension)
        if(!contractDir.exists()) contractDir.mkdirs()

        println("Pulling selected contracts from ${repoDir.path} into ${contractDir.path}")
        source.select(repoDir, contractDir)
    }

    return contractFiles(contractsDir).map { it.path }
}
