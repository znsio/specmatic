package application

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus
import com.ninjasquad.springmockk.MockkBean
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.core.LEGACY_CONTRACT_EXTENSION
import `in`.specmatic.core.parseGherkinStringToFeature
import `in`.specmatic.core.utilities.ContractPathData
import `in`.specmatic.core.utilities.StubServerWatcher
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.stub.HttpClientFactory
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
    lateinit var watcher: StubServerWatcher

    @MockkBean
    lateinit var httpStubEngine: HTTPStubEngine

    @MockkBean
    lateinit var stubLoaderEngine: StubLoaderEngine

    @Autowired
    lateinit var stubCommand: StubCommand

    @BeforeEach
    fun `clean up stub command`() {
        stubCommand.contractPaths = arrayListOf()
        stubCommand.specmaticConfigPath = null
    }

    @Test
    fun `when contract files are not given it should load from specmatic config`() {
        every { specmaticConfig.contractStubPathData() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION").map { ContractPathData("", it) })

        CommandLine(stubCommand, factory).execute()

        verify(exactly = 1) { specmaticConfig.contractStubPathData() }
    }

    @Test
    fun `when contract files are given it should not load from specmatic config`() {
        every { specmaticConfig.contractStubPathData() }.returns(arrayListOf("/config/path/to/contract.$CONTRACT_EXTENSION").map { ContractPathData("", it) })

        CommandLine(stubCommand, factory).execute("/parameter/path/to/contract.$CONTRACT_EXTENSION")

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
                    emptyList()
                )
            }.returns(stubInfo)

            val host = "0.0.0.0"
            val port = 9000
            val certInfo = CertInfo()
            val strictMode = false

            every {
                httpStubEngine.runHTTPStub(
                    stubInfo,
                    host,
                    port,
                    certInfo,
                    strictMode,
                    any(),
                    httpClientFactory = any(),
                    workingDirectory = any()
                )
            }.returns(null)

            every { specmaticConfig.contractStubPaths() }.returns(arrayListOf(contractPath))
            every { fileOperations.isFile(contractPath) }.returns(true)
            every { fileOperations.extensionIsNot(contractPath, CONTRACT_EXTENSIONS) }.returns(false)

            val exitStatus = CommandLine(stubCommand, factory).execute(contractPath)
            assertThat(exitStatus).isZero()

            verify(exactly = 1) {
                httpStubEngine.runHTTPStub(
                    any(),
                    any(),
                    any(),
                    certInfo,
                    strictMode,
                    any(),
                    httpClientFactory = any(),
                    workingDirectory = any(),
                )
            }
        } finally {
            file.delete()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = [CONTRACT_EXTENSION, LEGACY_CONTRACT_EXTENSION])
    fun `when a contract with the correct extension is given it should be loaded`(extension: String, @TempDir tempDir: Path) {
        val validSpec = tempDir.resolve("contract.$extension")

        val specFilePath = validSpec.toAbsolutePath().toString()
        File(specFilePath).writeText("""
            Feature: Is a dummy feature
        """.trimIndent())

        every { watchMaker.make(listOf(specFilePath)) }.returns(watcher)
        every { specmaticConfig.contractStubPaths() }.returns(arrayListOf("/config/path/to/contract.$extension"))
        every { fileOperations.isFile(specFilePath) }.returns(true)
        every { fileOperations.extensionIsNot(specFilePath, CONTRACT_EXTENSIONS) }.returns(false)

        val execute = CommandLine(stubCommand, factory).execute(specFilePath)

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
        every { fileOperations.isFile(specFilePath) }.returns(true)
        every { fileOperations.extensionIsNot(specFilePath, CONTRACT_EXTENSIONS) }.returns(true)

        CommandLine(stubCommand, factory).execute(specFilePath)
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
                    emptyList()
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
                    host,
                    port,
                    certInfo,
                    strictMode,
                    passThroughTargetBase,
                    httpClientFactory = any(),
                    workingDirectory = any(),
                )
            }.returns(null)

            every { specmaticConfig.contractStubPaths() }.returns(arrayListOf(contractPath))
            every { fileOperations.isFile(contractPath) }.returns(true)
            every { fileOperations.extensionIsNot(contractPath, CONTRACT_EXTENSIONS) }.returns(false)

            val exitStatus = CommandLine(stubCommand, factory).execute(
                "--passThroughTargetBase=$passThroughTargetBase",
                contractPath
            )
            assertThat(exitStatus).isZero()

            verify(exactly = 1) {
                httpStubEngine.runHTTPStub(
                    stubInfo,
                    host,
                    any(),
                    certInfo,
                    strictMode,
                    any(),
                    httpClientFactory = any(),
                    workingDirectory = any(),
                )
            }
        } finally {
            file.delete()
        }
    }
}
