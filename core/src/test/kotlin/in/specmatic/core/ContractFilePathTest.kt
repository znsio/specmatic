package `in`.specmatic.core

import `in`.specmatic.Utils.readTextResource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ContractException

internal class ContractFilePathTest {
    @Test
    fun `reads a qontract file and instantiates a feature file from it`() {
        val contractPath = "random.$CONTRACT_EXTENSION"

        val actualFeature = ContractFileWithExports(pathTo(contractPath)).readFeatureForValue("")
        val expectedFeature = parseGherkinStringToFeature(readTextResource(contractPath), javaClass.classLoader.getResource(contractPath)?.file!!)

        assertThat(actualFeature).isEqualTo(expectedFeature)
    }

    @Test
    fun `throws an exception when the given path does not exist`() {
        assertThatThrownBy {
            ContractFileWithExports("doesnotexist.$CONTRACT_EXTENSION").readFeatureForValue("")
        }.isInstanceOf(ContractException::class.java)
    }

    fun pathTo(path: String): String {
        return javaClass.classLoader.getResource(path)?.file ?: throw Exception("Could not find path $path")
    }
}