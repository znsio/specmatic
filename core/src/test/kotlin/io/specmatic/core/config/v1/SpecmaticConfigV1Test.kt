package io.specmatic.core.config.v1

import io.specmatic.core.config.SpecmaticConfig
import io.specmatic.core.config.loadSpecmaticConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SpecmaticConfigV1Test {
    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.json",
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given SpecmaticConfigV1`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
        val specmaticConfigV1 = SpecmaticConfigV1(
            sources = config.sources,
            version = config.version
        )

        val specmaticConfig = specmaticConfigV1.transform()

        assertThat(specmaticConfig.version).isEqualTo(specmaticConfigV1.version)
        assertThat(specmaticConfig.sources.size).isEqualTo(specmaticConfigV1.sources.size)
        assertThat(specmaticConfig.sources[0].test).containsAll(specmaticConfigV1.sources[0].test)
        assertThat(specmaticConfig.sources[0].stub).containsAll(specmaticConfigV1.sources[0].stub)
        assertThat(specmaticConfig.sources[0].provider).isEqualTo(specmaticConfigV1.sources[0].provider)
        assertThat(specmaticConfig.sources[0].repository).isEqualTo(specmaticConfigV1.sources[0].repository)
        assertThat(specmaticConfig.sources[0].branch).isEqualTo(specmaticConfigV1.sources[0].branch)
        assertThat(specmaticConfig.sources[0].directory).isEqualTo(specmaticConfigV1.sources[0].directory)
    }
}