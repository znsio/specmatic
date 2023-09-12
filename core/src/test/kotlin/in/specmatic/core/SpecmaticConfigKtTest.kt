package `in`.specmatic.core

import kotlinx.serialization.decodeFromString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SpecmaticConfigKtTest {
    @Test
    fun `parse specmatic config file with all values`() {
        val config = loadSpecmaticJsonConfig("./src/test/resources/specmaticConfigFiles/specmatic.json")

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
    }

    @Test
    fun `parse specmatic config file with only required values`() {
        val config = SpecmaticJsonFormat.decodeFromString<SpecmaticConfigJson>("""
            {
                "sources": [
                    {
                        "provider": "git"
                        "test": [
                            "path/to/contract.spec"
                        ]
                    }
                ]
            }
        """.trimIndent())

        assertThat(config.sources).isNotEmpty

        val sources = config.sources

        assertThat(sources.first().provider).isEqualTo(SourceProvider.git)
        assertThat(sources.first().test).isEqualTo(listOf("path/to/contract.spec"))
    }
}