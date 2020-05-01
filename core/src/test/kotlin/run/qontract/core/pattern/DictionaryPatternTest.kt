package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch

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
}