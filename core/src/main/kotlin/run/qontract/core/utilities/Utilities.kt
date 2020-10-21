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
import run.qontract.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import run.qontract.core.Resolver
import run.qontract.core.git.SystemGit
import run.qontract.core.git.clone
import run.qontract.core.nativeString
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.NullPattern
import run.qontract.core.pattern.NumberPattern
import run.qontract.core.pattern.parsedJSON
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilder
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
    val builder = newBuilder()
    return removeWhiteSpace(builder.parse(InputSource(StringReader(xmlData))))
}

internal fun newBuilder(): DocumentBuilder {
    val builderFactory = DocumentBuilderFactory.newInstance()
//    builderFactory.isNamespaceAware = true
    val builder = builderFactory.newDocumentBuilder()
    builder.setErrorHandler(null)
    return builder
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

fun strings(list: List<Value>): List<String> {
    return list.map {
        when(it) {
            is StringValue -> it.string
            else -> exitWithMessage("All members of the paths array must be strings, but found one (${it.toStringValue()}) which was not")
        }
    }
}

fun loadSources(configFilePath: String): List<ContractSource> = loadSources(File(configFilePath))

fun loadSources(configFile: File): List<ContractSource> = loadSources(loadConfigJSON(configFile))

fun loadConfigJSON(configFile: File): JSONObjectValue {
    val configJson = try {
        parsedJSON(configFile.readText())

    } catch (e: Throwable) {
        exitWithMessage("Error reading the $DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY: ${exceptionCauseMessage(e)}")
    }

    if (configJson !is JSONObjectValue)
        exitWithMessage("The contents of $DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY must be a json object")

    return configJson
}


fun loadSources(configJson: JSONObjectValue): List<ContractSource> {
    val sources = configJson.jsonObject.getOrDefault("sources", null)

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
            else -> throw ContractException("Provider ${nativeString(source.jsonObject, "provider")} not recognised in $DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY")
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

fun ensureExists(configFilePath: String, workingDirectoryPath: String) {
    if(!File(configFilePath).exists())
        exitWithMessage("$configFilePath does not exist")

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
fun contractStubPaths(): List<ContractPathData> =
        contractFilePathsFrom(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY, ".qontract") { source -> source.stubContracts }

fun interface ContractsSelectorPredicate {
    fun select(source: ContractSource): List<String>
}

fun contractTestPathsFrom(configFilePath: String, workingDirectory: String): List<ContractPathData> {
    return contractFilePathsFrom(configFilePath, workingDirectory) { source -> source.testContracts }
}

data class ContractPathData(val baseDir: String, val path: String)

fun contractFilePathsFrom(configFilePath: String, workingDirectory: String, selector: ContractsSelectorPredicate): List<ContractPathData> {
    println("Loading config file $configFilePath")
    val sources = loadSources(configFilePath)

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
            ContractPathData(repoDir.path, repoDir.resolve(it).path)
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

internal fun withNumberType(resolverWithNullType: Resolver) =
        resolverWithNullType.copy(newPatterns = resolverWithNullType.newPatterns.plus("(number)" to NumberPattern))
