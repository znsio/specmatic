package utilities

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.DEFAULT_WORKING_DIRECTORY
import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.SourceProvider
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.git.checkout
import `in`.specmatic.core.git.clone
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.osAgnosticPath
import `in`.specmatic.stub.createStub
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.net.ServerSocket

private const val testSystemProperty = "THIS_PROPERTY_EXISTS"

internal class UtilitiesTest {
    @Test
    fun `parsing multiline xml`() {
        val xml = """<line1>
<line2>data</line2>
</line1>
        """

        val xmlValue = toXMLNode(xml)

        assertThat(xmlValue.childNodes.size).isOne
        assertThat(xmlValue.toStringLiteral().trim()).isEqualTo("""<line1><line2>data</line2></line1>""".trimIndent())
    }

    @Test
    fun `contractFilePathsFrom sources when contracts repo dir does not exist`() {
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        File(".spec").deleteRecursively()

        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))


        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "a/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "b/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION", "git", "https://repo1",   specificationPath = "c/1.spec"),
        )
        verify(exactly = 0) { checkout(any(), any()) }
        contractPathsAreEqual(contractPaths, expectedContractPaths)
    }

    @Test
    fun `contractFilePathsFrom sources with branch when contracts repo dir does not exist`() {
        val branchName = "featureBranch"
        val sources = listOf(GitRepo("https://repo1",
            branchName, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        File(".spec").deleteRecursively()

        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)

        mockkStatic("in.specmatic.core.git.GitOperations")
        val repositoryDirectory = File(".spec/repos/repo1")
        every { clone(File(".spec/repos"), any()) }.returns(repositoryDirectory)
        every { checkout(repositoryDirectory, branchName) }.returns(Unit)

        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION", "git", "https://repo1", "featureBranch", "a/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION", "git", "https://repo1", "featureBranch", "b/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION", "git", "https://repo1", "featureBranch", "c/1.spec"),
        )
        verify(exactly = 1) { checkout(repositoryDirectory, branchName) }
        compare(contractPaths, expectedContractPaths)
    }

    private fun compare(
        contractPaths: List<ContractPathData>,
        expectedContractPaths: List<ContractPathData>
    ) {
        assertThat(osAgnosticPaths(contractPaths)).isEqualTo(osAgnosticPaths(expectedContractPaths))
    }

    @Test
    fun `contractFilePathsFrom sources when contracts repo dir exists and is clean`() {
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        File(".spec").deleteRecursively()
        File(".spec/repos/repo1").mkdirs()


        val mockGitCommand = mockk<GitCommand>()
        every { mockGitCommand.fetch() }.returns("")
        every { mockGitCommand.revisionsBehindCount() }.returns(0)
        every { mockGitCommand.statusPorcelain() }.returns("")
        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)
        every { getSystemGitWithAuth(any()) }.returns(mockGitCommand)

        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
                ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "a/1.spec"),
                ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "b/1.spec"),
                ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "c/1.spec"),
        )
        contractPathsAreEqual(contractPaths, expectedContractPaths)
    }

    private fun contractPathsAreEqual(
        contractPaths: List<ContractPathData>,
        expectedContractPaths: List<ContractPathData>
    ) {
        assertThat(osAgnosticPaths(contractPaths)).isEqualTo(osAgnosticPaths(expectedContractPaths))
    }

    private fun osAgnosticPaths(contractPaths: List<ContractPathData>): List<ContractPathData> {
        return contractPaths.map {
            it.copy(
                baseDir = removeDrive(osAgnosticPath(it.baseDir)),
                path = removeDrive(osAgnosticPath(it.path)),
                specificationPath = it.specificationPath?.let { osAgnosticPath(it) }
            )
        }
    }

    private fun removeDrive(path: String): String {
        return path.replace(Regex("^.:"), "")
    }


    @Test
    fun `contractFilePathsFrom sources when contracts repo dir exists and is not clean`() {
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        File(".spec").deleteRecursively()
        File(".spec/repos/repo1").mkdirs()

        val mockGitCommand = mockk<GitCommand>()
        every { mockGitCommand.fetch() }.returns("")
        every { mockGitCommand.revisionsBehindCount() }.returns(0)
        every { mockGitCommand.statusPorcelain() }.returns("someDir/someFile")
        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)
        every { getSystemGitWithAuth(any()) }.returns(mockGitCommand)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))


        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "a/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "b/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "c/1.spec"),
        )
        contractPathsAreEqual(contractPaths, expectedContractPaths)
    }

    @Test
    fun `contractFilePathsFrom sources when contracts repo dir exists and is behind remote`() {
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        File(".spec").deleteRecursively()
        File(".spec/repos/repo1").mkdirs()

        val mockGitCommand = mockk<GitCommand>()
        every { mockGitCommand.statusPorcelain() }.returns("")
        every { mockGitCommand.fetch() }.returns("changes")
        every { mockGitCommand.revisionsBehindCount() }.returns(1)
        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)
        every { getSystemGitWithAuth(any()) }.returns(mockGitCommand)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))


        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "a/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "b/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "c/1.spec"),
        )
        contractPathsAreEqual(contractPaths, expectedContractPaths)
    }

    @Test
    fun `contractFilePathsFrom sources with mono repo`() {
        val configFilePath = "monorepo/configLoc/specmatic.json"

        val monorepoContents = listOf(configFilePath, "monorepo/a/1.$CONTRACT_EXTENSION", "monorepo/b/1.$CONTRACT_EXTENSION", "monorepo/c/1.$CONTRACT_EXTENSION")
        monorepoContents.forEach {
            File(it).parentFile.mkdirs()
            File(it).createNewFile()
        }

        File(configFilePath).printWriter().use { it.println(
            """
            {"sources":[
                {"provider":"git",
                    "stub":["../a/1.$CONTRACT_EXTENSION","../b/1.$CONTRACT_EXTENSION"],
                    "test":["../c/1.$CONTRACT_EXTENSION"]}]
                }
            """.trimIndent()
        ) }

        mockkConstructor(SystemGit::class)
        every { anyConstructed<SystemGit>().gitRoot() }.returns("/path/to/monorepo")

        val currentPath = File(".").canonicalPath
        val testPaths = contractFilePathsFrom(configFilePath, ".$CONTRACT_EXTENSION") { source -> source.testContracts }
        val stubPaths = contractFilePathsFrom(configFilePath, ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedStubPaths = listOf(
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/a/1.$CONTRACT_EXTENSION", provider = SourceProvider.git.toString(), specificationPath = "../a/1.spec"),
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/b/1.$CONTRACT_EXTENSION", provider = SourceProvider.git.toString(), specificationPath = "../b/1.spec")
        )
        val expectedTestPaths = listOf(
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/c/1.$CONTRACT_EXTENSION", provider=SourceProvider.git.toString(), specificationPath = "../c/1.spec"),
        )

        contractPathsAreEqual(stubPaths, expectedStubPaths)
        contractPathsAreEqual(testPaths, expectedTestPaths)

        File("monorepo").deleteRecursively()
    }

    @Test
    fun `load sources with git repo`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\",\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with multiple git repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"]}," +
                "{\"provider\": \"git\",\"repository\": \"https://repo2\",\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
                GitRepo("https://repo1",null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()),
                GitRepo("https://repo2",null, listOf(), listOf("c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString())
        )
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for stub-only qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\",\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for test-only qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"test\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\",\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), listOf(), SourceProvider.git.toString()))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for tests and stubs in qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"test\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"],\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION"), listOf("c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with multiple mono repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"]}," +
                "{\"provider\": \"git\",\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
            GitMonoRepo(listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()),
                GitMonoRepo(listOf(), listOf("c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString())
        )
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with git and mono repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"]}," +
                "{\"provider\": \"git\",\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
                GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()),
                GitMonoRepo(listOf(), listOf("c/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString())
        )
        assertThat(sources == expectedSources).isTrue
    }

    @Nested
    inner class ReadingEnvironmentVariableOrPropertyConfiguration {
        @Test
        fun `read property when environment variable does not exist`() {
            System.setProperty(testSystemProperty, "true")
            assertThat(readEnvVarOrProperty("THIS_ENV_VAR_DOES_NOT_EXIST", testSystemProperty)).isEqualTo("true")
        }


        @Test
        fun `read environment variable instead of property when the environment variable exists`() {
            System.setProperty(testSystemProperty, "true")
            val (
                environmentVariableName,
                environmentVariableValue
            ) = System.getenv().entries.first { it.value != "true" }

            assertThat(readEnvVarOrProperty(environmentVariableName, testSystemProperty)).isEqualTo(environmentVariableValue)
        }
    }

    @Test
    fun `should load sources from local filesystem using current directory implicitly`() {
        val specmaticJSON = File("./specmatic.json")
        specmaticJSON.createNewFile()

        val specFile = File("random.yaml")
        specFile.createNewFile()

        try {
            specmaticJSON.writeText("""
                {
                    "sources": [
                        {
                            "provider": "filesystem",
                            "stub": ["random.yaml"]
                        }
                    ]
                }
            """.trimIndent())

            specFile.writeText("""
                openapi: 3.0.1
                info:
                  title: Random
                  version: "1"
                paths:
                  /random:
                    post:
                      summary: Random
                      parameters: []
                      requestBody:
                        content:
                          application/json:
                            schema:
                              required:
                              - id
                              properties:
                                id:
                                  type: number
                      responses:
                        "200":
                          description: Random
                          content:
                            text/plain:
                              schema:
                                type: string
            """.trimIndent())

            createStub("localhost", 9000, timeoutMillis = 0).use { stub ->
                val response = stub.client.execute(HttpRequest("POST", "/random", body = parsedJSONObject("""{"id": 1}""")))
                assertThat(response.status).isEqualTo(200)
            }
        } finally {
            specFile.delete()
            specmaticJSON.delete()
        }
    }

    @Test
    fun `should load sources from the web`() {
        val specSourcePort = ServerSocket(0).use { it.localPort }

        val spec = """
                openapi: 3.0.1
                info:
                  title: Random
                  version: "1"
                paths:
                  /random:
                    post:
                      summary: Random
                      parameters: []
                      requestBody:
                        content:
                          application/json:
                            schema:
                              required:
                              - id
                              properties:
                                id:
                                  type: number
                      responses:
                        "200":
                          description: Random
                          content:
                            text/plain:
                              schema:
                                type: string
            """.trimIndent()

        val server = embeddedServer(Netty, port = specSourcePort) {
            routing {
                get("/random.yaml") {
                    call.respondText(spec)
                }
            }
        }

        server.start(wait = false)

        val specmaticJSON = File("./specmatic.json")

        try {
            specmaticJSON.createNewFile()

            specmaticJSON.writeText("""
                {
                    "sources": [
                        {
                            "provider": "web",
                            "stub": ["http://localhost:$specSourcePort/random.yaml"]
                        }
                    ]
                }
            """.trimIndent())

            val stubPort = ServerSocket(0).use { it.localPort }

            createStub("localhost", stubPort, timeoutMillis = 0).use { stub ->
                val response = stub.client.execute(HttpRequest("POST", "/random", body = parsedJSONObject("""{"id": 1}""")))
                assertThat(response.status).isEqualTo(200)
            }

        } finally {
            if(specmaticJSON.exists())
                specmaticJSON.delete()

            File(".specmatic/web/localhost/random.yaml").delete()

            server.stop(0, 0)
        }
    }

    @Test
    fun `should load sources from local filesystem using the specified directory directory relative to current directory`() {
        val specmaticJSON = File("./specmatic.json")
        specmaticJSON.createNewFile()

        val specDir = File("specifications")
        specDir.mkdirs()

        val specFile = specDir.resolve("random.yaml")
        specFile.createNewFile()

        try {
            specmaticJSON.writeText("""
                {
                    "sources": [
                        {
                            "provider": "filesystem",
                            "directory": "${specDir.name}",
                            "stub": ["random.yaml"]
                        }
                    ]
                }
            """.trimIndent())

            specFile.writeText("""
                openapi: 3.0.1
                info:
                  title: Random
                  version: "1"
                paths:
                  /random:
                    post:
                      summary: Random
                      parameters: []
                      requestBody:
                        content:
                          application/json:
                            schema:
                              required:
                              - id
                              properties:
                                id:
                                  type: number
                      responses:
                        "200":
                          description: Random
                          content:
                            text/plain:
                              schema:
                                type: string
            """.trimIndent())

            val results = createStub("localhost", 9000, timeoutMillis = 0).use { stub ->
                loadSources(specmaticJSON.path).flatMap { source ->
                    source.testContracts
                }.map { specPath ->
                    OpenApiSpecification.fromFile(specPath).toFeature().executeTests(stub.client)
                }
            }

            assertThat(results.all { it.success() }).isTrue
        } finally {
            specDir.deleteRecursively()
            specmaticJSON.delete()
        }
    }

    private fun deleteGitIgnoreFile(){
        File(".gitignore").delete()
    }

    private fun createEmptyGitIgnoreFile(){
        File(".gitignore").createNewFile()
    }

    @AfterEach
    fun tearDownAfterEach() {
        deleteGitIgnoreFile()
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun teardown() {
            System.clearProperty(testSystemProperty)
        }
    }
}
