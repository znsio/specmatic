package io.specmatic.conversions

import io.specmatic.core.*
import io.specmatic.core.pattern.DeferredPattern
import io.specmatic.core.pattern.EnumPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File


class OpenApiPreProcessorTest {

    @Test
    fun `should be able to to load open-api specification with referenced paths`() {
        val openApiFile = File("src/test/resources/openapi/has_referenced_paths/api/v1/order.yaml")
        val specification = OpenApiSpecification.fromFile(openApiFile.canonicalPath)
        val feature = specification.toFeature()

        assertThat(feature.scenarios).hasSize(1).containsExactly(
            Scenario(ScenarioInfo(
                scenarioName = "GET /v1/products. Response: successful operation",
                httpRequestPattern = HttpRequestPattern(httpPathPattern = HttpPathPattern.from("/v1/products"), method = "GET", body = NoBodyPattern),
                httpResponsePattern = HttpResponsePattern(
                    status = 200,
                    headersPattern = HttpHeadersPattern(contentType = "application/json"),
                    body = DeferredPattern("(Product)")
                ),
                patterns = mapOf(
                    "(ProductType)" to EnumPattern(values = setOf("FOOD", "GADGET", "OTHER").map { StringValue(it) }, typeAlias = "ProductType"),
                    "(Product)" to JSONObjectPattern(mapOf("category?" to DeferredPattern("(ProductType)")), typeAlias = "(Product)")
                ),
                serviceType = "HTTP"
            ))
        )
    }
}