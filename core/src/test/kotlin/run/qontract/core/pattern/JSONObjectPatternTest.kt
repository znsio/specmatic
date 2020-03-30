package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mustMatch
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import java.util.*
import kotlin.collections.HashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class JSONObjectPatternTest {
    @Test
    fun `Given an optional key, the generated object should contain the key without the ?`() {
        when (val result = parsedPattern("""{"id?": "(number)"}""", null).generate(Resolver())) {
            is JSONObjectValue -> assertTrue("id" in result.jsonObject)
            else -> throw Exception("Wrong type, got ${result.javaClass}, expected JSONObjectValue")
        }
    }

    @Test
    fun `Given an optional key, the suffix should remain in place in an object generated using newBasedOn`() {
        val newPattern = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(Row(), Resolver()).first()

        val objectWithId = parsedValue("""{"id": 10}""")
        val emptyObject = parsedValue("""{}""")

        assertTrue(newPattern.matches(objectWithId, Resolver()).toBoolean())
        assertTrue(newPattern.matches(emptyObject, Resolver()).toBoolean())
    }

    @Test
    fun `Given an optional key, the unsuffixed key should be looked up in state when generating a value`() {
        val data = HashMap<String, Any>().apply {
            put("id", 12345)
        }

        val resolver = Resolver(data)

        when (val value = parsedPattern("""{"id?": "(number)"}""", null).generate(resolver)) {
            is JSONObjectValue -> assertEquals(12345, value.jsonObject["id"]?.value)
            else -> Exception("Expected JSONObjectValue, got ${value.javaClass}")
        }
    }

    @Test
    fun `Given an optional key, the unsuffixed key should be looked up in the row when generating a pattern`() {
        val row = Row(listOf("id"), listOf("12345"))
        val patterns = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(row, Resolver())

        val value = patterns.map { it.generate(Resolver()) }.map {
            if(it !is JSONObjectValue)
                throw Exception("Expected JSONObjectValue2, got ${it.javaClass}")

            it.jsonObject.getOrDefault("id", NumberValue(0))
        }.find {
            it.value == 12345
        }

        assertEquals(12345, value?.value)
    }

    @Test
    fun `Given a column name in the examples, a json key must be replaced by the example`() {
        val row = Row(listOf("id"), listOf("10"))
        val pattern = parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver()).first()

        if (pattern !is JSONObjectPattern)
            throw Exception("Expected JSONObjectPattern, got ${pattern.javaClass}")

        assertEquals(10, pattern.generate(Resolver()).jsonObject["id"]?.value)
    }

    @Test
    fun `Given a column name in the examples, a json key in a lazily looked up pattern must be replaced by the example`() {
        val row = Row(listOf("id"), listOf("10"))
        val actualPattern = parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver()).first()

        val resolver = Resolver()
        resolver.customPatterns["(Id)"] = actualPattern

        val lazyPattern = parsedPattern("(Id)")
        val value = lazyPattern.generate(resolver)

        if (value !is JSONObjectValue)
            throw Exception("Expected JSONObjectValue, got ${value.javaClass}")

        assertEquals(10, value.jsonObject["id"]?.value)
    }

    @Test
    fun `When generating a new pattern based on a row, a json value multiple 1+ lazy levels down must be replaced by the example value`() {
        val resolver = Resolver()
        resolver.customPatterns["(Address)"] = parsedPattern("""{"city": "(string)"}""")

        val personPattern = parsedPattern("""{"name": "(string)", "address": "(Address)"}""")

        val row = Row(listOf("city"), listOf("Mumbai"))

        val newPattern = personPattern.newBasedOn(row, resolver).first()
        if(newPattern !is JSONObjectPattern)
            throw AssertionError("Expected JSONObjectPattern, got ${newPattern.javaClass.name}")

        assertTrue(newPattern.pattern["name"] is StringPattern)

        val addressPattern = newPattern.pattern["address"]
        if(addressPattern !is JSONObjectPattern)
            throw AssertionError("Expected JSONObjectPattern, got ${addressPattern?.javaClass?.name}")

        val cityPattern = addressPattern.pattern["city"]
        if(cityPattern !is ExactMatchPattern)
            throw AssertionError("Expected ExactMatchPattern, got ${cityPattern?.javaClass?.name}")

        val cityValue = cityPattern.pattern
        if(cityValue !is StringValue)
            throw AssertionError("Expected StringValue, got ${cityValue.javaClass.name}")

        assertEquals("Mumbai", cityValue.string)
    }

    @Test
    fun `When generating a new pattern based on a row, a concrete pattern value in the object should not become a concrete value`() {
        val resolver = Resolver()

        val personPattern = parsedPattern("""{"name": "(string)"}""")

        val newPattern = personPattern.newBasedOn(Row(), resolver).first()

        if(newPattern !is JSONObjectPattern)
            throw AssertionError("Expected JSONObjectPattern, got ${newPattern.javaClass.name}")

        assertTrue(newPattern.pattern["name"] is StringPattern)
    }

    @Test
    fun `should return errors with id field`() {
        val patterns = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(Row(), Resolver())

        assertNotNull(patterns.find { pattern ->
            val result = pattern.matches(JSONObjectValue(mapOf("id" to StringValue("abc"))), Resolver())
            result is Result.Failure && result.stackTrace() == Stack<String>().also { stack ->
                stack.push("abc is not a Number")
                stack.push("Expected value at id to match (number), actual value abc in JSONObject {id=abc}")
            }
        })
    }

    @Test
    fun `should ignore extra keys`() {
        val value = parsedValue("""{"expected": 10, "unexpected": 20}""")
        val pattern = parsedPattern("""{"expected": "(number)"}""")

        value mustMatch pattern
    }
}
