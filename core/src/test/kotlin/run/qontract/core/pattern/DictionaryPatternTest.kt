package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import run.qontract.shouldMatch
import run.qontract.shouldNotMatch

internal class DictionaryPatternTest {
    @Test
    fun `should match a json object with specified pattern`() {
        val pattern = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(number)"))
        val value = parsedJSONStructure("""{"1": 1, "2": 2}""")

        value shouldMatch pattern
    }

    @Test
    fun `should not match a json object with nonmatching keys`() {
        val pattern = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(number)"))
        val value = parsedJSONStructure("""{"not a number": 1, "2": 2}""")

        value shouldNotMatch pattern
    }

    @Test
    fun `should not match a json object with nonmatching values`() {
        val pattern = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(number)"))
        val value = parsedJSONStructure("""{"1": 1, "2": "two"}""")

        value shouldNotMatch pattern
    }

    @Test
    fun `should match a json object with a key type of number`() {
        val pattern = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(string)"))
        parsedValue("""{"1": "1", "2": "two"}""") shouldMatch pattern
        parsedValue("""{"one": "1", "2": "two"}""") shouldNotMatch pattern
    }

    @Test
    fun `should load a matching json object from examples`() {
        val dictionaryType = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(string)"))
        val jsonType = toTabularPattern(mapOf("data" to dictionaryType))

        val example = Row(listOf("data"), listOf("""{"1": "one"}"""))
        val newJsonTypes = jsonType.newBasedOn(example, Resolver())

        assertThat(newJsonTypes).hasSize(1)

        val exactJson = newJsonTypes.single() as TabularPattern
        assertThat(exactJson).isEqualTo(toTabularPattern(mapOf("data" to ExactValuePattern(JSONObjectValue(mapOf("1" to StringValue("one")))))))
    }
}