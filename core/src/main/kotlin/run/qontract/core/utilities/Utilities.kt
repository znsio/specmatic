@file:JvmName("Utilities")

package run.qontract.core.utilities

import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.TransportHttp
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.Node.ELEMENT_NODE
import org.w3c.dom.Node.TEXT_NODE
import org.xml.sax.InputSource
import run.qontract.consoleLog
import run.qontract.core.Resolver
import run.qontract.core.git.SystemGit
import run.qontract.core.git.clone
import run.qontract.core.nativeString
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.NullPattern
import run.qontract.core.pattern.parsedJSONStructure
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.io.File
import java.io.StringReader
import java.io.StringWriter
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
    return removeWhiteSpace(builder.parse(InputSource(StringReader(xmlData))))
}

fun removeWhiteSpace(document: Document): Document {
    removeWhiteSpace(document.documentElement)
    return document
}

fun removeWhiteSpace(node: Node): Node {
    if(node.hasChildNodes() && !containsTextContent(node)) {
        val childNodes = 0.until(node.childNodes.length).map { i ->
            node.childNodes.item(i)
        }

        childNodes.forEach {
            if (it.nodeType == TEXT_NODE && node.nodeType == ELEMENT_NODE && it.textContent.trim().isBlank())
                node.removeChild(it)
            else if (it.hasChildNodes())
                removeWhiteSpace(it)
        }
    }

    return node
}

private fun containsTextContent(node: Node) =
        node.childNodes.length == 1 && node.firstChild.nodeType == TEXT_NODE && node.nodeType == ELEMENT_NODE

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

fun getTransportCallingCallback(bearerToken: String? = null): TransportConfigCallback {
    return TransportConfigCallback { transport ->
        if (transport is SshTransport) {
            transport.sshSessionFactory = SshdSessionFactory()
        } else if(bearerToken != null && transport is TransportHttp) {
            println("Setting Authorization header")
            transport.setAdditionalHeaders(mapOf("Authorization" to "Bearer $bearerToken"))
        }
    }
}

fun pathSelector(repoConfig: Map<String, Value>): SelectorFunction {
    return when(val sourcePaths = getStringArray(repoConfig, "paths")) {
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

fun loadSourceDataFromManifest(manifestFile: String): List<ContractSource> = loadSourceDataFromManifest(File(manifestFile))

fun loadSourceDataFromManifest(manifestFile: File): List<ContractSource> = loadSourceDataFromManifest(loadJSONFromManifest(manifestFile))

fun loadJSONFromManifest(manifestFile: File): JSONObjectValue {
    val manifestJson = try {
        parsedJSONStructure(manifestFile.readText())

    } catch (e: Throwable) {
        exitWithMessage("Error reading the manifest: ${exceptionCauseMessage(e)}")
    }

    if (manifestJson !is JSONObjectValue)
        exitWithMessage("The contents of the manifest must be a json object")

    return manifestJson
}


fun loadSourceDataFromManifest(manifestJson: JSONObjectValue): List<ContractSource> {
    val sources = manifestJson.jsonObject.getOrDefault("sources", null)

    if(sources !is JSONArrayValue)
        exitWithMessage("The \"sources\" key must hold a list of sources.")

    return sources.list.map { source ->
        if (source !is JSONObjectValue)
            exitWithMessage("Every element of the sources json array must be a json object, but got this: ${source.toStringValue()}")

        when(nativeString(source.jsonObject, "provider")) {
            "git" -> {
                val repositoryURL = nativeString(source.jsonObject, "repository")

                val stubPaths = jsonArray(source, "stub")
                val testPaths = jsonArray(source, "test")

                when (repositoryURL) {
                    null -> GitMonoRepo(testPaths, stubPaths)
                    else -> GitRepo(repositoryURL, testPaths, stubPaths)
                }
            }
            else -> throw ContractException("Provider ${nativeString(source.jsonObject, "provider")} not recognised in manifest data")
        }
    }
}

internal fun jsonArray(source: JSONObjectValue, key: String): List<String> {
    return when(val value = source.jsonObject[key]) {
        is JSONArrayValue -> value.list.map { it.toStringValue() }
        null -> emptyList()
        else -> throw ContractException("Expected $key to be an array")
    }
}

internal fun ensureEmptyOrNotExists(workingDirectory: File) {
    if(workingDirectory.exists() && workingDirectory.listFiles()?.isNotEmpty() == true) {
        exitWithMessage("The provided working directory ${workingDirectory.path} must be empty or must not exist")
    }
}

fun ensureExists(manifestFilePath: String, workingDirectoryPath: String) {
    if(!File(manifestFilePath).exists())
        exitWithMessage("Manifest file $manifestFilePath does not exist")

    val workingDirectory = File(workingDirectoryPath)

    if(!workingDirectory.exists()) {
        try {
            workingDirectory.mkdirs()
        } catch (e: Throwable) {
            exitWithMessage(exceptionCauseMessage(e))
        }
    }
}

fun createIfDoesNotExist(workingDirectoryPath: String) {
    val workingDirectory = File(workingDirectoryPath)

    if(!workingDirectory.exists()) {
        try {
            workingDirectory.mkdirs()
        } catch (e: Throwable) {
            exitWithMessage(exceptionCauseMessage(e))
        }
    }
}

fun exitIfDoesNotExist(label: String, filePath: String) {
    if(!File(filePath).exists())
        exitWithMessage("${label.capitalize()} $filePath does not exist")
}

// Used by QontractJUnitSupport users for loading contracts to stub or mock
fun contractStubPaths(): List<String> =
        contractFilePathsFrom("qontract.json", ".qontract") { source -> source.stubContracts }

fun interface ContractsSelectorPredicate {
    fun select(source: ContractSource): List<String>
}

fun contractTestPathsFrom(manifestFile: String, workingDirectory: String): List<String> {
    return contractFilePathsFrom(manifestFile, workingDirectory) { source -> source.testContracts }
}

fun contractFilePathsFrom(manifestFile: String, workingDirectory: String, selector: ContractsSelectorPredicate): List<String> {
    println("Loading manifest file $manifestFile")
    val sources = loadSourceDataFromManifest(manifestFile)

    return sources.flatMap { source ->
        val repoDir = when(source) {
            is GitRepo -> {
                println("Looking for contracts in local environment")
                val userHome = File(System.getProperty("user.home"))
                val defaultQontractWorkingDir = userHome.resolve(".qontract/repos")
                val defaultRepoDir = source.directoryRelativeTo(defaultQontractWorkingDir)

                when {
                    defaultRepoDir.exists() && SystemGit(defaultRepoDir.path).workingDirectoryIsGitRepo() -> {
                        println("Using local contracts")
                        defaultRepoDir
                    }
                    else -> {
                        val reposBaseDir = File(workingDirectory).resolve("repos")
                        println("Couldn't find local contracts, cloning ${source.gitRepositoryURL} into ${reposBaseDir.path}")
                        if(!reposBaseDir.exists())
                            reposBaseDir.mkdirs()

                        clone(reposBaseDir, source)
                    }
                }
            }
            is GitMonoRepo -> File(".")
        }

        selector.select(source).map {
            repoDir.resolve(it).path
        }
    }.also {
        println("Contract file paths #######")
        println(it)
    }
}

class UncaughtExceptionHandler: Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread?, e: Throwable?) {
        if(e != null)
            consoleLog(exceptionCauseMessage(e))

        exitProcess(1)
    }
}

internal fun withNullPattern(resolver: Resolver): Resolver {
    return resolver.copy(newPatterns = resolver.newPatterns.plus("(empty)" to NullPattern))
}
