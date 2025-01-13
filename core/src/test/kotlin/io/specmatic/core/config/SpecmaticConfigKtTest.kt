package io.specmatic.core.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.specmatic.core.utilities.Flags
import org.assertj.core.api.Assertions
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

        Assertions.assertThat(config.sources).isNotEmpty

        val sources = config.sources

        Assertions.assertThat(sources.first().provider).isEqualTo(SourceProvider.git)
        Assertions.assertThat(sources.first().repository).isEqualTo("https://contracts")
        Assertions.assertThat(sources.first().test).isEqualTo(listOf("com/petstore/1.spec"))
        Assertions.assertThat(sources.first().stub).isEqualTo(listOf("com/petstore/payment.spec"))

        Assertions.assertThat(config.auth?.bearerFile).isEqualTo("bearer.txt")

        Assertions.assertThat(config.pipeline?.provider).isEqualTo(PipelineProvider.azure)
        Assertions.assertThat(config.pipeline?.organization).isEqualTo("xnsio")
        Assertions.assertThat(config.pipeline?.project).isEqualTo("XNSIO")
        Assertions.assertThat(config.pipeline?.definitionId).isEqualTo(1)

        Assertions.assertThat(config.environments?.get("staging")?.baseurls?.get("auth.spec")).isEqualTo("http://localhost:8080")
        Assertions.assertThat(config.environments?.get("staging")?.variables?.get("username")).isEqualTo("jackie")
        Assertions.assertThat(config.environments?.get("staging")?.variables?.get("password")).isEqualTo("PaSsWoRd")

        Assertions.assertThat(config.report?.formatters?.get(0)?.type).isEqualTo(ReportFormatterType.TEXT)
        Assertions.assertThat(config.report?.formatters?.get(0)?.layout).isEqualTo(ReportFormatterLayout.TABLE)
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.minThresholdPercentage).isEqualTo(70)
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.maxMissedEndpointsInSpec).isEqualTo(3)
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.enforce).isTrue()
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(0)).isEqualTo("/heartbeat")
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(1)).isEqualTo("/health")

        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("oAuth2AuthCode") as OAuth2SecuritySchemeConfiguration).token)
	        .isEqualTo("OAUTH1234")
        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("BearerAuth") as BearerSecuritySchemeConfiguration).token)
	        .isEqualTo("BEARER1234")
        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthHeader") as APIKeySecuritySchemeConfiguration).value)
	        .isEqualTo("API-HEADER-USER")
        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthQuery") as APIKeySecuritySchemeConfiguration).value)
	        .isEqualTo("API-QUERY-PARAM-USER")

        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("BasicAuth") as BasicAuthSecuritySchemeConfiguration).token)
	        .isEqualTo("Abc123")

        Assertions.assertThat(config.examples).isEqualTo(listOf("folder1/examples", "folder2/examples"))

        Assertions.assertThat(config.isResiliencyTestingEnabled()).isEqualTo(true)
        Assertions.assertThat(config.isExtensibleSchemaEnabled()).isTrue()
        Assertions.assertThat(config.isResponseValueValidationEnabled()).isTrue()

        Assertions.assertThat(config.stub.delayInMilliseconds).isEqualTo(1000L)
        Assertions.assertThat(config.stub.generative).isEqualTo(false)

        val htmlConfig = config.report?.formatters?.first { it.type == ReportFormatterType.HTML }
        Assertions.assertThat(htmlConfig?.title).isEqualTo("Test Report")
        Assertions.assertThat(htmlConfig?.heading).isEqualTo("Test Results")
        Assertions.assertThat(htmlConfig?.outputDirectory).isEqualTo("output")

        Assertions.assertThat(config.test?.timeoutInMilliseconds).isEqualTo(3000)
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

        Assertions.assertThat(config.sources).isNotEmpty

        val sources = config.sources

        Assertions.assertThat(sources.first().provider).isEqualTo(SourceProvider.git)
        Assertions.assertThat(sources.first().test).isEqualTo(listOf("path/to/contract.spec"))
    }

    @CsvSource(
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yaml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.yml",
        "./src/test/resources/specmaticConfigFiles/specmatic_alias.json",
    )
    @ParameterizedTest
    fun `parse specmatic config file with aliases`(configFile: String) {
        val config: SpecmaticConfig = loadSpecmaticConfig(configFile)

        Assertions.assertThat(config.sources).isNotEmpty

        val sources = config.sources

        Assertions.assertThat(sources.first().provider).isEqualTo(SourceProvider.git)
        Assertions.assertThat(sources.first().repository).isEqualTo("https://contracts")
        Assertions.assertThat(sources.first().test).isEqualTo(listOf("com/petstore/1.yaml"))
        Assertions.assertThat(sources.first().stub).isEqualTo(listOf("com/petstore/payment.yaml"))

        Assertions.assertThat(config.auth?.bearerFile).isEqualTo("bearer.txt")

        Assertions.assertThat(config.pipeline?.provider).isEqualTo(PipelineProvider.azure)
        Assertions.assertThat(config.pipeline?.organization).isEqualTo("xnsio")
        Assertions.assertThat(config.pipeline?.project).isEqualTo("XNSIO")
        Assertions.assertThat(config.pipeline?.definitionId).isEqualTo(1)

        Assertions.assertThat(config.environments?.get("staging")?.baseurls?.get("auth.spec")).isEqualTo("http://localhost:8080")
        Assertions.assertThat(config.environments?.get("staging")?.variables?.get("username")).isEqualTo("jackie")
        Assertions.assertThat(config.environments?.get("staging")?.variables?.get("password")).isEqualTo("PaSsWoRd")

        Assertions.assertThat(config.report?.formatters?.get(0)?.type).isEqualTo(ReportFormatterType.TEXT)
        Assertions.assertThat(config.report?.formatters?.get(0)?.layout).isEqualTo(ReportFormatterLayout.TABLE)
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.minThresholdPercentage).isEqualTo(70)
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.maxMissedEndpointsInSpec).isEqualTo(3)
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.successCriteria?.enforce).isTrue()
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(0)).isEqualTo("/heartbeat")
        Assertions.assertThat(config.report?.types?.apiCoverage?.openAPI?.excludedEndpoints?.get(1)).isEqualTo("/health")

        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("oAuth2AuthCode") as OAuth2SecuritySchemeConfiguration).token)
	        .isEqualTo("OAUTH1234")
        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("BearerAuth") as BearerSecuritySchemeConfiguration).token)
	        .isEqualTo("BEARER1234")
        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthHeader") as APIKeySecuritySchemeConfiguration).value)
	        .isEqualTo("API-HEADER-USER")
        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("ApiKeyAuthQuery") as APIKeySecuritySchemeConfiguration).value)
	        .isEqualTo("API-QUERY-PARAM-USER")

        Assertions.assertThat((config.security?.OpenAPI?.securitySchemes?.get("BasicAuth") as BasicAuthSecuritySchemeConfiguration).token)
	        .isEqualTo("Abc123")
    }

    @Test
    fun `should create SpecmaticConfig with flag values read from system properties`() {
        val properties = mapOf(
            Flags.SPECMATIC_GENERATIVE_TESTS to "true",
            Flags.ONLY_POSITIVE to "false",
            Flags.VALIDATE_RESPONSE_VALUE to "true",
            Flags.EXTENSIBLE_SCHEMA to "false",
            Flags.SCHEMA_EXAMPLE_DEFAULT to "true",
            Flags.MAX_TEST_REQUEST_COMBINATIONS to "50",
            Flags.EXAMPLE_DIRECTORIES to "folder1/examples,folder2/examples",
            Flags.SPECMATIC_STUB_DELAY to "1000",
            Flags.SPECMATIC_TEST_TIMEOUT to "5000"
        )
        try {
            properties.forEach { System.setProperty(it.key, it.value) }
            val config = SpecmaticConfig()
            Assertions.assertThat(config.isResiliencyTestingEnabled()).isTrue()
            Assertions.assertThat(config.isOnlyPositiveTestingEnabled()).isFalse()
            Assertions.assertThat(config.isResponseValueValidationEnabled()).isTrue()
            Assertions.assertThat(config.isExtensibleSchemaEnabled()).isFalse()
            Assertions.assertThat(config.examples).isEqualTo(listOf("folder1/examples", "folder2/examples"))
            Assertions.assertThat(config.stub.delayInMilliseconds).isEqualTo(1000L)
            Assertions.assertThat(config.test?.timeoutInMilliseconds).isEqualTo(5000)
        } finally {
            properties.forEach { System.clearProperty(it.key) }
        }
    }

    @Test
    fun `isResiliencyTestingEnabled should return true if either of SPECMATIC_GENERATIVE_TESTS and ONLY_POSITIVE is true`() {
        try {
            System.setProperty(Flags.SPECMATIC_GENERATIVE_TESTS, "true")

            Assertions.assertThat(SpecmaticConfig().isResiliencyTestingEnabled()).isTrue()
        } finally {
            System.clearProperty(Flags.SPECMATIC_GENERATIVE_TESTS)
        }

        try {
            System.setProperty(Flags.ONLY_POSITIVE, "true")

            Assertions.assertThat(SpecmaticConfig().isResiliencyTestingEnabled()).isTrue()
        } finally {
            System.clearProperty(Flags.ONLY_POSITIVE)
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
            Flags.SPECMATIC_GENERATIVE_TESTS to "false",
            Flags.VALIDATE_RESPONSE_VALUE to "false",
            Flags.EXTENSIBLE_SCHEMA to "false",
            Flags.EXAMPLE_DIRECTORIES to "folder1/examples,folder2/examples",
            Flags.SPECMATIC_STUB_DELAY to "5000",
            Flags.EXAMPLE_DIRECTORIES to "folder1/examples,folder2/examples",
            Flags.SPECMATIC_TEST_TIMEOUT to "5000"
        )
        try {
            props.forEach { System.setProperty(it.key, it.value) }
            val config: SpecmaticConfig = loadSpecmaticConfig(configFile)
            Assertions.assertThat(config.isResiliencyTestingEnabled()).isTrue()
            Assertions.assertThat(config.isResponseValueValidationEnabled()).isTrue()
            Assertions.assertThat(config.isExtensibleSchemaEnabled()).isTrue()
            Assertions.assertThat(config.examples).isEqualTo(listOf("folder1/examples", "folder2/examples"))
            Assertions.assertThat(config.stub.delayInMilliseconds).isEqualTo(1000L)
            Assertions.assertThat(config.test?.timeoutInMilliseconds).isEqualTo(3000)
        } finally {
            props.forEach { System.clearProperty(it.key) }
        }
    }
}