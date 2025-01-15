package io.specmatic.core.config

import io.specmatic.core.SourceProvider
import io.specmatic.core.pattern.ContractException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

internal class VersionAwareConfigParserTest {
    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_without_version.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_without_version.json"
    )
    @ParameterizedTest
    fun `should default to version 1 when no version is provided`(configFile: String) {
        val specmaticConfig = File(configFile).toSpecmaticConfig()
        assertThat(specmaticConfig.version).isEqualTo(1)
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_with_unsupported_version.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_config_with_unsupported_version.json"
    )
    @ParameterizedTest
    fun `should throw error when unsupported version is provided`(configFile: String) {
        val specmaticConfigWithInvalidVersion = File(configFile)
        val exception = assertThrows<ContractException> { specmaticConfigWithInvalidVersion.toSpecmaticConfig() }
        assertThat(exception.message).isEqualTo("Unsupported Specmatic config version: 0")
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v1.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v1.json"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given version 1 config file`(configFile: String) {
        val specmaticConfig = File(configFile).toSpecmaticConfig()

        assertThat(specmaticConfig.version).isEqualTo(1)

        assertThat(specmaticConfig.sources.size).isEqualTo(2)
        assertThat(specmaticConfig.sources[0].provider).isEqualTo(SourceProvider.git)
        assertThat(specmaticConfig.sources[0].repository).isEqualTo("https://contracts")
        assertThat(specmaticConfig.sources[0].test).containsOnly("com/petstore/1.yaml")
        assertThat(specmaticConfig.sources[0].stub).containsOnly("com/petstore/payment.yaml")
        assertThat(specmaticConfig.sources[1].provider).isEqualTo(SourceProvider.filesystem)
        assertThat(specmaticConfig.sources[1].directory).isEqualTo("contracts")
        assertThat(specmaticConfig.sources[1].test).containsOnly("com/petstore/1.yaml")
        assertThat(specmaticConfig.sources[1].stub).containsOnly("com/petstore/payment.yaml", "com/petstore/order.yaml")
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.json"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given version 2 config file`(configFile: String) {
        val specmaticConfig = File(configFile).toSpecmaticConfig()

        assertThat(specmaticConfig.version).isEqualTo(2)

        assertThat(specmaticConfig.sources.size).isEqualTo(2)
        assertThat(specmaticConfig.sources[0].provider).isEqualTo(SourceProvider.git)
        assertThat(specmaticConfig.sources[0].repository).isEqualTo("https://contracts")
        assertThat(specmaticConfig.sources[0].branch).isEqualTo("1.0.1")
        assertThat(specmaticConfig.sources[0].test).containsOnly("product_search_bff_v4.yaml")
        assertThat(specmaticConfig.sources[0].stub).containsOnly("api_order_v3.yaml", "kafka.yaml")
        assertThat(specmaticConfig.sources[1].provider).isEqualTo(SourceProvider.filesystem)
        assertThat(specmaticConfig.sources[1].directory).isEqualTo("specmatic-order-contracts")
        assertThat(specmaticConfig.sources[1].test).containsOnly("product_search_bff_v4.yaml")
        assertThat(specmaticConfig.sources[1].stub).containsOnly("api_order_v3.yaml", "kafka.yaml")
    }
}