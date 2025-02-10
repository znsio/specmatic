package io.specmatic.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.specmatic.core.config.v3.Consumes
import io.specmatic.core.utilities.Flags.Companion.EXAMPLE_DIRECTORIES
import io.specmatic.core.utilities.Flags.Companion.EXTENSIBLE_SCHEMA
import io.specmatic.core.utilities.Flags.Companion.MAX_TEST_REQUEST_COMBINATIONS
import io.specmatic.core.utilities.Flags.Companion.ONLY_POSITIVE
import io.specmatic.core.utilities.Flags.Companion.SCHEMA_EXAMPLE_DEFAULT
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_GENERATIVE_TESTS
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_STUB_DELAY
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_TEST_TIMEOUT
import io.specmatic.core.utilities.Flags.Companion.VALIDATE_RESPONSE_VALUE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

internal class SpecmaticConfigKtTest {

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic.yml",
        "./src/test/resources/specmaticConfigFiles/specmatic.json",
    )
    @ParameterizedTest
    fun `parse specmatic config file with all values`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)

        val sources = SpecmaticConfig.getSources(config)
        assertThat(sources).isNotEmpty

        assertThat(sources?.first()?.provider).isEqualTo(SourceProvider.git)
        assertThat(sources?.first()?.repository).isEqualTo("https://contracts")
        assertThat(sources?.first()?.test).isEqualTo(listOf("com/petstore/1.spec"))
        assertThat(sources?.first()?.specsUsedAsStub()).isEqualTo(listOf("com/petstore/payment.spec"))

        assertThat(config.getAuthBearerFile()).isEqualTo("bearer.txt")
        assertThat(config.getAuthBearerEnvironmentVariable()).isNull()

        assertThat(config.getPipelineProvider()).isEqualTo(PipelineProvider.azure)
        assertThat(config.getPipelineOrganization()).isEqualTo("xnsio")
        assertThat(config.getPipelineProject()).isEqualTo("XNSIO")
        assertThat(config.getPipelineDefinitionId()).isEqualTo(1)

        assertThat(SpecmaticConfig.getEnvironments(config)?.get("staging")?.baseurls?.get("auth.spec")).isEqualTo("http://localhost:8080")
        assertThat(SpecmaticConfig.getEnvironments(config)?.get("staging")?.variables?.get("username")).isEqualTo("jackie")
        assertThat(SpecmaticConfig.getEnvironments(config)?.get("staging")?.variables?.get("password")).isEqualTo("PaSsWoRd")

        assertThat(ReportConfigurationDetails.getFormatters(SpecmaticConfig.getReport(config))?.get(0)?.type).isEqualTo(ReportFormatterType.TEXT)
        assertThat(ReportConfigurationDetails.getFormatters(SpecmaticConfig.getReport(config))?.get(0)?.layout).isEqualTo(ReportFormatterLayout.TABLE)
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.successCriteria?.minThresholdPercentage).isEqualTo(70)
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.successCriteria?.maxMissedEndpointsInSpec).isEqualTo(3)
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.successCriteria?.enforce).isTrue()
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.excludedEndpoints?.get(0)).isEqualTo("/heartbeat")
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.excludedEndpoints?.get(1)).isEqualTo("/health")

        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("oAuth2AuthCode") as OAuth2SecuritySchemeConfiguration).token
        ).isEqualTo("OAUTH1234")
        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("BearerAuth") as BearerSecuritySchemeConfiguration).token
        ).isEqualTo("BEARER1234")
        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("ApiKeyAuthHeader") as APIKeySecuritySchemeConfiguration).value
        ).isEqualTo("API-HEADER-USER")
        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("ApiKeyAuthQuery") as APIKeySecuritySchemeConfiguration).value
        ).isEqualTo("API-QUERY-PARAM-USER")

        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("BasicAuth") as BasicAuthSecuritySchemeConfiguration).token
        ).isEqualTo("Abc123")

        assertThat(config.getExamples()).isEqualTo(listOf("folder1/examples", "folder2/examples"))

        assertThat(config.isResiliencyTestingEnabled()).isEqualTo(true)
        assertThat(config.isExtensibleSchemaEnabled()).isTrue()
        assertThat(config.isResponseValueValidationEnabled()).isTrue()

        assertThat(config.getStubDelayInMilliseconds()).isEqualTo(1000L)
        assertThat(config.getStubGenerative()).isEqualTo(false)

        val htmlConfig = ReportConfigurationDetails.getFormatters(SpecmaticConfig.getReport(config))?.first { it.type == ReportFormatterType.HTML }
        assertThat(htmlConfig?.title).isEqualTo("Test Report")
        assertThat(htmlConfig?.heading).isEqualTo("Test Results")
        assertThat(htmlConfig?.outputDirectory).isEqualTo("output")

        assertThat(config.getTestTimeoutInMilliseconds()).isEqualTo(3000)
    }

    @Test
    fun `parse specmatic config file with only required values`() {
        val config = ObjectMapper(YAMLFactory()).registerKotlinModule().readValue(
            """
            {
                "sources": [
                    {
                        "provider": "git",
                        "test": [
                            "path/to/contract.spec"
                        ]
                    }
                ]
            }
        """.trimIndent(), SpecmaticConfig::class.java)

        val sources = SpecmaticConfig.getSources(config)
        assertThat(sources).isNotEmpty

        assertThat(sources?.first()?.provider).isEqualTo(SourceProvider.git)
        assertThat(sources?.first()?.test).isEqualTo(listOf("path/to/contract.spec"))
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.json",
    )
    @ParameterizedTest
    fun `parse specmatic config file with aliases`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)

        val sources = SpecmaticConfig.getSources(config)
        assertThat(sources).isNotEmpty

        assertThat(sources?.first()?.provider).isEqualTo(SourceProvider.git)
        assertThat(sources?.first()?.repository).isEqualTo("https://contracts")
        assertThat(sources?.first()?.test).isEqualTo(listOf("com/petstore/1.yaml"))
        assertThat(sources?.first()?.specsUsedAsStub()).isEqualTo(listOf("com/petstore/payment.yaml"))

        assertThat(config.getAuthBearerFile()).isEqualTo("bearer.txt")
        assertThat(config.getAuthBearerEnvironmentVariable()).isNull()

        assertThat(config.getPipelineProvider()).isEqualTo(PipelineProvider.azure)
        assertThat(config.getPipelineOrganization()).isEqualTo("xnsio")
        assertThat(config.getPipelineProject()).isEqualTo("XNSIO")
        assertThat(config.getPipelineDefinitionId()).isEqualTo(1)

        assertThat(SpecmaticConfig.getEnvironments(config)?.get("staging")?.baseurls?.get("auth.spec")).isEqualTo("http://localhost:8080")
        assertThat(SpecmaticConfig.getEnvironments(config)?.get("staging")?.variables?.get("username")).isEqualTo("jackie")
        assertThat(SpecmaticConfig.getEnvironments(config)?.get("staging")?.variables?.get("password")).isEqualTo("PaSsWoRd")

        assertThat(ReportConfigurationDetails.getFormatters(SpecmaticConfig.getReport(config))?.get(0)?.type).isEqualTo(ReportFormatterType.TEXT)
        assertThat(ReportConfigurationDetails.getFormatters(SpecmaticConfig.getReport(config))?.get(0)?.layout).isEqualTo(ReportFormatterLayout.TABLE)
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.successCriteria?.minThresholdPercentage).isEqualTo(70)
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.successCriteria?.maxMissedEndpointsInSpec).isEqualTo(3)
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.successCriteria?.enforce).isTrue()
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.excludedEndpoints?.get(0)).isEqualTo("/heartbeat")
        assertThat(ReportConfigurationDetails.getTypes(SpecmaticConfig.getReport(config))?.apiCoverage?.openAPI?.excludedEndpoints?.get(1)).isEqualTo("/health")

        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("oAuth2AuthCode") as OAuth2SecuritySchemeConfiguration).token
        ).isEqualTo("OAUTH1234")
        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("BearerAuth") as BearerSecuritySchemeConfiguration).token
        ).isEqualTo("BEARER1234")
        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("ApiKeyAuthHeader") as APIKeySecuritySchemeConfiguration).value
        ).isEqualTo("API-HEADER-USER")
        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("ApiKeyAuthQuery") as APIKeySecuritySchemeConfiguration).value
        ).isEqualTo("API-QUERY-PARAM-USER")

        assertThat(
            (config.getOpenAPISecurityConfigurationScheme("BasicAuth") as BasicAuthSecuritySchemeConfiguration).token
        ).isEqualTo("Abc123")
    }

    @Test
    fun `should create SpecmaticConfig with flag values read from system properties`() {
        val properties = mapOf(
            SPECMATIC_GENERATIVE_TESTS to "true",
            ONLY_POSITIVE to "false",
            VALIDATE_RESPONSE_VALUE to "true",
            EXTENSIBLE_SCHEMA to "false",
            SCHEMA_EXAMPLE_DEFAULT to "true",
            MAX_TEST_REQUEST_COMBINATIONS to "50",
            EXAMPLE_DIRECTORIES to "folder1/examples,folder2/examples",
            SPECMATIC_STUB_DELAY to "1000",
            SPECMATIC_TEST_TIMEOUT to "5000"
        )
        try {
            properties.forEach { System.setProperty(it.key, it.value) }
            val config = SpecmaticConfig()
            assertThat(config.isResiliencyTestingEnabled()).isTrue()
            assertThat(config.isOnlyPositiveTestingEnabled()).isFalse()
            assertThat(config.isResponseValueValidationEnabled()).isTrue()
            assertThat(config.isExtensibleSchemaEnabled()).isFalse()
            assertThat(config.getExamples()).isEqualTo(listOf("folder1/examples", "folder2/examples"))
            assertThat(config.getTestTimeoutInMilliseconds()).isEqualTo(5000)
            assertThat(config.getStubDelayInMilliseconds()).isEqualTo(1000L)
        } finally {
            properties.forEach { System.clearProperty(it.key) }
        }
    }

    @Test
    fun `isResiliencyTestingEnabled should return true if either of SPECMATIC_GENERATIVE_TESTS and ONLY_POSITIVE is true`() {
        try {
            System.setProperty(SPECMATIC_GENERATIVE_TESTS, "true")

            assertThat(SpecmaticConfig().isResiliencyTestingEnabled()).isTrue()
        } finally {
            System.clearProperty(SPECMATIC_GENERATIVE_TESTS)
        }

        try {
            System.setProperty(ONLY_POSITIVE, "true")

            assertThat(SpecmaticConfig().isResiliencyTestingEnabled()).isTrue()
        } finally {
            System.clearProperty(ONLY_POSITIVE)
        }
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic.yml",
        "./src/test/resources/specmaticConfigFiles/specmatic.json",
    )
    @ParameterizedTest
    fun `should give preferences to values coming from config file over the env vars or system properties`(configFile: String) {
        val props = mapOf(
            SPECMATIC_GENERATIVE_TESTS to "false",
            VALIDATE_RESPONSE_VALUE to "false",
            EXTENSIBLE_SCHEMA to "false",
            EXAMPLE_DIRECTORIES to "folder1/examples,folder2/examples",
            SPECMATIC_STUB_DELAY to "5000",
            EXAMPLE_DIRECTORIES to "folder1/examples,folder2/examples",
            SPECMATIC_TEST_TIMEOUT to "5000"
        )
        try {
            props.forEach { System.setProperty(it.key, it.value) }
            val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
            assertThat(config.isResiliencyTestingEnabled()).isTrue()
            assertThat(config.isResponseValueValidationEnabled()).isTrue()
            assertThat(config.isExtensibleSchemaEnabled()).isTrue()
            assertThat(config.getExamples()).isEqualTo(listOf("folder1/examples", "folder2/examples"))
            assertThat(config.getStubDelayInMilliseconds()).isEqualTo(1000L)
            assertThat(config.getTestTimeoutInMilliseconds()).isEqualTo(3000)
        } finally {
            props.forEach { System.clearProperty(it.key) }
        }
    }

    @Nested
    inner class StubPortConfigTests {
        @Test
        fun `should return the spec to stub port map from sources`() {
            val source1 = Source(
                stub = listOf(
                    Consumes.StringValue("9000_first.yaml"),
                    Consumes.StringValue("9000_second.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("9001_first.yaml", "9001_second.yaml"),
                        port = 9001
                    ),
                    Consumes.ObjectValue(
                        specs = listOf("9002_first.yaml"),
                        port = 9002
                    ),
                )
            )

            val source2 = Source(
                stub = listOf(
                    Consumes.StringValue("9000_third.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("9001_third.yaml", "9001_fourth.yaml"),
                        port = 9001
                    ),
                    Consumes.ObjectValue(
                        specs = listOf("9002_second.yaml"),
                        port = 9002
                    ),
                )
            )

            val specmaticConfig = SpecmaticConfig(
                sources = listOf(source1, source2)
            )

            val expectedMap = mapOf(
                "9000_first.yaml" to 9000,
                "9000_second.yaml" to 9000,
                "9000_third.yaml" to 9000,
                "9001_first.yaml" to 9001,
                "9001_second.yaml" to 9001,
                "9001_third.yaml" to 9001,
                "9001_fourth.yaml" to 9001,
                "9002_first.yaml" to 9002,
                "9002_second.yaml" to 9002,
            )

            assertThat(
                specmaticConfig.specToStubPortMap(
                    9000
                ).mapKeys { it.key.substringAfterLast(File.separator) }
            ).isEqualTo(expectedMap)
        }

        @Test
        fun `should return all stub ports from sources`() {
            val source1 = Source(
                stub = listOf(
                    Consumes.StringValue("9000_first.yaml"),
                    Consumes.StringValue("9000_second.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("9001_first.yaml", "9001_second.yaml"),
                        port = 9001
                    ),
                    Consumes.ObjectValue(
                        specs = listOf("9002_first.yaml"),
                        port = 9002
                    ),
                )
            )

            val source2 = Source(
                stub = listOf(
                    Consumes.StringValue("9000_third.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("9001_third.yaml", "9001_fourth.yaml"),
                        port = 9001
                    ),
                    Consumes.ObjectValue(
                        specs = listOf("9002_second.yaml"),
                        port = 9002
                    ),
                )
            )

            val specmaticConfig = SpecmaticConfig(
                sources = listOf(source1, source2)
            )

            assertThat(specmaticConfig.stubPorts(9000)).isEqualTo(listOf(9000, 9001, 9002))
        }

        @Test
        fun `should return all stub contracts from sources`() {
            val source1 = Source(
                stub = listOf(
                    Consumes.StringValue("9000_first.yaml"),
                    Consumes.StringValue("9000_second.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("9001_first.yaml", "9001_second.yaml"),
                        port = 9001
                    ),
                    Consumes.ObjectValue(
                        specs = listOf("9002_first.yaml"),
                        port = 9002
                    ),
                )
            )

            val source2 = Source(
                stub = listOf(
                    Consumes.StringValue("9000_third.yaml"),
                    Consumes.ObjectValue(
                        specs = listOf("9001_third.yaml", "9001_fourth.yaml"),
                        port = 9001
                    ),
                    Consumes.ObjectValue(
                        specs = listOf("9002_second.yaml"),
                        port = 9002
                    ),
                )
            )

            val specmaticConfig = SpecmaticConfig(
                sources = listOf(source1, source2)
            )

            assertThat(
                specmaticConfig.stubContracts().map { it.substringAfterLast(File.separator) }
            ).isEqualTo(
                listOf(
                    "9000_first.yaml",
                    "9000_second.yaml",
                    "9001_first.yaml",
                    "9001_second.yaml",
                    "9002_first.yaml",
                    "9000_third.yaml",
                    "9001_third.yaml",
                    "9001_fourth.yaml",
                    "9002_second.yaml"
                )
            )
        }
    }
}