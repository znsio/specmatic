package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnumPatternTest {
    @Nested
    inner class Construction {
        @Test
        fun `it should accept values of the same type`() {
            assertDoesNotThrow {
                EnumPattern(listOf(StringValue("01"), StringValue("02")))
            }
        }

        @Test
        fun `it should not accept values of the different types`() {
            org.junit.jupiter.api.assertThrows<ContractException> {
                EnumPattern(listOf(StringValue("01"), NumberValue(2)))
            }
        }

        @Test
        fun `it should accept null alongside other homogenous value when nullable is true`() {
            assertDoesNotThrow {
                EnumPattern(listOf(StringValue("01"), StringValue("02"), NullValue), nullable = true)
            }
        }
    }

    @Nested
    inner class GettingValues {
        @Test
        fun `it should generate a value from the given values`() {
            val enum = EnumPattern(listOf(StringValue("01"), StringValue("02")))

            val generatedValue = enum.generate(Resolver())

            assertThat(generatedValue).isIn(listOf(StringValue("01"), StringValue("02")))
        }

        @Test
        fun `it should parse a new value to the enum type`() {
            val enum = EnumPattern(listOf(NumberValue(1)))

            val parsedValue = enum.parse("03", Resolver())

            assertThat(parsedValue).isEqualTo(NumberValue(3))
        }

        @Test
        fun `it should fail to parse a new value NOT matching the enum type`() {
            val enum = EnumPattern(listOf(NumberValue(1)))

            assertThrows<ContractException> { enum.parse("not a number", Resolver()) }
        }
    }

    @Nested
    @Tag("generative")
    inner class TestGeneration {
        @Test
        fun `it should generate new patterns for all enum values when the row is empty`() {
            val enum = EnumPattern(listOf(StringValue("01"), StringValue("02")))

            val newPatterns = enum.newBasedOn(Row(), Resolver())

            assertThat(newPatterns).containsExactlyInAnyOrder(
                ExactValuePattern(StringValue("01")),
                ExactValuePattern(StringValue("02"))
            )
        }

        @Test
        fun `it should only pick the value in the row when the row is NOT empty`() {
            val jsonPattern = JSONObjectPattern(mapOf("type" to EnumPattern(listOf(StringValue("01"), StringValue("02")))))

            val newPatterns = jsonPattern.newBasedOn(Row(listOf("type"), values = listOf("01")), Resolver())

            val values = newPatterns.map { it.generate(Resolver()) }

            val strings = values.map { it.jsonObject.getValue("type") as StringValue }

            assertThat(strings).containsExactly(
                StringValue("01")
            )
        }

        @Test
        fun `it should use the inline example if present`() {
            val enum = EnumPattern(listOf(StringValue("01"), StringValue("02")), example = "01")
            val patterns = enum.newBasedOn(Row(), Resolver())

            assertThat(patterns).containsExactly(
                ExactValuePattern(StringValue("01"))
            )
        }

        @Test
        fun `it should generate negative values for what is in the row`() {
            val jsonPattern = EnumPattern(listOf(StringValue("01"), StringValue("02")))

            val newPatterns = jsonPattern.negativeBasedOn(Row(), Resolver())

            val negativeTypes = newPatterns.map { it.typeName }
            println(negativeTypes)

            assertThat(negativeTypes).containsExactlyInAnyOrder(
                "null",
                "number",
                "boolean"
            )
        }
    }

    private fun toStringEnum(vararg items: String): EnumPattern {
        return EnumPattern(items.map { StringValue(it) })
    }

    @Nested
    inner class BackwardCompatibility {
        private val enum = toStringEnum("sold", "available")

        @Test
        fun `enums with more are backward compatible than enums with less`() {
            val enumWithMore = toStringEnum("sold", "available", "reserved")
            assertThat(enum.encompasses(enumWithMore, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
        }

        @Test
        fun `enums with less are not backward compatible than enums with more`() {
            val enumWithLess = toStringEnum("sold")
            assertThat(enum.encompasses(enumWithLess, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        }
    }

    @Nested
    inner class ErrorMessage {
        @Test
        fun `enum error message should feature the expected values`() {
            val result: Result = EnumPattern(
                listOf(
                    StringValue("01"),
                    StringValue("02")
                )
            ).encompasses(
                StringPattern(), Resolver(), Resolver()
            )

            val resultText = result.reportString()

            println(resultText)

            assertThat(resultText).contains("01")
            assertThat(resultText).contains("02")
        }
    }
}