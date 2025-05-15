package io.specmatic.core

import io.specmatic.conversions.*
import io.specmatic.core.pattern.*
import org.apache.http.HttpHeaders.AUTHORIZATION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class HttpRequestPatternKtTest {
    @Test
    fun `when generating new content part types with two value options there should be two types generated`() {
        val multiPartTypes = listOf(MultiPartContentPattern(
            "data",
            AnyPattern(listOf(StringPattern(), NumberPattern())),
        ))

        val newTypes = newMultiPartBasedOn(multiPartTypes, Row(), Resolver()).toList()

        assertThat(newTypes).hasSize(2)

        assertThat(newTypes).contains(listOf(MultiPartContentPattern("data", NumberPattern())))
        assertThat(newTypes).contains(listOf(MultiPartContentPattern("data", StringPattern())))
    }

    @Test
    fun `when a part is optional there should be two lists generated in which one has the part and the other does not`() {
        val multiPartTypes = listOf(MultiPartContentPattern("data?", StringPattern()))

        val newTypes = newMultiPartBasedOn(multiPartTypes, Row(), Resolver()).toList()

        assertThat(newTypes).hasSize(2)

        assertThat(newTypes).contains(listOf(MultiPartContentPattern("data", StringPattern())))
        assertThat(newTypes).contains(emptyList())
    }

    @ParameterizedTest
    @MethodSource("io.specmatic.core.ScenarioTest#securitySchemaProvider")
    fun `should remove security schemes before header matching occurs even if value is invalid`(securitySchema: OpenAPISecurityScheme) {
        val httpRequestPattern = HttpRequestPattern(
            httpPathPattern = buildHttpPathPattern("/"), method = "POST", securitySchemes = listOf(securitySchema)
        )
        val httpRequest = invalidateSecuritySchemes(HttpRequest("POST", "/"), securitySchema)
        val result = httpRequestPattern.matches(httpRequest, Resolver().disableOverrideUnexpectedKeycheck())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).satisfiesAnyOf(
            { assertThat(it).containsOnlyOnce(">> REQUEST.HEADERS.API-KEY") },
            { assertThat(it).containsOnlyOnce(">> REQUEST.QUERY-PARAMS.API-KEY") },
            { assertThat(it).containsOnlyOnce(">> REQUEST.HEADERS.Authorization") }
        )
    }

    companion object {
        private fun invalidateSecuritySchemes(request: HttpRequest, scheme: OpenAPISecurityScheme): HttpRequest {
            if (scheme !is BasicAuthSecurityScheme && scheme !is BearerSecurityScheme) return request
            return request.copy(headers = request.headers.plus(AUTHORIZATION to "INVALID"))
        }
    }
}