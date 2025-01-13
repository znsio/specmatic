package io.specmatic.core.config

import io.specmatic.core.SourceProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class SpecmaticConfigFactoryTest {
    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yaml"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given version 1 config file`(configFile: String) {
        val specmaticConfig = SpecmaticConfigFactory().create(File(configFile))

        assertThat(specmaticConfig.version).isIn(null, 1)

        assertThat(specmaticConfig.sources.size).isEqualTo(1)
        assertThat(specmaticConfig.sources[0].provider).isEqualTo(SourceProvider.git)
        assertThat(specmaticConfig.sources[0].repository).isEqualTo("https://contracts")
        assertThat(specmaticConfig.sources[0].test).containsOnly("com/petstore/1.yaml")
        assertThat(specmaticConfig.sources[0].stub).containsOnly("com/petstore/payment.yaml")
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given version 2 config file`(configFile: String) {
        val specmaticConfig = SpecmaticConfigFactory().create(File(configFile))

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