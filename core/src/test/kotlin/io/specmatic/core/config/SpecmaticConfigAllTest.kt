package io.specmatic.core.config

import io.specmatic.core.Source
import io.specmatic.core.SourceProvider
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.loadSpecmaticConfig
import io.specmatic.core.pattern.ContractException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

internal class SpecmaticConfigAllTest {
    @Test
    fun `should return the version from the specmatic config`() {
        val config = """
           {"version": 2}
        """.trimIndent()

        assertThat(config.getVersion()).isEqualTo(SpecmaticConfigVersion.VERSION_2)
    }

    @Test
    fun `should return the version as 1 if it is not present`() {
        assertThat("{}".getVersion()).isEqualTo(SpecmaticConfigVersion.VERSION_1)
    }

    @Test
    fun `should return the version as 1 if it is null in the config`() {
        val config = """
               {"version": null}
            """.trimIndent()

        assertThat(config.getVersion()).isEqualTo(SpecmaticConfigVersion.VERSION_1)
    }

    @Test
    fun `should do nothing for invalid versions`() {
        val config = """
           {"version": 666}
        """.trimIndent()

        assertThat(config.getVersion()).isNull()
    }

    @CsvSource(
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_without_version.yaml",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_config_v1.yaml",
        "VERSION_2, ./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_without_version.json",
        "VERSION_1, ./src/test/resources/specmaticConfigFiles/specmatic_config_v1.json",
        "VERSION_2, ./src/test/resources/specmaticConfigFiles/specmatic_config_v2.json"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given SpecmaticConfigV1`(version: SpecmaticConfigVersion, configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
        assertThat(config.version).isEqualTo(version)
        assertThat(config.sources.size).isEqualTo(2)
        val expectedSources = listOf(
            Source(provider= SourceProvider.git, repository="https://contracts", branch = "1.0.1", test= listOf("com/petstore/1.yaml"), stub= listOf("com/petstore/payment.yaml")),
            Source(provider= SourceProvider.filesystem, test= listOf("com/petstore/1.yaml"), stub= listOf("com/petstore/payment.yaml", "com/petstore/order.yaml"), directory="contracts")
        )
        assertThat(config.sources).containsAll(expectedSources)
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_with_unsupported_version.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_config_with_unsupported_version.json"
    )
    @ParameterizedTest
    fun `should throw error when unsupported version is provided`(configFile: String) {
        val specmaticConfigWithInvalidVersion = File(configFile)
        val exception = assertThrows<ContractException> { specmaticConfigWithInvalidVersion.toSpecmaticConfig() }
        assertThat(exception.message).isEqualTo("Unsupported Specmatic config version")
    }
}