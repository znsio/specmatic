@file:JvmName("Utilities")

package run.qontract.core.utilities

import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.*
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@OptIn(KtorExperimentalAPI::class)
fun readFromAPI(urlStr: String?): String {
    val ktorClient = io.ktor.client.HttpClient(CIO)

    var response = ""

    val url = URL(urlStr)

    runBlocking {
        val ktorResponse: HttpResponse = ktorClient.request(url) {
            this.method = io.ktor.http.HttpMethod.Get
            this.accept(io.ktor.http.ContentType.Application.Json)
        }

        if(ktorResponse.status != HttpStatusCode.OK)
            throw Exception("API responded with ${ktorResponse.status}")

        response = ktorResponse.readText()
    }

    return response
}

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
var brokerURL: String = "http://localhost:8089"
private const val currentDirectory = "./"
private const val contractDirectory = "contract"
private const val defaultContractFilePath = "$contractDirectory/service.contract"
private const val serviceManifestFilename = "component.yaml"
const val serviceManifestPath: String = currentDirectory + serviceManifestFilename