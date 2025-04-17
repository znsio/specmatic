package io.specmatic.core.pattern

import io.specmatic.GENERATION
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class EmailPatternTest {
    @Test
    @Tag(GENERATION)
    fun `negative values should be generated`() {
        val result = EmailPattern().negativeBasedOn(Row(), Resolver()).toList().map { it.value }
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean",
            "string"
        )
    }

    @Test
    fun `email should not encompass string`() {
        assertThat(
            EmailPattern().encompasses(StringPattern(), Resolver(), Resolver(), emptySet())
        ).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `fillInTheBlanks should handle any-value pattern token correctly`() {
        val pattern = EmailPattern()
        val resolver = Resolver()
        val value = StringValue("(anyvalue)")

        val filledInValue = pattern.fillInTheBlanks(value, resolver).value
        val matchResult = pattern.matches(filledInValue, resolver)

        assertThat(matchResult.isSuccess()).withFailMessage(matchResult.reportString()).isTrue()
    }

    @Test
    fun `should be able to fix invalid values`() {
        val pattern = JSONObjectPattern(mapOf("email" to EmailPattern()), typeAlias = "(Test)")
        val resolver = Resolver(dictionary = mapOf("Test.email" to StringValue("SomeDude@example.com")))
        val invalidValues = listOf(
            StringValue("Unknown"),
            NumberValue(999),
            NullValue
        )

        assertThat(invalidValues).allSatisfy {
            val fixedValue = pattern.fixValue(JSONObjectValue(mapOf("email" to it)), resolver)
            fixedValue as JSONObjectValue
            assertThat(fixedValue.jsonObject["email"]).isEqualTo(StringValue("SomeDude@example.com"))
        }
    }

    @Test
    fun `should be able to create newBasedOn values without row value`() {
        val jsonPattern = JSONObjectPattern(mapOf("id" to NumberPattern(), "email" to EmailPattern()), typeAlias = "(Details)")
        val newBased = jsonPattern.newBasedOn(Resolver())

        assertThat(newBased.toList()).allSatisfy {
            assertThat(it.pattern.getValue("email")).isInstanceOf(EmailPattern::class.java)
        }
    }
}