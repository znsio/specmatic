package application

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE
import picocli.CommandLine
import picocli.CommandLine.IFactory
import `in`.specmatic.core.LEGACY_CONTRACT_EXTENSION
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpClientFactory
import java.io.File
import java.nio.file.Path

@SpringBootTest(webEnvironment = NONE, classes = [SpecmaticApplication::class, StubCommand::class, HttpClientFactory::class])
internal class StubCommandTest {
    @MockkBean
    lateinit var specmaticConfig: SpecmaticConfig

    @MockkBean
    lateinit var fileOperations: FileOperations

    @Autowired
    lateinit var factory: IFactory

    @MockkBean
    lateinit var watchMaker: WatchMaker

    @MockkBean(relaxUnitFun = true)
    lateinit var watcher: Watcher

    @MockkBean
    lateinit var httpStubEngine: HTTPStubEngine

    @MockkBean
    lateinit var kafkaStubEngine: KafkaStubEngine

    @MockkBean
    lateinit var stubLoaderEngine: StubLoaderEngine

    @Autowired
    lateinit var stubCommand: StubCommand

    @BeforeEach
    fun `clean up stub command`() {
        stubCommand.contractPaths = arrayListOf()
    }

    @Test
    fun `when contract files are not given it should load from qontract config`() {
        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION"))

        CommandLine(stubCommand, factory).execute()

        verify(exactly = 1) { specmaticConfig.contractStubPaths() }
    }

    @Test
    fun `when contract files are given it should not load from qontract config`() {
        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION"))

        CommandLine(stubCommand, factory).execute("/parameter/path/to/contract.$CONTRACT_EXTENSION")

        verify(exactly = 0) { specmaticConfig.contractStubPaths() }
    }

    @Test
    fun `should attempt to start HTTP and Kafka stubs`() {
        val contractPath = "/path/to/contract.$CONTRACT_EXTENSION"
        val contract = """
            Feature: Math API
              Scenario: Random API
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()
        val feature = parseGherkinStringToFeature(contract)

        every { watchMaker.make(listOf(contractPath)) }.returns(watcher)

        val stubInfo = listOf(Pair(feature, emptyList<ScenarioStub>()))
        every { stubLoaderEngine.loadStubs(listOf(contractPath), emptyList()) }.returns(stubInfo)

        val host = "0.0.0.0"
        val port = 9000
        val certInfo = CertInfo()
        val strictMode = false
        val kafkaHost = "localhost"
        val kafkaPort = 9093

        every { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, any(), any(), any()) }.returns(null)
        every { kafkaStubEngine.runKafkaStub(stubInfo, kafkaHost, kafkaPort, false) }.returns(null)

        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf(contractPath))
        every { fileOperations.isFile(contractPath) }.returns(true)
        every { fileOperations.extensionIsNot(contractPath, CONTRACT_EXTENSIONS) }.returns(false)

        val exitStatus = CommandLine(stubCommand, factory).execute(contractPath)
        assertThat(exitStatus).isZero()

        verify(exactly = 1) { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, any(), any(), any()) }
        verify(exactly = 1) { kafkaStubEngine.runKafkaStub(stubInfo, kafkaHost, kafkaPort, false) }
    }

    @ParameterizedTest
    @ValueSource(strings = [CONTRACT_EXTENSION, LEGACY_CONTRACT_EXTENSION])
    fun `when a contract with the correct extension is given it should be loaded`(extension: String, @TempDir tempDir: Path) {
        val validQontract = tempDir.resolve("contract.$extension")

        val qontractFilePath = validQontract.toAbsolutePath().toString()
        File(qontractFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(qontractFilePath)) }.returns(watcher)
        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.$extension"))
        every { fileOperations.isFile(qontractFilePath) }.returns(true)
        every { fileOperations.extensionIsNot(qontractFilePath, CONTRACT_EXTENSIONS) }.returns(false)

        val execute = CommandLine(stubCommand, factory).execute(qontractFilePath)

        assertThat(execute).isEqualTo(0)
    }

    @Test
    @ExpectSystemExitWithStatus(1)
    fun `when a contract with the incorrect extension command should exit with non-zero`(@TempDir tempDir: Path) {
        val invalidQontract = tempDir.resolve("contract.contract")

        val qontractFilePath = invalidQontract.toAbsolutePath().toString()
        File(qontractFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(qontractFilePath)) }.returns(watcher)
        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION"))
        every { fileOperations.isFile(qontractFilePath) }.returns(true)
        every { fileOperations.extensionIsNot(qontractFilePath, CONTRACT_EXTENSIONS) }.returns(true)

        CommandLine(stubCommand, factory).execute(qontractFilePath)
    }

    @Test
    fun `should run the stub with the specified pass-through url target`() {
        val contractPath = "/path/to/contract.$CONTRACT_EXTENSION"
        val contract = """
            Feature: Simple API
              Scenario: GET request
                When GET /
                Then status 200
        """.trimIndent()

        val feature = parseGherkinStringToFeature(contract)

        every { watchMaker.make(listOf(contractPath)) }.returns(watcher)

        val stubInfo = listOf(Pair(feature, emptyList<ScenarioStub>()))
        every { stubLoaderEngine.loadStubs(listOf(contractPath), emptyList()) }.returns(stubInfo)

        val host = "0.0.0.0"
        val port = 9000
        val certInfo = CertInfo()
        val strictMode = false
        val passThroughTargetBase = "http://passthroughTargetBase"

        every { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, passThroughTargetBase, any(), any()) }.returns(null)

        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf(contractPath))
        every { fileOperations.isFile(contractPath) }.returns(true)
        every { fileOperations.extensionIsNot(contractPath, CONTRACT_EXTENSIONS) }.returns(false)

        val exitStatus = CommandLine(stubCommand, factory).execute("--passThroughTargetBase=$passThroughTargetBase", contractPath)
        assertThat(exitStatus).isZero()

        verify(exactly = 1) { httpStubEngine.runHTTPStub(stubInfo, host, port, certInfo, strictMode, any(), any(), any()) }
    }
}
