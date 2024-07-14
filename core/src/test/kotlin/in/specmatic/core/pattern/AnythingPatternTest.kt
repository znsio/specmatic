package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.BooleanValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AnythingPatternTest {
    fun matches(value: Value) {
        assertThat(AnythingPattern.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should match anything`() {
        matches(NumberValue(10))
        matches(StringValue("ten"))
        matches(BooleanValue(true))
    }

    @Test
    fun `generates a string value`() {
        assertThat(AnythingPattern.generate(Resolver())).isInstanceOf(StringValue::class.java)
    }

    @Test
    fun `row value gets picked up when in an xml type`() {
        val type = XMLPattern("<data>(anything)</data>")
        val row = Row(listOf("data"), listOf("abcxyz"))

        val newType = type.newBasedOn(row, Resolver()).map { it.value as XMLPattern }

        val expected = ExactValuePattern(StringValue("abcxyz"))
        val firstNodeInType = newType.first().pattern.nodes.first()

        assertThat(firstNodeInType).isEqualTo(expected)
    }

    @Test
    fun `parse just returns the value it is given`() {
        assertThat(AnythingPattern.parse("hello", Resolver())).isEqualTo(StringValue("hello"))
    }

    @Test
    fun `should encompass itself`() {
        assertThat(AnythingPattern.encompasses(
            AnythingPattern,
            Resolver(),
            Resolver()
        )).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should not encompass anything else`() {
        assertThat(AnythingPattern.encompasses(
            NumberPattern(),
            Resolver(),
            Resolver()
        )).isInstanceOf(Result.Failure::class.java)
    }
}