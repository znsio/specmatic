package utilities

import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.git.clone
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.toXMLNode
import java.io.File
import org.junit.Assert.*
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import io.mockk.*
import org.w3c.dom.Document
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Transformer
import javax.xml.transform.OutputKeys

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
    fun `contractFilePathsFrom sources with git repo`() {
        val sources = listOf(GitRepo("https://repo1", listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))

        mockkStatic("in.specmatic.core.utilities.Utilities")
        every { loadSources("/configFilePath") }.returns(sources)

        mockkStatic("in.specmatic.core.git.GitOperations")
        every { clone(any(), any()) }.returns(File("cloneDir"))

        val contractPaths = contractFilePathsFrom("/configFilePath", ".$CONTRACT_EXTENSION") { source -> source.stubContracts }
        val expectedContractPaths = listOf(
                ContractPathData("cloneDir", "cloneDir/a/1.$CONTRACT_EXTENSION"),
                ContractPathData("cloneDir", "cloneDir/b/1.$CONTRACT_EXTENSION"),
                ContractPathData("cloneDir", "cloneDir/c/1.$CONTRACT_EXTENSION"),
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
        val expectedSources = listOf(GitRepo("https://repo1", listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION", "c/1.$CONTRACT_EXTENSION")))
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun `load sources with multiple git repos`() {
        val qontractJson = "{\"sources\": [{\"provider\": \"git\",\"repository\": \"https://repo1\",\"stub\": [\"a/1.$CONTRACT_EXTENSION\",\"b/1.$CONTRACT_EXTENSION\"]}," +
                "{\"provider\": \"git\",\"repository\": \"https://repo2\",\"stub\": [\"c/1.$CONTRACT_EXTENSION\"]}]}"
        val configJson = parsedJSON(qontractJson) as JSONObjectValue
        val sources = loadSources(configJson)
        val expectedSources = listOf(
                GitRepo("https://repo1", listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION")),
                GitRepo("https://repo2", listOf(), listOf("c/1.$CONTRACT_EXTENSION"))
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
                GitRepo("https://repo1", listOf(), listOf("a/1.$CONTRACT_EXTENSION", "b/1.$CONTRACT_EXTENSION")),
                GitMonoRepo(listOf(), listOf("c/1.$CONTRACT_EXTENSION"))
        )
        assertThat(sources == expectedSources).isTrue
    }

    @Test
    fun testXmlToString() {
        val mockTransformerFactory = mockk<TransformerFactory>()
        val mockTransformer = mockk<Transformer>()
        val mockConfigureTransformer = mockk<(Transformer) -> Unit>()

        every { mockTransformerFactory.newTransformer() } returns mockTransformer

        val document = createMockDocument()
        val rootElement = document.createElement("root")
        document.appendChild(rootElement)

        val domSource = DOMSource(rootElement)

        every { mockTransformer.setOutputProperty(any(), any()) } just Runs
        every { mockTransformer.transform(domSource, any()) } just Runs
        every { mockConfigureTransformer.invoke(any()) } just Runs

        val output = xmlToString(domSource, mockConfigureTransformer)

        assertEquals("<root/>", output)
    }

    private fun createMockDocument(): Document {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        return documentBuilder.newDocument()
    }
    private fun xmlToString(domSource: DOMSource, configureTransformer: (Transformer) -> Unit = {}): String {
        val writer = StringWriter()
        val result = StreamResult(writer)
        val tf = TransformerFactory.newInstance()
        val transformer = tf.newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        configureTransformer(transformer)
        transformer.transform(domSource, result)
        return writer.toString()
    }

}
