package utilities

import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.git.clone
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.toXMLNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
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
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))
        File(".spec").deleteRecursively()

        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))


        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION"),
        )
        assertThat(contractPaths == expectedContractPaths).isTrue
    }

    @Test
    fun `contractFilePathsFrom sources when contracts repo dir exists and is clean`() {
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))
        File(".spec").deleteRecursively()
        File(".spec/repos/repo1").mkdirs()


        val mockGitCommand = mockk<GitCommand>()
        every { mockGitCommand.fetch() }.returns("")
        every { mockGitCommand.revisionsBehindCount() }.returns(0)
        every { mockGitCommand.statusPorcelain() }.returns("")
        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)
        every { getSystemGit(any()) }.returns(mockGitCommand)

        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
                ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION"),
                ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION"),
                ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION"),
        )
        assertThat(contractPaths == expectedContractPaths).isTrue
    }

    @Test
    fun `contractFilePathsFrom sources when contracts repo dir exists and is not clean`() {
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))
        File(".spec").deleteRecursively()
        File(".spec/repos/repo1").mkdirs()

        val mockGitCommand = mockk<GitCommand>()
        every { mockGitCommand.fetch() }.returns("")
        every { mockGitCommand.revisionsBehindCount() }.returns(0)
        every { mockGitCommand.statusPorcelain() }.returns("someDir/someFile")
        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)
        every { getSystemGit(any()) }.returns(mockGitCommand)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))


        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION"),
        )
        assertThat(contractPaths == expectedContractPaths).isTrue
    }

    @Test
    fun `contractFilePathsFrom sources when contracts repo dir exists and is behind remote`() {
        val sources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))
        File(".spec").deleteRecursively()
        File(".spec/repos/repo1").mkdirs()

        val mockGitCommand = mockk<GitCommand>()
        every { mockGitCommand.fetch() }.returns("changes")
        every { mockGitCommand.revisionsBehindCount() }.returns(1)
        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)
        every { getSystemGit(any()) }.returns(mockGitCommand)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(File(".spec/repos"), any()) }.returns(File(".spec/repos/repo1"))


        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/a/1.$CONTRACT_EXTENSION"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/b/1.$CONTRACT_EXTENSION"),
            ContractPathData(".spec/repos/repo1", ".spec/repos/repo1/c/1.$CONTRACT_EXTENSION"),
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
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/a/1.$CONTRACT_EXTENSION"),
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/b/1.$CONTRACT_EXTENSION")
        )
        val expectedTestPaths = listOf(
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/c/1.$CONTRACT_EXTENSION"),
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
        val expectedSources = listOf(GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with multiple git repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"]}," +
                "{\"provider\": \"git\",\"repository\": \"https://repo2\",\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
                GitRepo("https://repo1",null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION")),
                GitRepo("https://repo2",null, listOf(), listOf("c/1.$CONTRACT_EXTENSION"))
        )
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for stub-only qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\",\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for test-only qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"test\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\",\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION"), listOf()))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for tests and stubs in qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"test\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"],\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION"), listOf("c/1.$CONTRACT_EXTENSION")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with multiple mono repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"]}," +
                "{\"provider\": \"git\",\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
            GitMonoRepo(listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION")),
                GitMonoRepo(listOf(), listOf("c/1.$CONTRACT_EXTENSION"))
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
                GitRepo("https://repo1", null, listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION")),
                GitMonoRepo(listOf(), listOf("c/1.$CONTRACT_EXTENSION"))
        )
        assertThat(sources == expectedSources).isTrue
    }

}
