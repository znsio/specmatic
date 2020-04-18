@file:JvmName("Utilities")

package run.qontract.core.utilities

import run.qontract.core.ComponentManifest
import run.qontract.core.utilities.BrokerClient.getContractVersions
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.*
import java.net.URL
import java.util.*
import java.util.stream.Collectors
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
fun getServiceContract(contractFilePath: String?): String {
    val filepath = contractFilePath ?: currentDirectory + defaultContractFilePath
    return readFile(filepath)
}

@Throws(IOException::class)
fun loadDependencyInfo(): MutableMap<String, Any?>? {
    val componentManifest = ComponentManifest()
    if (componentManifest.hasDependencies()) {
        val info = mutableMapOf<String, Any?>()
        val dependencies = componentManifest.dependencies
        val dependencyNames: MutableSet<String?> = dependencies.keys
        for (dependencyName in dependencyNames) {
            val majorVersion = componentManifest.getDependencyMajorVersion(dependencyName)
            val contractTest = readFile("$currentDirectory/contract/tests/$dependencyName.feature")
            val contractInfo = mutableMapOf<String, Any?>()
            contractInfo["version"] = majorVersion
            contractInfo["contract-test"] = contractTest
            info[dependencyName as String] = contractInfo
        }
        return info
    }
    return null
}

@OptIn(KtorExperimentalAPI::class)
fun writeToAPI(method: io.ktor.http.HttpMethod, urlStr: String?, contractMessage: Map<String, Any?>): String {
    val ktorClient = io.ktor.client.HttpClient(CIO)

    var response = ""

    val url = URL(urlStr)

    runBlocking {
        val ktorResponse: HttpResponse = ktorClient.request(url) {
            this.method = method
            this.contentType(io.ktor.http.ContentType.Application.Json)
            this.body = nativeMapToJsonString(contractMessage)
        }

        if(ktorResponse.status != HttpStatusCode.OK)
            throw Exception("API responded with ${ktorResponse.status}")

        response = ktorResponse.readText()
    }

    return response
}

@Throws(IOException::class)
fun calculateExclusions(componentManifest: ComponentManifest): Array<String> {
    return calculateExclusions(componentManifest.componentName, componentManifest.componentContractMajorVersion, componentManifest.componentContractMinorVersion)
}

@Throws(IOException::class)
fun getMissingMinorVersions(dependency: String?, majorVersion: Int, minorVersion: Int): List<Int> {
    val availableContractVersions = getContractVersions(dependency!!)
    return availableContractVersions.stream()
            .filter { version: ArrayList<Int> -> version[0] == majorVersion && version[1] > minorVersion }
            .map { version: ArrayList<Int> -> version[1] }
            .collect(Collectors.toList())
}

@Throws(IOException::class)
fun calculateExclusions(contractName: String?, majorVersion: Int, supportedMinorVersion: Int): Array<String> {
    val contractVersions = getContractVersions(contractName!!)
    val exclusionArrayList = ArrayList<String>()
    for (version in contractVersions) {
        if (version[0] == majorVersion) {
            if (version[1] > supportedMinorVersion) {
                val versionStrings = ArrayList<String>()
                for (integer in version) {
                    versionStrings.add(integer.toString())
                }
                val versionString = "~@" + java.lang.String.join(".", versionStrings)
                exclusionArrayList.add(versionString)
            }
        }
    }
    return exclusionArrayList.toTypedArray()
}

@Throws(IOException::class)
fun getContractGherkin(provider: String): String {
    val contractInfoBuffer = StringBuilder()
    val componentManifest = ComponentManifest()
    componentManifest.dependencies
    val url = URL("$brokerURL/contracts?provider=$provider")
    BufferedReader(InputStreamReader(url.openStream())).use { bufferedReader ->
        var tmp: String?
        while (bufferedReader.readLine().also { tmp = it } != null) contractInfoBuffer.append(tmp).append(System.lineSeparator())
    }
    val contractInfo = jsonStringToMap(contractInfoBuffer.toString())
    return contractInfo["spec"].toString()
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

@get:Throws(IOException::class)
val contractGherkinForCurrentComponent: String
    get() {
        val componentManifest = ComponentManifest()
        val componentName = componentManifest.componentName
        val majorVersion = componentManifest.componentContractMajorVersion
        val minorVersion = componentManifest.componentContractMinorVersion
        val url = URL("$brokerURL/contracts?provider=$componentName&majorVersion=$majorVersion&minorVersion=$minorVersion")
        val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
        val contractInfoBuffer = StringBuilder()
        try {
            var tmp: String?
            while (bufferedReader.readLine().also { tmp = it } != null) {
                contractInfoBuffer.append(tmp).append(System.lineSeparator())
            }
        } finally {
            bufferedReader.close()
        }
        val contractInfo = jsonStringToMap(contractInfoBuffer.toString())
        return contractInfo["spec"].toString()
    }
val contractFilePath: String
    get() = currentDirectory + defaultContractFilePath
var brokerURL: String = "http://localhost:8089"
private const val currentDirectory = "./"
private const val contractDirectory = "contract"
private const val defaultContractFilePath = "$contractDirectory/service.contract"
private const val serviceManifestFilename = "component.yaml"
const val serviceManifestPath: String = currentDirectory + serviceManifestFilename