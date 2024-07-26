package io.specmatic.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.DeferredPattern
import io.specmatic.core.pattern.StringPattern
import io.specmatic.optionalPattern
import io.specmatic.shouldMatch

internal class StringValueTest {
    @Test
    fun `should generate pattern matching an empty string from pattern with question suffix`() {
        val pattern = DeferredPattern("(string?)").resolvePattern(Resolver())

        val constructedPattern = optionalPattern(StringPattern())

        assertThat(pattern.encompasses(constructedPattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)

        StringValue("data") shouldMatch  pattern
        EmptyString shouldMatch  pattern
    }

    @Test
    fun `value to be shown in a snippet should be quoted but type not mentioned`() {
        assertThat(StringValue("test").valueErrorSnippet()).isEqualTo("\"test\"")
    }
}
