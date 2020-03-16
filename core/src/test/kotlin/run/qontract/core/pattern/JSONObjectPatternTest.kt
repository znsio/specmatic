package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mustMatch
import run.qontract.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.collections.HashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val newPattern = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(Row(), Resolver())

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
            is JSONObjectValue -> assertEquals(12345, value.jsonObject["id"])
            else -> Exception("Expected JSONObjectValue, got ${value.javaClass}")
        }
    }

    @Test
    fun `Given an optional key, the unsuffixed key should be looked up in the row when generating a pattern`() {
        val row = Row(listOf("id"), listOf("12345"))
        val pattern = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(row, Resolver())

        if (pattern !is JSONObjectPattern)
            throw Exception("Expected JSONObjectPattern, got ${pattern.javaClass}")

        val value = pattern.generate(Resolver())

        assertEquals(12345, value.jsonObject["id"])
    }

    @Test
    fun `Given a column name in the examples, a json key must be replaced by the example`() {
        val row = Row(listOf("id"), listOf("10"))
        val pattern = parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver())

        if (pattern !is JSONObjectPattern)
            throw Exception("Expected JSONObjectPattern, got ${pattern.javaClass}")

        assertEquals(10, pattern.generate(Resolver()).jsonObject["id"])
    }

    @Test
    fun `Given a column name in the examples, a json key in a lazily looked up pattern must be replaced by the example`() {
        val row = Row(listOf("id"), listOf("10"))
        val actualPattern = parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver())

        val resolver = Resolver()
        resolver.customPatterns["(Id)"] = actualPattern

        val lazyPattern = parsedPattern("(Id)")
        val value = lazyPattern.generate(resolver)

        if (value !is JSONObjectValue)
            throw Exception("Expected JSONObjectValue, got ${value.javaClass}")

        assertEquals(10, value.jsonObject["id"])
    }

    @Test
    fun `When generating a new pattern based on a row, a json value multiple 1+ lazy levels down must be replaced by the example value`() {
        val resolver = Resolver()
        resolver.customPatterns["(Address)"] = parsedPattern("""{"city": "(string)"}""")

        val personPattern = parsedPattern("""{"name": "(string)", "address": "(Address)"}""")

        val row = Row(listOf("city"), listOf("Mumbai"))

        val newPattern = personPattern.newBasedOn(row, resolver)

        val patternValue = newPattern.pattern as Map<String, Any?>
        assertEquals("(string)", patternValue["name"])
        assertEquals("Mumbai", (patternValue["address"] as Map<String, Any?>)["city"])
    }

    @Test
    fun `When generating a new pattern based on a row, a concrete pattern value in the object should not become a concrete value`() {
        val resolver = Resolver()

        val personPattern = parsedPattern("""{"name": "(string)"}""")

        val newPattern = personPattern.newBasedOn(Row(), resolver)

        val patternValue = newPattern.pattern as Map<String, Any?>
        assertEquals("(string)", patternValue["name"])
    }

    @Test
    fun `should return errors with id field`() {
        val pattern = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(Row(), Resolver())

        if (pattern !is JSONObjectPattern)
            throw Exception("Expected JSONObjectValue, got ${pattern.javaClass}")

        pattern.matches(JSONObjectValue(hashMapOf("id" to "abc")), Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push("abc is not a Number")
                stack.push("""Expected: (number) Actual: abc""")
                stack.push("Expected: object[id] to match (number). Actual value: abc, in JSONObject {id=abc}")
            })
        }
    }

    @Test
    fun `should ignore extra keys`() {
        val value = parsedValue("""{"expected": 10, "unexpected": 20}""")
        val pattern = parsedPattern("""{"expected": "(number)"}""")

        value mustMatch pattern
    }
}
