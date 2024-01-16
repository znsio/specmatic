package application

import application.test.ContractExecutionListener
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import picocli.CommandLine
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.newXMLBuilder
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.XMLValue
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.CONTRACT_PATHS
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.HOST
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.PORT
import `in`.specmatic.test.SpecmaticJUnitSupport.Companion.TIMEOUT
import org.assertj.core.api.Assertions.fail
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.stream.Stream

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [SpecmaticApplication::class, TestCommand::class])
internal class TestCommandTest {
    @MockkBean
    lateinit var specmaticConfig: SpecmaticConfig

    @MockkBean
    lateinit var junitLauncher: Launcher

    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var testCommand: TestCommand

    private val contractsToBeRunAsTests = arrayListOf("/config/path/to/contract_1.$CONTRACT_EXTENSION",
            "/config/path/to/another_contract_1.$CONTRACT_EXTENSION")

    @BeforeEach
    fun `clean up test command`() {
        testCommand.contractPaths = arrayListOf()
        testCommand.junitReportDirName = null
        System.clearProperty(CONTRACT_PATHS)
    }

    @Test
    fun `when contract files are not given it should not load from specmatic config`() {
        every { specmaticConfig.contractTestPaths() }.returns(contractsToBeRunAsTests)

        CommandLine(testCommand, factory).execute()

        verify(exactly = 0) { specmaticConfig.contractTestPaths() }
        assertThat(System.getProperty(CONTRACT_PATHS)).isNull()
    }

    @Test
    fun `when contract files are given it should not load from specmatic config`() {
        CommandLine(testCommand, factory).execute(contractsToBeRunAsTests[0], contractsToBeRunAsTests[1])
        verify(exactly = 0) { specmaticConfig.contractTestPaths() }
        assertThat(System.getProperty(CONTRACT_PATHS)).isEqualTo(contractsToBeRunAsTests.joinToString(","))
    }

    @Test
    fun `it should execute junit test with execution listener`() {
        every { junitLauncher.discover(any()) }.returns(mockk())
        every { junitLauncher.registerTestExecutionListeners(any<ContractExecutionListener>()) }.returns(mockk())

        CommandLine(testCommand, factory).execute("api_1.$CONTRACT_EXTENSION")

        verify(exactly = 1) { junitLauncher.discover(any()) }
        verify(exactly = 1) { junitLauncher.registerTestExecutionListeners(any<ContractExecutionListener>()) }
        verify(exactly = 1) { junitLauncher.execute(any<LauncherDiscoveryRequest>()) }
    }

    @Test
    fun `when junit report directory is set it should also register legacy XML test execution listener`() {
        every { junitLauncher.discover(any()) }.returns(mockk())
        every { junitLauncher.registerTestExecutionListeners(any<ContractExecutionListener>()) }.returns(mockk())
        every { junitLauncher.registerTestExecutionListeners(any<org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener>()) }.returns(mockk())

        CommandLine(testCommand, factory).execute("api_1.$CONTRACT_EXTENSION", "--junitReportDir", "reports/junit")

        verify(exactly = 1) { junitLauncher.discover(any()) }
        verify { junitLauncher.registerTestExecutionListeners(any<ContractExecutionListener>()) }
        verify { junitLauncher.registerTestExecutionListeners(any<org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener>()) }
        verify(exactly = 1) { junitLauncher.execute(any<LauncherDiscoveryRequest>()) }
    }

    @ParameterizedTest
    @MethodSource("commandLineArguments")
    fun `applies command line arguments`(
            optionName: String,
            optionValue: String,
            systemPropertyName: String,
            systemPropertyValue: String
    ) {
        every { specmaticConfig.contractTestPaths() }.returns(contractsToBeRunAsTests)

        CommandLine(testCommand, factory).execute(optionName, optionValue)

        assertThat(System.getProperty(systemPropertyName)).isEqualTo(systemPropertyValue)
    }

    @Test
    fun `should replace the names of the tests and the top-level test suite in a JUnit XML text generated by the test command`() {
        val initialJUnitReport = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="JUnit Jupiter" tests="1" skipped="0" failures="0" errors="0" time="1.22" hostname="" timestamp="">
            <properties>
            <property name="contractPaths" value="service.yaml"/>
            </properties>
            <testcase name="contractTest()[1]" classname="in.specmatic.test.SpecmaticJUnitSupport" time="1.028">
            <system-out><![CDATA[
            unique-id: [engine:junit-jupiter]/[class:in.specmatic.test.SpecmaticJUnitSupport]/[test-factory:contractTest()]/[dynamic-test:#1]
            display-name:  Scenario: GET /pets/(petid:number) -> 200 | EX:200_OKAY
            ]]></system-out>
            </testcase>
            <system-out><![CDATA[
            unique-id: [engine:junit-jupiter]
            display-name: JUnit Jupiter
            ]]></system-out>
            </testsuite>
        """.trimIndent()

        val expectedJUnitReport = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="Contract Tests" tests="1" skipped="0" failures="0" errors="0" time="1.22" hostname="" timestamp="">
            <properties>
            <property name="contractPaths" value="service.yaml"/>
            </properties>
            <testcase name="Scenario: GET /pets/(petid:number) -&gt; 200 | EX:200_OKAY" classname="in.specmatic.test.SpecmaticJUnitSupport" time="1.028">
            <system-out><![CDATA[
            unique-id: [engine:junit-jupiter]/[class:in.specmatic.test.SpecmaticJUnitSupport]/[test-factory:contractTest()]/[dynamic-test:#1]
            display-name:  Scenario: GET /pets/(petid:number) -> 200 | EX:200_OKAY
            ]]></system-out>
            </testcase>
            <system-out><![CDATA[
            unique-id: [engine:junit-jupiter]
            display-name: Contract Tests
            ]]></system-out>
            </testsuite>
        """.trimIndent()

        val junitReportWithUpdatedNames = updateNamesInJUnitXML(initialJUnitReport)

        val builder = newXMLBuilder()
        val reportDocument: Document = builder.parse(InputSource(StringReader(junitReportWithUpdatedNames)))

        assertThat(reportDocument.documentElement.attributes.getNamedItem("name").nodeValue).isEqualTo("Contract Tests")

        val testCaseNode = findFirstChildNodeByName(reportDocument.documentElement.childNodes, "testcase") ?: fail("Could not find testcase node in the updated JUnit report")

        assertThat(testCaseNode.attributes.getNamedItem("name").nodeValue).isEqualTo("Scenario: GET /pets/(petid:number) -> 200 | EX:200_OKAY")
    }

    private companion object {
        @JvmStatic
        fun commandLineArguments(): Stream<Arguments> = Stream.of(
                Arguments.of("--port", "9999", PORT, "9999"),
                Arguments.of("--host", "10.10.10.10", HOST, "10.10.10.10"),
                Arguments.of("--timeout", "33", TIMEOUT, "33"),
        )
    }

}