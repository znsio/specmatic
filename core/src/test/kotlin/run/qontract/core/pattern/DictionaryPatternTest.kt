package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch

internal class DictionaryPatternTest {
    @Test
    fun `should match a json object with specified pattern`() {
        val pattern = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(number)"))
        val value = parsedJSON("""{"1": 1, "2": 2}""")

        value shouldMatch pattern
    }

    @Test
    fun `should not match a json object with nonmatching keys`() {
        val pattern = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(number)"))
        val value = parsedJSON("""{"not a number": 1, "2": 2}""")

        value shouldNotMatch pattern
    }

    @Test
    fun `should not match a json object with nonmatching values`() {
        val pattern = DictionaryPattern(DeferredPattern("(number)"), DeferredPattern("(number)"))
        val value = parsedJSON("""{"1": 1, "2": "two"}""")

        value shouldNotMatch pattern
    }
}