package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils

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
        assertThat(StringPattern().generate(Resolver()).toStringValue().length).isEqualTo(5)
    }

    @Test
    fun `should generate random string when minLength`() {
        assertThat(StringPattern(minLength = 8).generate(Resolver()).toStringValue().length).isEqualTo(8)
    }

    @Test
    fun `should match empty String when min and max are not specified`() {
        assertThat(StringPattern().matches(StringValue(""), Resolver()).isTrue()).isTrue
    }

    @Test
    fun `should match String of any length when min and max are not specified`() {
        val randomString = RandomStringUtils.randomAlphabetic((0..99).random())
        assertThat(StringPattern().matches(StringValue(randomString), Resolver()).isTrue()).isTrue
    }

    @Test
    fun `should not match when String is shorter than minLength`() {
        val result = StringPattern(minLength = 4).matches(StringValue("abc"), Resolver())
        assertThat(result.isTrue()).isFalse
        assertThat(result.reportString()).isEqualTo("""Expected string with minLength 4, actual was string: "abc"""")
    }

    @Test
    fun `should not match when String is longer than maxLength`() {
        val result = StringPattern(maxLength = 3).matches(StringValue("test"), Resolver())
        assertThat(result.isTrue()).isFalse
        assertThat(result.reportString()).isEqualTo("""Expected string with maxLength 3, actual was string: "test"""")
    }
}