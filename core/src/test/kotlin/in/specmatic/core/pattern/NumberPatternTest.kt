package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATION
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.UseDefaultExample
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.shouldNotMatch
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class NumberPatternTest {
    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch NumberPattern()
    }

    @Test
    fun `should not be allow maxLength less than minLength`() {
        val exception = assertThrows<IllegalArgumentException> { NumberPattern(minLength = 6, maxLength = 4) }
        assertThat(exception.message).isEqualTo("maxLength cannot be less than minLength")
    }

    @Test
    fun `should allow maxLength equal to minLength`() {
        NumberPattern(minLength = 4, maxLength = 4)
    }

    @Test
    fun `should not allow minLength to be less than 1`() {
        val exception = assertThrows<IllegalArgumentException> { NumberPattern(minLength = 0) }
        assertThat(exception.message).isEqualTo("minLength cannot be less than 1")
    }

    @Test
    fun `should generate 1 digit long random number when min and max length are not specified`() {
        assertThat(NumberPattern().generate(Resolver()).toStringLiteral().length).isEqualTo(1)
    }

    @Test
    fun `should generate random number when minLength`() {
        assertThat(
            NumberPattern(minLength = 8, maxLength = 12).generate(Resolver()).toStringLiteral().length
        ).isEqualTo(8)
    }

    @Test
    fun `should match number of any length when min and max are not specified`() {
        val randomNumber = RandomStringUtils.randomNumeric((1..9).random()).toInt()
        assertThat(NumberPattern().matches(NumberValue(randomNumber), Resolver()).isSuccess()).isTrue
    }

    @Test
    fun `should not match when number is shorter than minLength`() {
        val result = NumberPattern(minLength = 4).matches(NumberValue(123), Resolver())
        assertThat(result.isSuccess()).isFalse
        assertThat(result.reportString()).isEqualTo("""Expected number with minLength 4, actual was 123 (number)""")
    }

    @Test
    fun `should not match when number is longer than maxLength`() {
        val result = NumberPattern(maxLength = 3).matches(NumberValue(1234), Resolver())
        assertThat(result.isSuccess()).isFalse
        assertThat(result.reportString()).isEqualTo("""Expected number with maxLength 3, actual was 1234 (number)""")
    }

    @Test
    fun `it should use the example if provided when generating`() {
        val generated = NumberPattern(example = "10").generate(Resolver(defaultExampleResolver = UseDefaultExample))
        assertThat(generated).isEqualTo(NumberValue(10))
    }

    @Test
    @Tag(GENERATION)
    fun `negative values should be generated`() {
        val result = NumberPattern().negativeBasedOn(Row(), Resolver()).map { it.value }.toList()
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "string",
            "boolean"
        )
    }
}