package io.specmatic.core.pattern

import io.specmatic.GENERATION
import io.specmatic.core.Resolver
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

internal class ExactValuePatternTest {
    @Test
    fun `should gracefully fail when matching non null value to null value`() {
        NullValue shouldNotMatch ExactValuePattern(NumberValue(10))
    }

    @Test
    @Tag(GENERATION)
    fun `negative patterns should be generated`() {
        val result =
            ExactValuePattern(StringValue("data")).negativeBasedOn(Row(), Resolver()).map { it.value }.toList()
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean"
        )
    }
}