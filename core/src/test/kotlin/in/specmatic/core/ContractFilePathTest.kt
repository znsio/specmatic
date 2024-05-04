package `in`.specmatic.core

import `in`.specmatic.Utils.readTextResource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.osAgnosticPath

internal class ContractFilePathTest {
    @Test
    fun `reads a spec file and instantiates a feature file from it`() {
        val contractPath = "random.$CONTRACT_EXTENSION"

        val actualFeature = ContractFileWithExports(pathTo(contractPath)).readFeatureForValue("")
        val expectedFeature = parseGherkinStringToFeature(readTextResource(contractPath), osAgnosticPath( javaClass.classLoader.getResource(contractPath)?.file!!))

        assertThat(actualFeature).isEqualTo(expectedFeature)
    }

    @Test
    fun `throws an exception when the given path does not exist`() {
        assertThatThrownBy {
            ContractFileWithExports("doesNotExist.$CONTRACT_EXTENSION").readFeatureForValue("")
        }.isInstanceOf(ContractException::class.java)
    }

    private fun pathTo(path: String): String {
        return javaClass.classLoader.getResource(path)?.file ?: throw Exception("Could not find path $path")
    }
}