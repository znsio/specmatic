package `in`.specmatic.core.pattern

import `in`.specmatic.core.Flags
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.value.BooleanValue
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat

internal class BooleanPatternTest {
    @Test
    fun `should fail to match nulls graceully`() {
        NullValue shouldNotMatch BooleanPattern()
    }

    @Test
    fun `it should use the example if provided when generating`() {
        try {
            System.setProperty(Flags.schemaExampleDefault, "true")
            val generated = BooleanPattern(example = "true").generate(Resolver())
            assertThat(generated).isEqualTo(BooleanValue(true))
        } finally {
            System.clearProperty(Flags.schemaExampleDefault)
        }
    }

    @Test
    fun `negative values should be generated`() {
        val result = BooleanPattern().negativeBasedOn(Row(), Resolver())
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null"
        )
    }
}