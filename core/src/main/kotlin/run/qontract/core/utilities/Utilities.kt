@file:JvmName("Utilities")

package run.qontract.core.utilities

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@Throws(IOException::class)
fun readFile(filePath: String): String {
    val fileInputStream = FileInputStream(filePath)
    val contractBuffer = StringBuilder()
    val bufferedReader = BufferedReader(InputStreamReader(fileInputStream))
    var line: String?
    val lineSeparator = System.getProperty("line.separator")
    while (bufferedReader.readLine().also { line = it } != null) {
        contractBuffer.append(line)
        contractBuffer.append(lineSeparator)
    }
    return contractBuffer.toString().trim { it <= ' ' }
}

@Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
fun parseXML(xmlData: String): Document {
    val builderFactory = DocumentBuilderFactory.newInstance()
    val builder = builderFactory.newDocumentBuilder()
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
