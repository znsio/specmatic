package io.specmatic.core.config

import io.specmatic.core.SourceProvider
import io.specmatic.core.pattern.ContractException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class VersionAwareConfigParserTest {
    @Test
    fun `should default to version 1 when no version is provided`() {
        val specmaticConfig =
            File("./src/test/resources/specmaticConfigFiles/specmatic_without_version.yaml").toSpecmaticConfig()
        assertThat(specmaticConfig.version).isEqualTo(1)
    }

    @Test
    fun `should throw error when unsupported version is provided`() {
        val specmaticConfigWithInvalidVersion =
            File("./src/test/resources/specmaticConfigFiles/specmatic_without_unsupported_version.yaml")
        val exception = assertThrows<ContractException> { specmaticConfigWithInvalidVersion.toSpecmaticConfig() }
        assertThat(exception.message).isEqualTo("Unsupported Specmatic config version: 0")
    }

    @Test
    fun `should create SpecmaticConfig given version 1 config file`() {
        val specmaticConfig =
            File("./src/test/resources/specmaticConfigFiles/specmatic_config_v1.yaml").toSpecmaticConfig()

        assertThat(specmaticConfig.version).isEqualTo(1)

        assertThat(specmaticConfig.sources.size).isEqualTo(1)
        assertThat(specmaticConfig.sources[0].provider).isEqualTo(SourceProvider.git)
        assertThat(specmaticConfig.sources[0].repository).isEqualTo("https://contracts")
        assertThat(specmaticConfig.sources[0].test).containsOnly("com/petstore/1.yaml")
        assertThat(specmaticConfig.sources[0].stub).containsOnly("com/petstore/payment.yaml")
    }

    @Test
    fun `should create SpecmaticConfig given version 2 config file`() {
        val specmaticConfig =
            File("./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml").toSpecmaticConfig()

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