package io.specmatic.conversions

import io.specmatic.core.Resolver
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OptionalBodyPatternTest {
    @Test
    fun `optional body error match`() {
        val body = OptionalBodyPattern.fromPattern(NumberPattern())

        val matchResult = body.matches(StringValue("abc"), Resolver())
        assertThat(matchResult.reportString().trim()).isEqualTo("Expected number, actual was \"abc\"")
    }
}