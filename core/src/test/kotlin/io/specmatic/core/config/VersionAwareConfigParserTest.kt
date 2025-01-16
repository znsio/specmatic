package io.specmatic.core.config

import io.specmatic.core.Source
import io.specmatic.core.SourceProvider
import io.specmatic.core.pattern.ContractException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

internal class VersionAwareConfigParserTest {

    @Nested
    inner class ToSpecmaticConfigTests {
        @CsvSource(
            "./src/test/resources/specmaticConfigFiles/specmatic_config_v1.yaml",
            "./src/test/resources/specmaticConfigFiles/specmatic_config_v1.json"
        )
        @ParameterizedTest
        fun `should create SpecmaticConfig from a config file with version 1`(configFile: String) {
            val specmaticConfig = File(configFile).toSpecmaticConfig()

            assertThat(specmaticConfig.version).isEqualTo(SpecmaticConfigVersion.VERSION_1)

            val expectedGitSource = Source(
                provider = SourceProvider.git,
                repository = "https://contracts",
                test = listOf("com/petstore/1.yaml"),
                stub = listOf("com/petstore/payment.yaml")
            )
            val expectedFileSystemSource = Source(
                provider = SourceProvider.filesystem,
                directory = "contracts",
                test = listOf("com/petstore/1.yaml"),
                stub = listOf("com/petstore/payment.yaml", "com/petstore/order.yaml")
            )

            assertThat(specmaticConfig.sources).contains(expectedGitSource, expectedFileSystemSource)
        }

        @CsvSource(
            "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.yaml",
            "./src/test/resources/specmaticConfigFiles/specmatic_config_v2.json"
        )
        @ParameterizedTest
        fun `should create SpecmaticConfig from a config file with version 2`(configFile: String) {
            val specmaticConfig = File(configFile).toSpecmaticConfig()

            assertThat(specmaticConfig.version).isEqualTo(SpecmaticConfigVersion.VERSION_2)

            val expectedGitSource = Source(
                provider = SourceProvider.git,
                repository = "https://contracts",
                branch = "1.0.1",
                test = listOf("product_search_bff_v4.yaml"),
                stub = listOf("api_order_v3.yaml", "kafka.yaml")
            )
            val expectedFileSystemSource = Source(
                provider = SourceProvider.filesystem,
                directory = "specmatic-order-contracts",
                test = listOf("product_search_bff_v4.yaml"),
                stub = listOf("api_order_v3.yaml", "kafka.yaml")
            )

            assertThat(specmaticConfig.sources).contains(expectedGitSource, expectedFileSystemSource)
        }

        @CsvSource(
            "./src/test/resources/specmaticConfigFiles/specmatic_without_version.yaml",
            "./src/test/resources/specmaticConfigFiles/specmatic_without_version.json"
        )
        @ParameterizedTest
        fun `should default to version 1 when no version is provided`(configFile: String) {
            assertThat(
                File(configFile).toSpecmaticConfig().version
            ).isEqualTo(SpecmaticConfigVersion.VERSION_1)
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

    @Nested
    inner class GetVersionTests {
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
    }

}