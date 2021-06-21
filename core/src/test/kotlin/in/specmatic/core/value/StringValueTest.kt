package `in`.specmatic.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.DeferredPattern
import `in`.specmatic.core.pattern.StringPattern
import `in`.specmatic.optionalPattern
import `in`.specmatic.shouldMatch

internal class StringValueTest {
    @Test
    fun `should generate pattern matching an empty string from pattern with question suffix`() {
        val pattern = DeferredPattern("(string?)").resolvePattern(Resolver())

        val constructedPattern = optionalPattern(StringPattern())

        assertThat(pattern.encompasses(constructedPattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)

        StringValue("data") shouldMatch  pattern
        EmptyString shouldMatch  pattern
    }
}
