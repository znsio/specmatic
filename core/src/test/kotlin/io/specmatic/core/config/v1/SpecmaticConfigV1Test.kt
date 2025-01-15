package io.specmatic.core.config.v1

import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.loadSpecmaticConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SpecmaticConfigV1Test {
    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_without_version.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_without_version.json"
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
        assertThat(specmaticConfig.sources).containsAll(specmaticConfigV1.sources)
    }
}