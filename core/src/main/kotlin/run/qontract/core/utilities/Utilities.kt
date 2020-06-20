@file:JvmName("Utilities")

package run.qontract.core.utilities

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

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
