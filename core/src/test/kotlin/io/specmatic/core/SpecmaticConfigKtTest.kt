package io.specmatic.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.specmatic.core.utilities.Flags.Companion.EXTENSIBLE_SCHEMA
import io.specmatic.core.utilities.Flags.Companion.MAX_TEST_REQUEST_COMBINATIONS
import io.specmatic.core.utilities.Flags.Companion.ONLY_POSITIVE
import io.specmatic.core.utilities.Flags.Companion.SCHEMA_EXAMPLE_DEFAULT
import io.specmatic.core.utilities.Flags.Companion.SPECMATIC_GENERATIVE_TESTS
import io.specmatic.core.utilities.Flags.Companion.VALIDATE_RESPONSE_VALUE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SpecmaticConfigKtTest {

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic.yml",
        "./src/test/resources/specmaticConfigFiles/specmatic.json",
    )
    @ParameterizedTest
    fun `parse specmatic config file with all values`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)

        assertThat(config.sources).isNotEmpty

        val sources = config.sources

        assertThat(sources.first().provider).isEqualTo(SourceProvider.git)
        assertThat(sources.first().repository).isEqualTo("https://contracts")
        assertThat(sources.first().test).isEqualTo(listOf("com/petstore/1.spec"))
        assertThat(sources.first().stub).isEqualTo(listOf("com/petstore/payment.spec"))

        assertThat(config.auth?.bearerFile).isEqualTo("bearer.txt")

        assertThat(config.pipeline?.provider).isEqualTo(PipelineProvider.azure)
        assertThat(config.pipeline?.organization).isEqualTo("xnsio")
        assertThat(config.pipeline?.project).isEqualTo("XNSIO")
        assertThat(config.pipeline?.definitionId).isEqualTo(1)

        assertThat(config.environments?.get("staging")?.baseurls?.get("auth.spec")).isEqualTo("http://localhost:8080")
        assertThat(config.environments?.get("staging")?.variables?.get("username")).isEqualTo("jackie")
        assertThat(config.environments?.get("staging")?.variables?.get("password")).isEqualTo("PaSsWoRd")

        assertThat(config.report?.formatters?.get(0)?.type).isEqualTo(ReportFormatterType.TEXT)
        assertThat(config.report?.formatters?.get(0)?.layout).isEqualTo(ReportFormatterLayout.TABLE)
        assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.minThresholdPercentage).isEqualTo(70)
        assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.maxMissedEndpointsInSpec).isEqualTo(3)
        assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.enforce).isTrue()
        assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(0)).isEqualTo("/heartbeat")
        assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(1)).isEqualTo("/health")

        assertThat((config.security?.OpenAPI?.securitySchemes?.get("oAuth2AuthCode") as OAuth2SecuritySchemeConfiguration).token).isEqualTo("OAUTH1234")
        assertThat((config.security?.OpenAPI?.securitySchemes?.get("BearerAuth") as BearerSecuritySchemeConfiguration).token).isEqualTo("BEARER1234")
        assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthHeader") as APIKeySecuritySchemeConfiguration).value).isEqualTo("API-HEADER-USER")
        assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthQuery") as APIKeySecuritySchemeConfiguration).value).isEqualTo("API-QUERY-PARAM-USER")

        assertThat((config.security?.OpenAPI?.securitySchemes?.get("BasicAuth") as BasicAuthSecuritySchemeConfiguration).token).isEqualTo("Abc123")

        assertThat(config.isResiliencyTestingEnabled()).isEqualTo(true)
        assertThat(config.isExtensibleSchemaEnabled()).isTrue()
        assertThat(config.isResponseValueValidationEnabled()).isTrue()
    }

    @Test
    fun `parse specmatic config file with only required values`() {
        val config = ObjectMapper(YAMLFactory()).readValue("""
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

        assertThat(config.sources).isNotEmpty

        val sources = config.sources

        assertThat(sources.first().provider).isEqualTo(SourceProvider.git)
        assertThat(sources.first().test).isEqualTo(listOf("path/to/contract.spec"))
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.json",
    )
    @ParameterizedTest
    fun `parse specmatic config file with aliases provides and consumes`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)

        assertThat(config.sources).isNotEmpty

        val sources = config.sources

        assertThat(sources.first().provider).isEqualTo(SourceProvider.git)
        assertThat(sources.first().repository).isEqualTo("https://contracts")
        assertThat(sources.first().test).isEqualTo(listOf("com/petstore/1.yaml"))
        assertThat(sources.first().stub).isEqualTo(listOf("com/petstore/payment.yaml"))

        assertThat(config.auth?.bearerFile).isEqualTo("bearer.txt")

        assertThat(config.pipeline?.provider).isEqualTo(PipelineProvider.azure)
        assertThat(config.pipeline?.organization).isEqualTo("xnsio")
        assertThat(config.pipeline?.project).isEqualTo("XNSIO")
        assertThat(config.pipeline?.definitionId).isEqualTo(1)

        assertThat(config.environments?.get("staging")?.baseurls?.get("auth.spec")).isEqualTo("http://localhost:8080")
        assertThat(config.environments?.get("staging")?.variables?.get("username")).isEqualTo("jackie")
        assertThat(config.environments?.get("staging")?.variables?.get("password")).isEqualTo("PaSsWoRd")

        assertThat(config.report?.formatters?.get(0)?.type).isEqualTo(ReportFormatterType.TEXT)
        assertThat(config.report?.formatters?.get(0)?.layout).isEqualTo(ReportFormatterLayout.TABLE)
        assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.minThresholdPercentage).isEqualTo(70)
        assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.maxMissedEndpointsInSpec).isEqualTo(3)
        assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.enforce).isTrue()
        assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(0)).isEqualTo("/heartbeat")
        assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(1)).isEqualTo("/health")

        assertThat((config.security?.OpenAPI?.securitySchemes?.get("oAuth2AuthCode") as OAuth2SecuritySchemeConfiguration).token).isEqualTo("OAUTH1234")
        assertThat((config.security?.OpenAPI?.securitySchemes?.get("BearerAuth") as BearerSecuritySchemeConfiguration).token).isEqualTo("BEARER1234")
        assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthHeader") as APIKeySecuritySchemeConfiguration).value).isEqualTo("API-HEADER-USER")
        assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthQuery") as APIKeySecuritySchemeConfiguration).value).isEqualTo("API-QUERY-PARAM-USER")

        assertThat((config.security?.OpenAPI?.securitySchemes?.get("BasicAuth") as BasicAuthSecuritySchemeConfiguration).token).isEqualTo("Abc123")
    }

    @Test
    fun `should create SpecmaticConfig with flag values read from system properties`() {
        val properties = mapOf(
            SPECMATIC_GENERATIVE_TESTS to "true",
            ONLY_POSITIVE to "false",
            VALIDATE_RESPONSE_VALUE to "true",
            EXTENSIBLE_SCHEMA to "false",
            SCHEMA_EXAMPLE_DEFAULT to "true",
            MAX_TEST_REQUEST_COMBINATIONS to "50"
        )
        properties.forEach { System.setProperty(it.key, it.value) }

        val config = SpecmaticConfig()

        assertThat(config.isResiliencyTestingEnabled()).isTrue()
        assertThat(config.isOnlyPositiveTestingEnabled()).isFalse()
        assertThat(config.isResponseValueValidationEnabled()).isTrue()
        assertThat(config.isExtensibleSchemaEnabled()).isFalse()

        properties.forEach { System.clearProperty(it.key) }
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
            EXTENSIBLE_SCHEMA to "false"
        )
        try {
            props.forEach { System.setProperty(it.key, it.value) }

            val config: SpecmaticConfig = loadSpecmaticConfig(configFile)

            assertThat(config.isResiliencyTestingEnabled()).isTrue()
            assertThat(config.isResponseValueValidationEnabled()).isTrue()
            assertThat(config.isExtensibleSchemaEnabled()).isTrue()
        } finally {
            props.forEach { System.clearProperty(it.key) }
        }
    }
}