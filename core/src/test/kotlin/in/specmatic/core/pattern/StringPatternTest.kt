package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATION
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.UseDefaultExample
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldNotMatch
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import `in`.specmatic.core.Result as Result

internal class StringPatternTest {
    @Test
    fun `should fail to match null values gracefully`() {
        NullValue shouldNotMatch StringPattern()
    }

    @Test
    fun `should not be allow maxLength less than minLength`() {
        val exception = assertThrows<IllegalArgumentException> { StringPattern(minLength = 6, maxLength = 4) }
        assertThat(exception.message).isEqualTo("maxLength cannot be less than minLength")
    }

    @Test
    fun `should allow maxLength equal to minLength`() {
        StringPattern(minLength = 4, maxLength = 4)
    }

    @Test
    fun `should generate 5 character long random string when min and max length are not specified`() {
        assertThat(StringPattern().generate(Resolver()).toStringLiteral().length).isEqualTo(5)
    }

    @Test
    fun `should generate random string when minLength`() {
        assertThat(StringPattern(minLength = 8).generate(Resolver()).toStringLiteral().length).isEqualTo(8)
    }

    @Test
    fun `should match empty String when min and max are not specified`() {
        assertThat(StringPattern().matches(StringValue(""), Resolver()).isSuccess()).isTrue
    }

    @Test
    fun `should match String of any length when min and max are not specified`() {
        val randomString = RandomStringUtils.randomAlphabetic((0..99).random())
        assertThat(StringPattern().matches(StringValue(randomString), Resolver()).isSuccess()).isTrue
    }

    @Test
    fun `should not match when String is shorter than minLength`() {
        val result = StringPattern(minLength = 4).matches(StringValue("abc"), Resolver())
        assertThat(result.isSuccess()).isFalse
        assertThat(result.reportString()).isEqualTo("""Expected string with minLength 4, actual was "abc"""")
    }

    @Test
    fun `should not match when String is longer than maxLength`() {
        val result = StringPattern(maxLength = 3).matches(StringValue("test"), Resolver())
        assertThat(result.isSuccess()).isFalse
        assertThat(result.reportString()).isEqualTo("""Expected string with maxLength 3, actual was "test"""")
    }

    companion object {
        @JvmStatic
        fun lengthTestValues(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(null, 10, 5),
                Arguments.of(null, 4, 4),
                Arguments.of(1, 10, 5),
                Arguments.of(1, 4, 4),
                Arguments.of(1, 5, 5),
                Arguments.of(5, 10, 5),
                Arguments.of(6, 10, 6),
                Arguments.of(6, null, 6),
                Arguments.of(3, null, 5),
                Arguments.of(null, null, 5)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("lengthTestValues")
    fun `generate string value of appropriate length matching minLength and maxLength parameters`(min: Int?, max: Int?, length: Int) {
        val result = StringPattern(minLength = min, maxLength = max).generate(Resolver()) as StringValue

        assertThat(result.string.length).isEqualTo(length)
    }

    @Test
    fun `string should encompass enum of string`() {
        val result: Result = StringPattern().encompasses(
            AnyPattern(
                listOf(
                    ExactValuePattern(StringValue("01")),
                    ExactValuePattern(StringValue("02"))
                )
            ), Resolver(), Resolver()
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `enum should not encompass string`() {
        val result: Result = AnyPattern(
            listOf(
                ExactValuePattern(StringValue("01")),
                ExactValuePattern(StringValue("02"))
            )
        ).encompasses(
            StringPattern(), Resolver(), Resolver()
        )

        println(result.reportString())
        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `it should use the example if provided when generating`() {
        val generated = StringPattern(example = "sample data").generate(Resolver(defaultExampleResolver = UseDefaultExample))
        assertThat(generated).isEqualTo(StringValue("sample data"))
    }

    @Test
    @Tag(GENERATION)
    fun `negative values should be generated`() {
        val result = StringPattern().negativeBasedOn(Row(), Resolver()).map { it.value }.toList()
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean",
        )
    }

    @Test
    @Tag(GENERATION)
    fun `positive values for lengths should be generated when lengths are provided`() {
        val minLength = 10
        val maxLength = 20

        val result = StringPattern(minLength = minLength, maxLength = maxLength).newBasedOn(Row(), Resolver()).toList()

        val randomlyGeneratedStrings = result.map { it.value } .filterIsInstance<ExactValuePattern>().map { it.pattern.toString() }

        assertThat(randomlyGeneratedStrings.filter { it.length == minLength}).hasSize(1)
        assertThat(randomlyGeneratedStrings.filter { it.length == maxLength}).hasSize(1)
    }

    @Test
    @Tag(GENERATION)
    fun `negative values for lengths should be generated when lengths are provided`() {
        val minLength = 10
        val maxLength = 20

        val result = StringPattern(minLength = minLength, maxLength = maxLength).negativeBasedOn(Row(), Resolver()).map { it.value }.toList()
        assertThat(result.map { it.typeName }).contains(
            "null",
            "number",
            "boolean",
        )

        val randomlyGeneratedStrings = result.filterIsInstance<ExactValuePattern>().map { it.pattern.toString() }

        assertThat(randomlyGeneratedStrings.filter { it.length == minLength - 1 }).hasSize(1)
        assertThat(randomlyGeneratedStrings.filter { it.length == maxLength + 1 }).hasSize(1)
    }

    @Test
    fun `string pattern encompasses email`() {
        assertThat(StringPattern().encompasses(EmailPattern(), Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

}
