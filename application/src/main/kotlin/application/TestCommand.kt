package application

import application.test.ContractExecutionListener
import `in`.specmatic.core.APPLICATION_NAME_LOWER_CASE
import `in`.specmatic.core.Configuration
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.newXMLBuilder
import `in`.specmatic.core.utilities.xmlToString
import `in`.specmatic.test.SpecmaticJUnitSupport
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.CONFIG_FILE_NAME
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.CONTRACT_PATHS
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.ENV_NAME
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.FILTER_NAME
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.FILTER_NOT_NAME
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.HOST
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.INLINE_SUGGESTIONS
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.PORT
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.SUGGESTIONS_PATH
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.SUITE_LIST
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.TEST_BASE_URL
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.TIMEOUT
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.VARIABLES_FILE_NAME
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.w3c.dom.Document
import org.xml.sax.InputSource
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.io.PrintWriter
import java.io.StringReader
import java.nio.file.Paths
import java.util.concurrent.Callable

@Command(name = "test",
        mixinStandardHelpOptions = true,
        description = ["Run contract as tests"])
class TestCommand : Callable<Unit> {
    @Autowired
    lateinit var specmaticConfig: SpecmaticConfig

    @Autowired
    lateinit var junitLauncher: Launcher

    @CommandLine.Parameters(arity = "0..*", description = ["Contract file paths"])
    var contractPaths: List<String> = emptyList()

    @Option(names = ["--host"], description = ["The host to bind to, e.g. localhost or some locally bound IP"], defaultValue = "localhost")
    lateinit var host: String

    @Option(names = ["--port"], description = ["The port to bind to"])
    var port: Int = 0

    @Option(names = ["--testBaseURL"], description = ["The base URL, use this instead of host and port"], defaultValue = "")
    lateinit var testBaseURL: String

    @Option(names = ["--suggestionsPath"], description = ["Location of the suggestions file"], defaultValue = "")
    lateinit var suggestionsPath: String

    @Option(names = ["--suggestions"], description = ["A json value with scenario name and multiple suggestions"], defaultValue = "")
    var suggestions: String = ""

    @Option(names = ["--filter-name"], description = ["Run only tests with this value in their name"], defaultValue = "\${env:SPECMATIC_FILTER_NAME}")
    var filterName: String = ""

    @Option(names = ["--filter-not-name"], description = ["Run only tests which do not have this value in their name"], defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}")
    var filterNotName: String = ""

    @Option(names = ["--env"], description = ["Environment name"])
    var envName: String = ""

    @Option(names = ["--https"], description = ["Use https instead of the default http"], required = false)
    var useHttps: Boolean = false

    @Option(names = ["--timeout"], description = ["Specify a timeout for the test requests"], required = false, defaultValue = "60")
    var timeout: Int = 60

    @Option(names = ["--junitReportDir"], description = ["Create junit xml reports in this directory"])
    var junitReportDirName: String? = null

    @Option(names = ["--config"], description = ["Configuration file name ($APPLICATION_NAME_LOWER_CASE.json by default)"])
    var configFileName: String? = null

    @Option(names = ["--variables"], description = ["Variables file name ($APPLICATION_NAME_LOWER_CASE.json by default)"])
    var variablesFileName: String? = null

    @Option(names = ["--suites"], description = ["Comma-separated list of suites to run (W = Within-bounds tests, O = Outside-bounds tests, WG = Within-bounds generated tests)"])
    var suites: String = ""

    @Option(names = ["--debug"], description = ["Debug logs"])
    var verboseMode: Boolean = false

    override fun call() = try {
        if(verboseMode) {
            logger = Verbose()
        }

        configFileName?.let {
            Configuration.globalConfigFileName = it
            System.setProperty(CONFIG_FILE_NAME, it)
        }

        if(port == 0) {
            port = when {
                useHttps -> 443
                else -> 9000
            }
        }

        val protocol = when {
            port == 443 -> "https"
            useHttps -> "https"
            else -> "http"
        }

        System.setProperty(HOST, host)
        System.setProperty(PORT, port.toString())
        System.setProperty(TIMEOUT, timeout.toString())
        System.setProperty(SUGGESTIONS_PATH, suggestionsPath)
        System.setProperty(INLINE_SUGGESTIONS, suggestions)
        System.setProperty(ENV_NAME, envName)
        System.setProperty("protocol", protocol)
        System.setProperty(SUITE_LIST, suites)

        if(filterName.isNotBlank()) {
            System.setProperty(FILTER_NAME, filterName)
        }

        if(filterNotName.isNotBlank()) {
            System.setProperty(FILTER_NOT_NAME, filterNotName)
        }

        variablesFileName?.let {
            System.setProperty(VARIABLES_FILE_NAME, it)
        }

        if(testBaseURL.isNotEmpty())
            System.setProperty(TEST_BASE_URL, testBaseURL)

        if(contractPaths.isNotEmpty()) System.setProperty(CONTRACT_PATHS, contractPaths.joinToString(","))

        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(SpecmaticJUnitSupport::class.java))
                .build()
        junitLauncher.discover(request)
        val contractExecutionListener = ContractExecutionListener()
        junitLauncher.registerTestExecutionListeners(contractExecutionListener)

        junitReportDirName?.let { dirName ->
            val reportListener = org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener(Paths.get(dirName), PrintWriter(System.out, true))
            junitLauncher.registerTestExecutionListeners(reportListener)
        }

        junitLauncher.execute(request)

        junitReportDirName?.let {
            val reportDirectory = File(it)
            val reportFile = reportDirectory.resolve("TEST-junit-jupiter.xml")

            if(reportFile.isFile) {
                val newText = reportFile.readText().let { text ->
                    text.replace("JUnit Jupiter", "Contract Tests")
                }.let { text ->
                    val builder = newXMLBuilder()
                    val reportXML: Document = builder.parse(InputSource(StringReader(text)))

                    val actualTestNameMap: Map<String, String> = SpecmaticJUnitSupport.testsNames.mapIndexed { index, actualTestName ->
                        val nodeTestName = "contractAsTest()[${index + 1}]"
                        nodeTestName to actualTestName
                    }.toMap()

                    for(i in 0..reportXML.documentElement.childNodes.length.minus(1)) {
                        val node = reportXML.documentElement.childNodes.item(i)

                        if(node.nodeName == "testcase") {
                            val nodeTestName: String = node.attributes.getNamedItem("name").nodeValue
                            val actualTestName = actualTestNameMap[nodeTestName]
                            node.attributes.getNamedItem("name").nodeValue = actualTestName
                        }
                    }

                    xmlToString(reportXML)
                }

                reportFile.writeText(newText)
            } else {
                throw ContractException("Was expecting a JUnit report file called TEST-junit-jupiter.xml inside $junitReportDirName but could not find it.")
            }
        }

        contractExecutionListener.exitProcess()
    }
    catch (e: Throwable) {
        logger.log(e)
    }
}