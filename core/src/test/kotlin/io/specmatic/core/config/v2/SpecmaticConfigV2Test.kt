package io.specmatic.core.config.v2

import io.specmatic.core.SourceProvider
import io.specmatic.core.SpecmaticConfig
import io.specmatic.core.loadSpecmaticConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SpecmaticConfigV2Test {
    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given SpecmaticConfigV2 With Git URL`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
        val specmaticConfigV2 = SpecmaticConfigV2(
            version = config.version!!,
            contracts = config.sources.map { source ->
                ContractConfig(
                    git = when (source.provider) {
                        SourceProvider.git -> GitConfig(url = source.repository, branch = source.branch)
                        else -> null
                    },
                    provides = source.test,
                    consumes = source.stub
                )
            }
        )

        val specmaticConfig = specmaticConfigV2.transform()

        assertThat(specmaticConfig.version).isEqualTo(specmaticConfigV2.version)
        assertThat(specmaticConfig.sources.size).isEqualTo(specmaticConfigV2.contracts.size)
        assertThat(specmaticConfig.sources[0].provider).isEqualTo(SourceProvider.git)
        assertThat(specmaticConfig.sources[0].repository).isEqualTo(specmaticConfigV2.contracts[0].git?.url)
        assertThat(specmaticConfig.sources[0].branch).isEqualTo(specmaticConfigV2.contracts[0].git?.branch)
        assertThat(specmaticConfig.sources[0].test).containsAll(specmaticConfigV2.contracts[0].provides)
        assertThat(specmaticConfig.sources[0].stub).containsAll(specmaticConfigV2.contracts[0].consumes)
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml"
    )
    @ParameterizedTest
    fun `should create SpecmaticConfig given SpecmaticConfigV2 With Filesystem`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
        val specmaticConfigV2 = SpecmaticConfigV2(
            version = config.version!!,
            contracts = config.sources.map { source ->
                ContractConfig(
                    filesystem = when (source.provider) {
                        SourceProvider.filesystem -> FileSystemConfig(directory = source.directory)
                        else -> null
                    },
                    provides = source.test,
                    consumes = source.stub
                )
            }
        )

        val specmaticConfig = specmaticConfigV2.transform()

        assertThat(specmaticConfig.version).isEqualTo(specmaticConfigV2.version)
        assertThat(specmaticConfig.sources.size).isEqualTo(specmaticConfigV2.contracts.size)
        assertThat(specmaticConfig.sources[0].provider).isEqualTo(SourceProvider.filesystem)
        assertThat(specmaticConfig.sources[0].directory).isEqualTo(specmaticConfigV2.contracts[0].filesystem?.directory)
        assertThat(specmaticConfig.sources[0].test).containsAll(specmaticConfigV2.contracts[0].provides)
        assertThat(specmaticConfig.sources[0].stub).containsAll(specmaticConfigV2.contracts[0].consumes)
    }
}