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
        val sources = listOf(GitMonoRepo(listOf(), listOf("../a/1.qontract", "../b/1.qontract", "../c/1.qontract")))

        mockkStatic("run.qontract.core.utilities.Utilities")
        every { loadSources(".") }.returns(sources)

        mockkConstructor(SystemGit::class)
        every { anyConstructed<SystemGit>().repoName() }.returns("monorepo")

        every { anyConstructed<SystemGit>().relativeGitPath("../a/1.qontract") }.returns(Pair(SystemGit(), "a/1.qontract"))
        every { anyConstructed<SystemGit>().relativeGitPath("../b/1.qontract") }.returns(Pair(SystemGit(), "b/1.qontract"))
        every { anyConstructed<SystemGit>().relativeGitPath("../c/1.qontract") }.returns(Pair(SystemGit(), "c/1.qontract"))

        sources[0].stubContracts.forEach {
            File(it).parentFile.mkdirs()
            File(it).createNewFile()
        }

        val contractPaths = contractFilePathsFrom(".", ".qontract") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
                ContractPathData(".qontract/repos/monorepo", ".qontract/repos/monorepo/a/1.qontract"),
                ContractPathData(".qontract/repos/monorepo", ".qontract/repos/monorepo/b/1.qontract"),
                ContractPathData(".qontract/repos/monorepo", ".qontract/repos/monorepo/c/1.qontract"),
        )

        assertThat(contractPaths == expectedContractPaths).isTrue

        listOf("../a", "../b", "../c").forEach { File(it).deleteRecursively() }
        File(".qontract").deleteRecursively()
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
    fun `load sources with mono repo`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"stub\": [\"a/1.qontract\",\"b/1.qontract\",\"c/1.qontract\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(GitMonoRepo(listOf(), listOf("a/1.qontract", "b/1.qontract", "c/1.qontract")))
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
