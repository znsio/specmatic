package run.qontract.core.utilities

import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.git.SystemGit
import run.qontract.core.git.clone
import run.qontract.core.pattern.parsedJSON
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.XMLNode
import java.io.File

internal class UtilitiesTest {
    @Test
    fun `parsing multiline xml`() {
        val xml = """<line1>
<line2>data</line2>
</line1>
        """

        val xmlValue = XMLNode(xml)

        assertThat(xmlValue.nodes.size).isOne
        assertThat(xmlValue.toStringValue().trim()).isEqualTo("""<line1><line2>data</line2></line1>""")
    }

    @Test
    fun `contractFilePathsFrom sources with git repo`() {
        val sources = listOf(GitRepo("https://repo1", listOf(), listOf("a/1.qontract", "b/1.qontract", "c/1.qontract")))

        mockkStatic("run.qontract.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)

        mockkStatic("run.qontract.core.git.GitOperations")
        every { clone(any(), any()) }.returns(File("cloneDir"))

        val contractPaths = contractFilePathsFrom("/configFilePath", ".qontract") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
                ContractPathData("cloneDir", "cloneDir/a/1.qontract"),
                ContractPathData("cloneDir", "cloneDir/b/1.qontract"),
                ContractPathData("cloneDir", "cloneDir/c/1.qontract"),
        )
        assertThat(contractPaths == expectedContractPaths).isTrue
    }

    @Test
    fun `contractFilePathsFrom sources with mono repo`() {
        val configFilePath = "monorepo/configLoc/qontract.json"

        var monorepoContents = listOf(configFilePath, "monorepo/a/1.qontract", "monorepo/b/1.qontract", "monorepo/c/1.qontract");
        monorepoContents.forEach {
            File(it).parentFile.mkdirs()
            File(it).createNewFile()
        }

        File(configFilePath).printWriter().use { it.println("{\"sources\":[{\"provider\":\"git\",\"stub\":[\"..\\/a\\/1.qontract\",\"..\\/b\\/1.qontract\"],\"test\":[\"..\\/c\\/1.qontract\"]}]}") }

        mockkConstructor(SystemGit::class)
        every { anyConstructed<SystemGit>().gitRoot() }.returns("/path/to/monorepo")

        val currentPath = File(".").canonicalPath
        val testPaths = contractFilePathsFrom(configFilePath, ".qontract") { source -> source.testContracts }
        val stubPaths = contractFilePathsFrom(configFilePath, ".qontract") { source -> source.stubContracts }
        val expectedStubPaths = listOf(
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/a/1.qontract"),
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/b/1.qontract")
        )
        val expectedTestPaths = listOf(
            ContractPathData("/path/to/monorepo", "$currentPath/monorepo/c/1.qontract"),
        )

        assertThat(stubPaths == expectedStubPaths).isTrue
        assertThat(testPaths == expectedTestPaths).isTrue

        File("monorepo").deleteRecursively()
    }

    @Test
    fun `load sources with git repo`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.qontract\",\"b/1.qontract\",\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitRepo("https://repo1", listOf(), listOf("a/1.qontract", "b/1.qontract", "c/1.qontract")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with multiple git repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.qontract\",\"b/1.qontract\"]}," +
                "{\"provider\": \"git\",\"repository\": \"https://repo2\",\"stub\": [\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
                GitRepo("https://repo1", listOf(), listOf("a/1.qontract", "b/1.qontract")),
                GitRepo("https://repo2", listOf(), listOf("c/1.qontract"))
        )
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for stub-only qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"stub\": [\"a/1.qontract\",\"b/1.qontract\",\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf(), listOf("a/1.qontract", "b/1.qontract", "c/1.qontract")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for test-only qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"test\": [\"a/1.qontract\",\"b/1.qontract\",\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf("a/1.qontract", "b/1.qontract", "c/1.qontract"), listOf()))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with mono repo for tests and stubs in qontract`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"test\": [\"a/1.qontract\",\"b/1.qontract\"],\"stub\": [\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf("a/1.qontract", "b/1.qontract"), listOf("c/1.qontract")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with multiple mono repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"stub\": [\"a/1.qontract\",\"b/1.qontract\"]}," +
                "{\"provider\": \"git\",\"stub\": [\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf(), listOf("a/1.qontract", "b/1.qontract")),
                GitMonoRepo(listOf(), listOf("c/1.qontract")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with git and mono repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.qontract\",\"b/1.qontract\"]}," +
                "{\"provider\": \"git\",\"stub\": [\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
                GitRepo("https://repo1", listOf(), listOf("a/1.qontract", "b/1.qontract")),
                GitMonoRepo(listOf(), listOf("c/1.qontract"))
        )
        assertThat(sources == expectedSources).isTrue
    }

}
