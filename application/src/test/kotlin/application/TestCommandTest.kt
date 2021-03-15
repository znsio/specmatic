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
import run.qontract.test.QontractJUnitSupport.Companion.CONTRACT_PATHS
import run.qontract.test.QontractJUnitSupport.Companion.HOST
import run.qontract.test.QontractJUnitSupport.Companion.PORT
import run.qontract.test.QontractJUnitSupport.Companion.TIMEOUT
import java.util.stream.Stream

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = arrayOf(QontractApplication::class, TestCommand::class))
internal class TestCommandTest {
    @MockkBean
    lateinit var qontractConfig: QontractConfig

    @MockkBean
    lateinit var junitLauncher: Launcher

    @Autowired
    lateinit var factory: CommandLine.IFactory

    @Autowired
    lateinit var testCommand: TestCommand

    private val contractsToBeRunAsTests = arrayListOf("/config/path/to/contract_1.qontract",
            "/config/path/to/another_contract_1.qontract")

    @BeforeEach
    fun `clean up test command`() {
        testCommand.contractPaths = arrayListOf()
        testCommand.junitReportDirName = null
    }

    @Test
    fun `when contract files are not given it should load from qontract config`() {
        every { qontractConfig.contractTestPaths() }.returns(contractsToBeRunAsTests)

        CommandLine(testCommand, factory).execute()

        verify(exactly = 1) { qontractConfig.contractTestPaths() }
        assertThat(System.getProperty(CONTRACT_PATHS)).isEqualTo(contractsToBeRunAsTests.joinToString(","))
    }

    @Test
    fun `when contract files are given it should not load from qontract config`() {
        CommandLine(testCommand, factory).execute(contractsToBeRunAsTests[0], contractsToBeRunAsTests[1])
        verify(exactly = 0) { qontractConfig.contractTestPaths() }
        assertThat(System.getProperty(CONTRACT_PATHS)).isEqualTo(contractsToBeRunAsTests.joinToString(","))
    }

    @Test
    fun `it should execute junit test with execution listener`() {
        every { junitLauncher.discover(any()) }.returns(mockk())
        every { junitLauncher.registerTestExecutionListeners(any<ContractExecutionListener>()) }.returns(mockk())

        CommandLine(testCommand, factory).execute("api_1.qontract")

        verify(exactly = 1) { junitLauncher.discover(any()) }
        verify(exactly = 1) { junitLauncher.registerTestExecutionListeners(any<ContractExecutionListener>()) }
        verify(exactly = 1) { junitLauncher.execute(any<LauncherDiscoveryRequest>()) }
    }

    @Test
    fun `when junit report directory is set it should also register legacy XML test execution listener`() {
        every { junitLauncher.discover(any()) }.returns(mockk())
        every { junitLauncher.registerTestExecutionListeners(any<ContractExecutionListener>()) }.returns(mockk())
        every { junitLauncher.registerTestExecutionListeners(any<org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener>()) }.returns(mockk())

        CommandLine(testCommand, factory).execute("api_1.qontract", "--junitReportDir", "reports/junit")

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
        every { qontractConfig.contractTestPaths() }.returns(contractsToBeRunAsTests)

        CommandLine(testCommand, factory).execute(optionName, optionValue)

        assertThat(System.getProperty(systemPropertyName)).isEqualTo(systemPropertyValue)
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