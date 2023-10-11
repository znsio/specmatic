package utilities

import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.DEFAULT_WORKING_DIRECTORY
import `in`.specmatic.core.SourceProvider
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.git.checkout
import `in`.specmatic.core.git.clone
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.toXMLNode
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

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
        assertThat(contractPaths == expectedContractPaths).isTrue
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
        assertThat(contractPaths == expectedContractPaths).isTrue
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
        assertThat(contractPaths == expectedContractPaths).isTrue
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
        assertThat(contractPaths == expectedContractPaths).isTrue
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
        every { getSystemGit(any()) }.returns(mockGitCommand)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))


        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "a/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "b/1.spec"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION", "git", "https://repo1",  specificationPath = "c/1.spec"),
        )
        assertThat(contractPaths == expectedContractPaths).isTrue
    }

    @Test
    fun `contractFilePathsFrom sources with mono repo`() {
        val configFilePath = "monorepo/configLoc/specmatic.json"

        var monorepoContents = listOf(configFilePath, "monorepo/a/1.$CONTRACT_EXTENSION", "monorepo/b/1.$CONTRACT_EXTENSION", "monorepo/c/1.$CONTRACT_EXTENSION")
        monorepoContents.forEach {
            File(it).parentFile.mkdirs()
            File(it).createNewFile()
        }

        File(configFilePath).printWriter().use { it.println("{\"sources\":[{\"provider\":\"git\",\"stub\":[\"..\\/a\\/1.$CONTRACT_EXTENSION\",\"..\\/b\\/1.$CONTRACT_EXTENSION\"],\"test\":[\"..\\/c\\/1.$CONTRACT_EXTENSION\"]}]}") }

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

        assertThat(stubPaths == expectedStubPaths).isTrue
        assertThat(testPaths == expectedTestPaths).isTrue

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
    inner class SpecmaticFolderIsIgnoredInGitRepoTest {
        @Nested
        inner class GitIgnoreFileDoesNotExistTest {
            @Test
            fun `should create gitignore file add specmatic folder to it when contracts repo dir does not exist `() {
                val branchName = "featureBranch"
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        branchName, listOf(), listOf("a/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                deleteGitIgnoreFile()

                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)

                mockkStatic("in.specmatic.core.git.GitOperations")
                val repositoryDirectory = File(".spec/repos/repo1")
                every { clone(File(".spec/repos"), any()) }.returns(repositoryDirectory)
                every { checkout(repositoryDirectory, branchName) }.returns(Unit)

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }

            @Test
            fun `should create gitignore file add specmatic folder to it when contracts repo dir exists and is clean`() {
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        null,
                        listOf(),
                        listOf("a/1.$CONTRACT_EXTENSION"),
                        SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                File(".spec/repos/repo1").mkdirs()
                deleteGitIgnoreFile()

                val mockGitCommand = mockk<GitCommand>()
                every { mockGitCommand.fetch() }.returns("")
                every { mockGitCommand.revisionsBehindCount() }.returns(0)
                every { mockGitCommand.statusPorcelain() }.returns("")
                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)
                every { mockGitCommand.checkIgnore(any()) }.returns("")
                every { getSystemGit(any()) }.returns(mockGitCommand)

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }

            @Test
            fun `should create gitignore file and add specmatic folder to it when contracts repo dir exists and is not clean`() {
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        null,
                        listOf(),
                        listOf("a/1.$CONTRACT_EXTENSION"),
                        SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                File(".spec/repos/repo1").mkdirs()
                deleteGitIgnoreFile()

                val mockGitCommand = mockk<GitCommand>()
                every { mockGitCommand.fetch() }.returns("")
                every { mockGitCommand.revisionsBehindCount() }.returns(0)
                every { mockGitCommand.statusPorcelain() }.returns("someDir/someFile")
                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)
                every { mockGitCommand.checkIgnore(any()) }.returns("")
                every { getSystemGitWithAuth(any()) }.returns(mockGitCommand)
                every { getSystemGit(any()) }.returns(mockGitCommand)

                mockkStatic("in.specmatic.core.git.GitOperations")
                every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }

            @Test
            fun `should create gitignore file and add specmatic folder to it when contracts repo dir exists and is behind remote`() {
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        null,
                        listOf(),
                        listOf("a/1.$CONTRACT_EXTENSION"),
                        SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                File(".spec/repos/repo1").mkdirs()
                deleteGitIgnoreFile()

                val mockGitCommand = mockk<GitCommand>()
                every { mockGitCommand.fetch() }.returns("")
                every { mockGitCommand.revisionsBehindCount() }.returns(1)
                every { mockGitCommand.statusPorcelain() }.returns("")
                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)
                every { mockGitCommand.checkIgnore(any()) }.returns("")
                every { getSystemGitWithAuth(any()) }.returns(mockGitCommand)
                every { getSystemGit(any()) }.returns(mockGitCommand)

                mockkStatic("in.specmatic.core.git.GitOperations")
                every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }
        }

        @Nested
        inner class GitIgnoreFileExistsTest {
            @Test
            fun `should add specmatic folder to gitignore file if it exists when contracts repo dir does not exist `() {
                val branchName = "featureBranch"
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        branchName, listOf(), listOf("a/1.$CONTRACT_EXTENSION"), SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                deleteGitIgnoreFile()
                createEmptyGitIgnoreFile()

                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)

                mockkStatic("in.specmatic.core.git.GitOperations")
                val repositoryDirectory = File(".spec/repos/repo1")
                every { clone(File(".spec/repos"), any()) }.returns(repositoryDirectory)
                every { checkout(repositoryDirectory, branchName) }.returns(Unit)

                val mockGitCommand = mockk<GitCommand>()
                every { mockGitCommand.checkIgnore(any()) }.returns("")

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }

            @Test
            fun `should add specmatic folder to gitignore file if it exists when contracts repo dir exists and is clean`() {
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        null,
                        listOf(),
                        listOf("a/1.$CONTRACT_EXTENSION"),
                        SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                File(".spec/repos/repo1").mkdirs()
                createEmptyGitIgnoreFile()


                val mockGitCommand = mockk<GitCommand>()
                every { mockGitCommand.fetch() }.returns("")
                every { mockGitCommand.revisionsBehindCount() }.returns(0)
                every { mockGitCommand.statusPorcelain() }.returns("")
                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)
                every { mockGitCommand.checkIgnore(any()) }.returns("")
                every { getSystemGit(any()) }.returns(mockGitCommand)

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }

            @Test
            fun `should add specmatic folder to gitignore file if it exists when contracts repo dir exists and is not clean`() {
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        null,
                        listOf(),
                        listOf("a/1.$CONTRACT_EXTENSION"),
                        SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                File(".spec/repos/repo1").mkdirs()
                createEmptyGitIgnoreFile()

                val mockGitCommand = mockk<GitCommand>()
                every { mockGitCommand.fetch() }.returns("")
                every { mockGitCommand.revisionsBehindCount() }.returns(0)
                every { mockGitCommand.statusPorcelain() }.returns("someDir/someFile")
                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)
                every { mockGitCommand.checkIgnore(any()) }.returns("")
                every { getSystemGitWithAuth(any()) }.returns(mockGitCommand)
                every { getSystemGit(any()) }.returns(mockGitCommand)

                mockkStatic("in.specmatic.core.git.GitOperations")
                every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }

            @Test
            fun `should add specmatic folder to gitignore file if it exists when contracts repo dir exists and is behind remote`() {
                val sources = listOf(
                    GitRepo(
                        "https://repo1",
                        null,
                        listOf(),
                        listOf("a/1.$CONTRACT_EXTENSION"),
                        SourceProvider.git.toString()
                    )
                )
                File(".spec").deleteRecursively()
                File(".spec/repos/repo1").mkdirs()
                createEmptyGitIgnoreFile()

                val mockGitCommand = mockk<GitCommand>()
                every { mockGitCommand.fetch() }.returns("")
                every { mockGitCommand.revisionsBehindCount() }.returns(1)
                every { mockGitCommand.statusPorcelain() }.returns("")
                mockkStatic("in.specmatic.core.utilities.Utilities")
                every { loadSources("/configFilePath") }.returns(sources)
                every { mockGitCommand.checkIgnore(any()) }.returns("")
                every { getSystemGitWithAuth(any()) }.returns(mockGitCommand)
                every { getSystemGit(any()) }.returns(mockGitCommand)

                mockkStatic("in.specmatic.core.git.GitOperations")
                every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))

                val contractPaths =
                    contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
                assertThat(contractPaths.size).isEqualTo(1)
                assertSpecmaticFolderIsIgnored()
            }
        }
    }

    private fun assertSpecmaticFolderIsIgnored() {
        val gitIgnoreFile = File(".gitignore")
        val ignored =  gitIgnoreFile.readLines().any {
            it.trim().contains(DEFAULT_WORKING_DIRECTORY)
        }
        assertThat(ignored).isTrue
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
}
