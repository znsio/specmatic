package application

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.specmatic.core.CONTRACT_EXTENSION
import io.specmatic.core.parseGherkinStringToFeature
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.core.utilities.StubServerWatcher
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.endPointFromHostAndPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import picocli.CommandLine
import java.io.File
import java.nio.file.Path

internal class StubCommandTest {

    @MockK
    lateinit var specmaticConfig: SpecmaticConfig

    @MockK
    lateinit var watchMaker: WatchMaker

    @MockK(relaxUnitFun = true)
    lateinit var watcher: StubServerWatcher

    @MockK
    lateinit var httpStubEngine: HTTPStubEngine

    @MockK
    lateinit var stubLoaderEngine: StubLoaderEngine

    @InjectMockKs
    lateinit var stubCommand: StubCommand

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @AfterEach
    fun cleanUp() {
        clearAllMocks()
        stubCommand.contractPaths = arrayListOf()
        stubCommand.specmaticConfigPath = null
    }

    @Test
    fun `when contract files are not given it should load from specmatic config`() {
        every { specmaticConfig.contractStubPathData() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION").map { ContractPathData("", it) })

        CommandLine(stubCommand).execute()

        verify(exactly = 1) { specmaticConfig.contractStubPathData() }
    }

    @Test
    fun `when contract files are given it should not load from specmatic config`() {
        every { specmaticConfig.contractStubPathData() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION").map { ContractPathData("", it) })

        CommandLine(stubCommand).execute("/parameter/path/to/contract.$CONTRACT_EXTENSION")

        verify(exactly = 0) { specmaticConfig.contractStubPathData() }
    }

    @Test
    fun `should attempt to start a HTTP stub`(@TempDir tempDir: File) {
        val contractPath = osAgnosticPath("${tempDir.path}/contract.$CONTRACT_EXTENSION")
        val contract = """
            Feature: Math API
              Scenario: Random API
                When GET /
                Then status 200
                And response-body (number)
        """.trimIndent()
        val file = File(contractPath).also { it.writeText(contract) }

        try {
            val feature = parseGherkinStringToFeature(contract)

            every { watchMaker.make(listOf(contractPath)) }.returns(watcher)

            val stubInfo = listOf(Pair(feature, emptyList<ScenarioStub>()))
            every {
                stubLoaderEngine.loadStubs(
                    listOf(contractPath).map { ContractPathData("", it) },
                    emptyList(),
                    any(),
                    false
                )
            }.returns(stubInfo)

            val host = "0.0.0.0"
            val port = 9000
            val certInfo = CertInfo()
            val strictMode = false

            every {
                httpStubEngine.runHTTPStub(
                    stubInfo,
                    endPointFromHostAndPort(host, port, certInfo.getHttpsCert()),
                    certInfo,
                    strictMode,
                    any(),
                    httpClientFactory = any(),
                    workingDirectory = any(),
                    gracefulRestartTimeoutInMs = any(),
                    specToBaseUrlMap = any()
                )
            }.returns(
                mockk {
                   every { close() } returns Unit
                }
            )

            every { specmaticConfig.contractStubPaths() }.returns(arrayListOf(contractPath))

            val exitStatus = CommandLine(stubCommand).execute(contractPath)
            assertThat(exitStatus).isZero()

            verify(exactly = 1) {
                httpStubEngine.runHTTPStub(
                    any(),
                    any(),
                    certInfo,
                    strictMode,
                    any(),
                    httpClientFactory = any(),
                    workingDirectory = any(),
                    gracefulRestartTimeoutInMs = any(),
                    specToBaseUrlMap = any()
                )
            }
        } finally {
            file.delete()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [CONTRACT_EXTENSION])
    fun `when a contract with the correct extension is given it should be loaded`(extension: String, @TempDir tempDir: Path) {
        val validSpec = tempDir.resolve("contract.$extension")

        val specFilePath = validSpec.toAbsolutePath().toString()
        File(specFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(specFilePath)) }.returns(watcher)
        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.$extension"))

        val execute = CommandLine(stubCommand).execute(specFilePath)

        assertThat(execute).isEqualTo(0)
    }

    @Test
    @ExpectSystemExitWithStatus(1)
    fun `when a contract with the incorrect extension command should exit with non-zero`(@TempDir tempDir: Path) {
        val invalidSpec = tempDir.resolve("contract.contract")

        val specFilePath = invalidSpec.toAbsolutePath().toString()
        File(specFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(specFilePath)) }.returns(watcher)
        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION"))

        CommandLine(stubCommand).execute(specFilePath)
    }

    @Test
    fun `should run the stub with the specified pass-through url target`(@TempDir tempDir: File) {
        val contractPath = osAgnosticPath("${tempDir.path}/contract.$CONTRACT_EXTENSION")
        val contract = """
            Feature: Simple API
              Scenario: GET request
                When GET /
                Then status 200
        """.trimIndent()

        val file = File(contractPath).also { it.writeText(contract) }

        try {
            val feature = parseGherkinStringToFeature(contract)

            every { watchMaker.make(listOf(contractPath)) }.returns(watcher)

            val stubInfo = listOf(Pair(feature, emptyList<ScenarioStub>()))
            every {
                stubLoaderEngine.loadStubs(
                    listOf(contractPath).map { ContractPathData("", it) },
                    emptyList(),
                    any(),
                    false
                )
            }.returns(stubInfo)

            val host = "0.0.0.0"
            val port = 9000
            val certInfo = CertInfo()
            val strictMode = false
            val passThroughTargetBase = "http://passthroughTargetBase"

            every {
                httpStubEngine.runHTTPStub(
                    stubInfo,
                    endPointFromHostAndPort(host, port, certInfo.getHttpsCert()),
                    certInfo,
                    strictMode,
                    passThroughTargetBase,
                    httpClientFactory = any(),
                    workingDirectory = any(),
                    gracefulRestartTimeoutInMs = any(),
                    specToBaseUrlMap = any()
                )
            }.returns(
                mockk {
                    every { close() } returns Unit
                }
            )

            every { specmaticConfig.contractStubPaths() }.returns(arrayListOf(contractPath))

            val exitStatus = CommandLine(stubCommand).execute(
                "--passThroughTargetBase=$passThroughTargetBase",
                contractPath
            )
            assertThat(exitStatus).isZero()

            verify(exactly = 1) {
                httpStubEngine.runHTTPStub(
                    stubInfo,
                    endPointFromHostAndPort(host, port, certInfo.getHttpsCert()),
                    certInfo,
                    strictMode,
                    any(),
                    httpClientFactory = any(),
                    workingDirectory = any(),
                    gracefulRestartTimeoutInMs = any(),
                    specToBaseUrlMap = any()
                )
            }
        } finally {
            file.delete()
        }
    }

    @ParameterizedTest
    @CsvSource(
        "          ,      ,                      , http://0.0.0.0:9000",
        "localhost , 8080 ,                      , http://localhost:8080",
        "localhost ,      ,                      , http://localhost:9000",
        "          , 8080 ,                      , http://0.0.0.0:8080",
        "          ,      , http://0.0.0.0:3000  , http://0.0.0.0:3000",
        "localhost , 8080 , http://0.0.0.0:3000  , http://0.0.0.0:3000",
        "localhost ,      , https://0.0.0.0:3000 , https://0.0.0.0:3000",
        "          , 8080 , https://0.0.0.0:3000 , https://0.0.0.0:3000"
    )
    fun `should prioritise baseURL over passed through port and host args`(host: String?, port: String?, baseURL: String?, expected: String) {
        every { stubLoaderEngine.loadStubs(any(), any(), any(), any()) } returns emptyList()
        every { watchMaker.make(any()) } returns watcher
        every { specmaticConfig.contractStubPaths() } returns emptyList()
        every { specmaticConfig.contractStubPathData() } returns emptyList()
        every {
            httpStubEngine.runHTTPStub(any(), expected, any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockk { every { close() } returns Unit }

        val args = buildList {
            baseURL?.let { add("--baseUrl=$it") }
            host?.let { add("--host=$it") }
            port?.let { add("--port=$it") }
        }
        val exitStatus = CommandLine(stubCommand).execute(*args.toTypedArray())

        assertThat(exitStatus).isZero()
        verify(exactly = 1) {
            httpStubEngine.runHTTPStub(
                any(), expected, any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }
}
