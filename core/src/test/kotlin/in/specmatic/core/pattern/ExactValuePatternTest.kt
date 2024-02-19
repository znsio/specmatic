package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATION
import `in`.specmatic.core.Resolver
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag

internal class ExactValuePatternTest {
    @Test
    fun `should gracefully fail when matching non null value to null value`() {
        NullValue shouldNotMatch ExactValuePattern(NumberValue(10))
    }

    @Test
    @Tag(GENERATION)
    fun `negative patterns should be generated`() {
        val result = ExactValuePattern(StringValue("data")).negativeBasedOn(Row(), Resolver())
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean"
        )
    }
}