package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.*
import `in`.specmatic.core.value.*
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import org.junit.jupiter.api.Assertions.*

internal class JSONObjectPatternTest {
    @Test
    fun `Given an optional key, the generated object should contain the key without the question mark`() {
        when (val result = parsedPattern("""{"id?": "(number)"}""", null).generate(Resolver())) {
            is JSONObjectValue -> assertTrue("id" in result.jsonObject)
            else -> throw Exception("Wrong type, got ${result.javaClass}, expected JSONObjectValue")
        }
    }

    @Test
    fun `Given an optional key, the suffix should remain in place in an object generated using newBasedOn`() {
        val newPatterns = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(Row(), Resolver())
        val newPattern = newPatterns.first {
            it != toJSONObjectPattern(emptyMap())
        }

        val objectWithId = parsedValue("""{"id": 10}""")
        val emptyObject = parsedValue("""{}""")

        assertTrue(newPattern.matches(objectWithId, Resolver()).isTrue())
        assertTrue(newPattern.matches(emptyObject, Resolver()).isTrue())
    }

    @Test
    fun `Given an optional key, the unsuffixed key should be looked up in state when generating a value`() {
        val facts = HashMap<String, Value>().apply {
            put("id", NumberValue(12345))
        }

        val resolver = Resolver(facts)

        when (val value = parsedPattern("""{"id?": "(number)"}""", null).generate(resolver)) {
            is JSONObjectValue -> {
                val id = value.jsonObject["id"] as NumberValue
                assertEquals(12345, id.number)
            }
            else -> Exception("Expected JSONObjectValue, got ${value.javaClass}")
        }
    }

    @Test
    fun `Given an optional key, the unsuffixed key should be looked up in the row when generating a pattern`() {
        val row = Row(listOf("id"), listOf("12345"))
        val patterns = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(row, Resolver())

        val value = patterns.map { it.generate(Resolver()) }.map {
            if (it !is JSONObjectValue)
                throw Exception("Expected JSONObjectValue2, got ${it.javaClass}")

            it.jsonObject.getOrDefault("id", NumberValue(0))
        }.find {
            it is NumberValue && it.number == 12345
        }

        assertEquals(12345, (value as NumberValue).number)
    }

    @Test
    fun `Given a column name in the examples, a json key must be replaced by the example`() {
        val row = Row(listOf("id"), listOf("10"))
        val pattern = parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver()).first()

        if (pattern !is JSONObjectPattern)
            throw Exception("Expected JSONObjectPattern, got ${pattern.javaClass}")

        val id = pattern.generate(Resolver()).jsonObject["id"] as NumberValue
        assertEquals(10, id.number)
    }

    @Test
    fun `Given a column name in the examples, a json key in a lazily looked up pattern must be replaced by the example`() {
        val row = Row(listOf("id"), listOf("10"))
        val actualPattern = parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver()).first()

        val resolver = Resolver(newPatterns = mapOf("(Id)" to actualPattern))

        val lazyPattern = parsedPattern("(Id)")
        val value = lazyPattern.generate(resolver)

        if (value !is JSONObjectValue)
            throw Exception("Expected JSONObjectValue, got ${value.javaClass}")

        val id = value.jsonObject["id"] as NumberValue
        assertEquals(10, id.number)
    }

    @Test
    fun `When generating a new pattern based on a row, a json value multiple 1+ lazy levels down must be replaced by the example value`() {
        val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"city": "(string)"}""")))

        val personPattern = parsedPattern("""{"name": "(string)", "address": "(Address)"}""")

        val row = Row(listOf("city"), listOf("Mumbai"))

        val newPattern = personPattern.newBasedOn(row, resolver).first()
        if (newPattern !is JSONObjectPattern)
            throw AssertionError("Expected JSONObjectPattern, got ${newPattern.javaClass.name}")

        assertTrue(newPattern.pattern["name"] is StringPattern)

        val addressPattern = newPattern.pattern["address"]
        if (addressPattern !is JSONObjectPattern)
            throw AssertionError("Expected JSONObjectPattern, got ${addressPattern?.javaClass?.name}")

        val cityPattern = addressPattern.pattern["city"]
        if (cityPattern !is ExactValuePattern)
            throw AssertionError("Expected ExactMatchPattern, got ${cityPattern?.javaClass?.name}")

        val cityValue = cityPattern.pattern
        if (cityValue !is StringValue)
            throw AssertionError("Expected StringValue, got ${cityValue.javaClass.name}")

        assertEquals("Mumbai", cityValue.string)
    }

    @Test
    fun `When generating a new pattern based on a row, a concrete pattern value in the object should not become a concrete value`() {
        val resolver = Resolver()

        val personPattern = parsedPattern("""{"name": "(string)"}""")

        val newPattern = personPattern.newBasedOn(Row(), resolver).first()

        if (newPattern !is JSONObjectPattern)
            throw AssertionError("Expected JSONObjectPattern, got ${newPattern.javaClass.name}")

        assertTrue(newPattern.pattern["name"] is StringPattern)
    }

    @Test
    fun `should return errors with id field`() {
        val patterns = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(Row(), Resolver())

        assertNotNull(patterns.find { pattern ->
            val result = pattern.matches(JSONObjectValue(mapOf("id" to StringValue("abc"))), Resolver())
            result is Result.Failure && result.report() == FailureReport(listOf("id"), listOf("""Expected number, actual was string: "abc""""))
        })
    }

    @Test
    fun `should ignore extra keys given the ellipsis key`() {
        val value = parsedValue("""{"expected": 10, "unexpected": 20}""")
        val pattern = parsedPattern("""{"expected": "(number)", "...": ""}""")

        value shouldMatch pattern
    }

    @Test
    fun `should not ignore extra keys by default`() {
        val value = parsedValue("""{"expected": 10, "unexpected": 20}""")
        val pattern = parsedPattern("""{"expected": "(number)"}""")

        value shouldNotMatch pattern
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch toJSONObjectPattern(mapOf("name" to StringPattern()))
    }

    @Test
    fun `it should encompass itself`() {
        val type = parsedPattern("""{"name": "(string)"}""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass itself with a nullable value`() {
        val type = parsedPattern("""{"number": "(number?)"}""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `having a nullable value it should encompass another with a non null value of the same type`() {
        val bigger = parsedPattern("""{"number": "(number?)"}""")
        val smallerWithNumber = parsedPattern("""{"number": "(number)"}""")
        val smallerWithNull = parsedPattern("""{"number": "(null)"}""")

        assertThat(bigger.encompasses(smallerWithNumber, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerWithNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass with an optional key`() {
        val type = parsedPattern("""{"number?": "(number)"}""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another with the optional key missing`() {
        val bigger = parsedPattern("""{"required": "(number)", "optional?": "(number)"}""")
        val smaller = parsedPattern("""{"required": "(number)"}""")
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another with an unheard of key`() {
        val bigger = parsedPattern("""{"required": "(number)"}""")
        val smaller = parsedPattern("""{"required": "(number)", "extra": "(number)"}""")
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }


    @Test
    fun `it should encompass itself when ellipsis is present`() {
        val bigger = toJSONObjectPattern(mapOf<String, Pattern>("data" to NumberPattern, "..." to StringPattern()))
        assertThat(bigger.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `type with ellipsis is equivalent to a type with the same keys except the ellipsis`() {
        val theOne = toJSONObjectPattern(mapOf<String, Pattern>("data" to NumberPattern))
        val theOther = toJSONObjectPattern(mapOf<String, Pattern>("data" to NumberPattern, "..." to StringPattern()))

        assertThat(theOne.encompasses(theOther, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(theOther.encompasses(theOne, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `creates three combinations per optional field with optional children`() {
        val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"number": "(string)", "street?": "(string)"}""")))

        val personPattern = parsedPattern("""{"name": "(string)", "address?": "(Address)"}""")

        val combinations = personPattern.newBasedOn(resolver)

        assertThat(combinations.size).isEqualTo(3)

        val addressPatternWithStreet = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to StringPattern()))
        val addressPatternWithoutStreet = toJSONObjectPattern(mapOf("number" to StringPattern()))
        val personWithAddressWithStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreet))
        val personWithAddressWithoutStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithoutStreet))
        val personWithoutAddress = toJSONObjectPattern(mapOf("name" to StringPattern()))

        assertThat(combinations).contains(personWithAddressWithStreet)
        assertThat(combinations).contains(personWithAddressWithoutStreet)
        assertThat(combinations).contains(personWithoutAddress)
    }

    @Test
    fun `creates four combinations per optional field with optional children with optional value`() {
        val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"number": "(string)", "street?": "(string?)"}""")))

        val personPattern = parsedPattern("""{"name": "(string)", "address?": "(Address)"}""")

        val combinations = personPattern.newBasedOn(resolver)

        assertThat(combinations.size).isEqualTo(4)

        val addressPatternWithStreet = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to StringPattern()))
        val addressPatternWithStreetSetToNull = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to NullPattern))
        val addressPatternWithoutStreet = toJSONObjectPattern(mapOf("number" to StringPattern()))
        val personWithAddressWithStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreet))
        val personWithAddressWithStreetSetToNull = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreetSetToNull))
        val personWithAddressWithoutStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithoutStreet))
        val personWithoutAddress = toJSONObjectPattern(mapOf("name" to StringPattern()))

        assertThat(combinations).contains(personWithAddressWithStreet)
        assertThat(combinations).contains(personWithAddressWithStreetSetToNull)
        assertThat(combinations).contains(personWithAddressWithoutStreet)
        assertThat(combinations).contains(personWithoutAddress)
    }

    @Test
    fun `creates five combinations per optional field with optional value with optional children`() {
        val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"number": "(string)", "street?": "(string?)"}""")))

        val personPattern = parsedPattern("""{"name": "(string)", "address?": "(Address?)"}""")

        val combinations = personPattern.newBasedOn(resolver)

        assertThat(combinations.size).isEqualTo(5)

        val addressPatternWithStreet = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to StringPattern()))
        val addressPatternWithStreetSetToNull = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to NullPattern))
        val addressPatternWithoutStreet = toJSONObjectPattern(mapOf("number" to StringPattern()))
        val personWithAddressWithStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreet))
        val personWithAddressWithStreetSetToNull = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreetSetToNull))
        val personWithAddressWithoutStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithoutStreet))
        val personWithAddressSetToNull = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to NullPattern))
        val personWithoutAddress = toJSONObjectPattern(mapOf("name" to StringPattern()))

        assertThat(combinations).contains(personWithAddressWithStreet)
        assertThat(combinations).contains(personWithAddressWithStreetSetToNull)
        assertThat(combinations).contains(personWithAddressWithoutStreet)
        assertThat(combinations).contains(personWithAddressSetToNull)
        assertThat(combinations).contains(personWithoutAddress)
    }
}
