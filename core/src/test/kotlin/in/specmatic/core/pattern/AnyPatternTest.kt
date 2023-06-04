package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.utilities.withNullPattern
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.emptyPattern
import `in`.specmatic.shouldMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested

internal class AnyPatternTest {
    @Test
    fun `should match multiple patterns`() {
        val pattern = AnyPattern(listOf(NumberPattern(), StringPattern()))
        val string = StringValue("hello")
        val number = NumberValue(10)

        string shouldMatch pattern
        number shouldMatch pattern
    }

    @Test
    fun `error message when a json object does not match nullable primitive such as string in the contract`() {
        val pattern1 = AnyPattern(listOf(NullPattern, StringPattern()))
        val pattern2 = AnyPattern(listOf(DeferredPattern("(empty)"), StringPattern()))

        val value = parsedValue("""{"firstname": "Jane", "lastname": "Doe"}""")

        val resolver = withNullPattern(Resolver())

        val result1 = pattern1.matches(value, resolver)
        val result2 = pattern2.matches(value, resolver)

        assertThat(result2.toReport().toText().trimIndent()).isEqualTo("""Expected string, actual was JSON object {
       "firstname": "Jane",
       "lastname": "Doe"
   }""")

        assertThat(result1.toReport().toText().trimIndent()).isEqualTo("""Expected string, actual was JSON object {
       "firstname": "Jane",
       "lastname": "Doe"
   }""")
    }

    @Test
    fun `typename of a nullable type`() {
        val pattern1 = AnyPattern(listOf(NullPattern, StringPattern()))
        val pattern2 = AnyPattern(listOf(DeferredPattern("(empty)"), StringPattern()))

        assertThat(pattern1.typeName).isEqualTo("(string?)")
        assertThat(pattern2.typeName).isEqualTo("(string?)")
    }

    @Test
    fun `should create a new pattern based on the given row`() {
        val pattern = AnyPattern(listOf(parsedPattern("""{"id": "(number)"}""")))
        val row = Row(listOf("id"), listOf("10"))

        val value = pattern.newBasedOn(row, Resolver()).first().generate(Resolver())

        if(value is JSONObjectValue) {
            val id = value.jsonObject.getValue("id")

            if(id is NumberValue)
                assertEquals(10, id.number)
            else fail("Expected NumberValue")
        } else fail("Expected JSONObjectValue")
    }

    @Test
    fun `should generate a value based on the pattern given`() {
        NumberValue(10) shouldMatch AnyPattern(listOf(parsedPattern("(number)")))
    }

    @Test
    fun `AnyPattern of null and string patterns should encompass null pattern`() {
        assertThat(AnyPattern(listOf(NullPattern, StringPattern())).encompasses(NullPattern, Resolver(), Resolver())).isInstanceOf(
            Result.Success::class.java)
    }

    @Test
    fun `should encompass any of the specified types`() {
        val bigger = parsedPattern("""(string?)""")
        val smallerString = StringPattern()
        val smallerNull = emptyPattern()

        assertThat(bigger.encompasses(smallerString, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass itself`() {
        val bigger = parsedPattern("""(string?)""")
        assertThat(bigger.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass another any with fewer types`() {
        val bigger = parsedPattern("""(string?)""")
        val anyOfString = AnyPattern(listOf(StringPattern()))

        assertThat(bigger.encompasses(anyOfString, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass itself with a list type`() {
        val bigger = parsedPattern("""(string*?)""")
        assertThat(bigger.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass a matching string list type`() {
        val bigger = parsedPattern("""(string*?)""")
        val smallerStringList = parsedPattern("(string*)")
        assertThat(bigger.encompasses(smallerStringList, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass a matching null type`() {
        val bigger = parsedPattern("""(string*?)""")
        val smallerNull = emptyPattern()
        assertThat(bigger.encompasses(smallerNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `typeName should show nullable when one of the types is null`() {
        val type = AnyPattern(listOf(NullPattern, NumberPattern()))
        assertThat(type.typeName).isEqualTo("(number?)")
    }

    private fun toEnum(items: List<Value>): AnyPattern {
        return AnyPattern(items.map { ExactValuePattern(it) })
    }

    private fun toStringEnum(vararg items: String): AnyPattern {
        return toEnum(items.map { StringValue(it) })
    }

    @Nested
    inner class EnumBackwardComaptibility {
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
    inner class EnumErrorMessage {
        @Test
        fun `enum error message should feature the expected values`() {
            val result: Result = AnyPattern(
                listOf(
                    ExactValuePattern(StringValue("01")),
                    ExactValuePattern(StringValue("02"))
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

    @Test
    fun `parse operation of Nullable type implemented with AnyPattern should return a string`() {
        val type = AnyPattern(listOf(NullPattern, StringPattern()))
        val parsedValue = type.parse("22B Baker Street", Resolver(isNegative = true))
        assertThat(parsedValue.toStringLiteral()).isEqualTo("22B Baker Street")
    }

    @Test
    fun `values for negative tests`() {
        val negativeTypes = AnyPattern(listOf(NullPattern, StringPattern())).negativeBasedOn(Row(), Resolver())

        val expectedTypes = listOf(NumberPattern(), BooleanPattern)

        assertThat(negativeTypes).containsAll(expectedTypes)
        assertThat(negativeTypes).hasSize(expectedTypes.size)
    }

    @Test
    fun `we should get deep errors errors with breadcrumbs for each possible type in a oneOf list`() {
        val customerType = JSONObjectPattern(mapOf("name" to StringPattern()), typeAlias = "(Customer)")
        val employeeType = JSONObjectPattern(mapOf("name" to StringPattern(), "manager" to StringPattern()), typeAlias = "(Employee)")
        val oneOfCustomerOrEmployeeType = AnyPattern(listOf(customerType, employeeType))

        val personType = JSONObjectPattern(mapOf("personInfo" to oneOfCustomerOrEmployeeType))

        val personData = parsedJSONObject("""{ "personInfo": { "name": "Sherlock Holmes", "salutation": "Mr" } }""")

        val personMatchResult = personType.matches(personData, Resolver()).reportString()

        assertThat(personMatchResult).contains("""
            >> personInfo (when Customer object).salutation
            
               Key named "salutation" was unexpected
            
            >> personInfo (when Employee object).manager
            
               Expected key named "manager" was missing
            
            >> personInfo (when Employee object).salutation
            
               Key named "salutation" was unexpected
       """.trimIndent())
    }
}
